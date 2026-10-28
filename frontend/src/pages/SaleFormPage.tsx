import { useEffect, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router';
import { api, ApiError, compact } from '../api/client';
import type {
  Customer,
  Inventory,
  InventoryItem,
  PaymentMethod,
  Product,
  Sale,
} from '../api/types';
import { useMe } from '../auth/session-context';
import { ConfirmDelete } from '../components/ConfirmDelete';
import { Button, FormAlert, Loading, SelectField, TextField } from '../components/ui';
import { cashPaymentMethods } from '../i18n/es';
import { localDateOf, todayIn } from '../lib/dates';
import { formatMoney, lineTotal, normalizeAmount, sumAmounts } from '../lib/money';
import { normalizeQuantity, toMilli } from '../lib/quantity';
import { matchesProduct } from '../lib/search';
import { occurredAtFor, useMovementForm } from './useMovementForm';

interface Line {
  key: string;
  /** null when the product was deleted after the sale; the line keeps its own name and price. */
  productId: string | null;
  name: string;
  quantity: string;
  unitPrice: string;
}

/** Registers a customer at the till, without leaving the sale. */
function NewCustomer({ onCreated }: { onCreated: (customer: Customer) => void }) {
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});

  async function create() {
    try {
      const customer = await api.post<Customer>('/api/customers', compact({ name, phone }));
      onCreated(customer);
      setOpen(false);
      setName('');
      setPhone('');
      setErrors({});
    } catch (error) {
      setErrors(error instanceof ApiError ? error.fieldErrors() : {});
    }
  }

  if (!open) {
    return (
      <button
        type="button"
        className="text-sm font-semibold text-emerald-800 underline"
        onClick={() => setOpen(true)}
      >
        Nuevo cliente
      </button>
    );
  }
  return (
    <div className="space-y-3 rounded-lg border border-slate-200 bg-white p-3">
      <TextField
        label="Nombre del cliente"
        value={name}
        onChange={(e) => setName(e.target.value)}
        error={errors.name}
      />
      <TextField
        label="Teléfono del cliente (opcional)"
        type="tel"
        value={phone}
        onChange={(e) => setPhone(e.target.value)}
        error={errors.phone}
      />
      <div className="flex gap-2">
        <Button onClick={() => void create()}>Crear cliente</Button>
        <Button variant="secondary" onClick={() => setOpen(false)}>
          Cancelar
        </Button>
      </div>
    </div>
  );
}

function productLabel(product: Product, item: InventoryItem | undefined): string {
  const label = `${product.name} — ${formatMoney(product.price)}`;
  return item ? `${label} · ${item.stock} disponibles` : label;
}

export function SaleFormPage() {
  const { id } = useParams();
  const { business } = useMe();
  const { errors, alert, busy, save, remove, fail } = useMovementForm('/api/sales', id);
  const [loaded, setLoaded] = useState(!id);
  const [products, setProducts] = useState<Product[]>([]);
  const [stock, setStock] = useState<Record<string, InventoryItem>>({});
  const [allowNegativeStock, setAllowNegativeStock] = useState(true);
  // Quantities this sale already took from stock, so editing it does not warn about its own units.
  const [originalQuantities, setOriginalQuantities] = useState<Record<string, string>>({});
  const [kind, setKind] = useState<'FREE' | 'ITEMS'>('FREE');
  const [amount, setAmount] = useState('');
  const [lines, setLines] = useState<Line[]>([]);
  const [search, setSearch] = useState('');
  const [concept, setConcept] = useState('');
  const [status, setStatus] = useState<'PAID' | 'CREDIT'>('PAID');
  const [dueDate, setDueDate] = useState('');
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('CASH');
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [customerId, setCustomerId] = useState('');
  const [date, setDate] = useState('');
  const [original, setOriginal] = useState<string | null>(null);
  const [note, setNote] = useState('');

  useEffect(() => {
    api.get<Product[]>('/api/products').then(setProducts, fail);
    api.get<Customer[]>('/api/customers').then(setCustomers, () => undefined);
    api.get<{ allowNegativeStock: boolean }>('/api/business/settings').then(
      (s) => setAllowNegativeStock(s.allowNegativeStock),
      () => undefined,
    );
    api.get<Inventory>('/api/inventory').then(
      (data) => setStock(Object.fromEntries(data.items.map((item) => [item.productId, item]))),
      () => undefined,
    );
    if (!id) return;
    api.get<Sale>(`/api/sales/${id}`).then((sale) => {
      setKind(sale.kind);
      setAmount(sale.kind === 'FREE' ? sale.total.replace(/\.00$/, '') : '');
      setLines(
        sale.items.map((item, index) => ({
          key: item.productId ?? `line-${index}`,
          productId: item.productId,
          name: item.name,
          quantity: item.quantity,
          unitPrice: item.unitPrice,
        })),
      );
      setOriginalQuantities(
        Object.fromEntries(
          sale.items.filter((i) => i.productId).map((i) => [i.productId!, i.quantity]),
        ),
      );
      setConcept(sale.concept ?? '');
      const credit = sale.status === 'CREDIT' || sale.paymentMethod === 'CREDIT';
      setStatus(credit ? 'CREDIT' : 'PAID');
      setPaymentMethod(credit ? 'CASH' : sale.paymentMethod);
      setDueDate(sale.dueDate ?? '');
      setCustomerId(sale.customerId ?? '');
      setOriginal(sale.occurredAt);
      setDate(localDateOf(sale.occurredAt, business.timeZone));
      setNote(sale.note ?? '');
      setLoaded(true);
    }, fail);
    // fail is stable enough for a one-time load
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, business.timeZone]);

  const lineTotals = lines.map((line) =>
    lineTotal(normalizeQuantity(line.quantity), normalizeAmount(line.unitPrice)),
  );
  const total = sumAmounts(lineTotals.map((t) => t ?? '0'));

  /** Available units when the line asks for more than the stock (plus what this sale already took). */
  function shortage(line: Line): string | null {
    const item = line.productId ? stock[line.productId] : undefined;
    if (!item) return null;
    const available =
      (toMilli(item.stock) ?? 0n) + (toMilli(originalQuantities[line.productId!] ?? '0') ?? 0n);
    const wanted = toMilli(line.quantity);
    if (wanted === null || wanted <= available) return null;
    return item.stock;
  }

  function addProduct(productId: string) {
    const product = products.find((p) => p.id === productId);
    if (!product || lines.some((l) => l.productId === productId)) return;
    setLines([
      ...lines,
      { key: productId, productId, name: product.name, quantity: '1', unitPrice: product.price },
    ]);
  }

  function updateLine(index: number, change: Partial<Line>) {
    setLines(lines.map((line, i) => (i === index ? { ...line, ...change } : line)));
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    const clientErrors: Record<string, string> = {};
    if (kind === 'FREE' && !amount.trim()) clientErrors.amount = 'required';
    if (kind === 'ITEMS' && lines.length === 0) clientErrors.items = 'sale_kind';
    if (status === 'CREDIT' && !customerId) clientErrors.customerId = 'required';
    const common = compact({
      concept: concept.trim(),
      // A credit sale (fiado) has no payment method yet: it is paid later with abonos.
      ...(status === 'CREDIT' ? { status, dueDate } : { paymentMethod }),
      occurredAt: occurredAtFor(date, original, business.timeZone),
      note: note.trim(),
      customerId,
    });
    const body =
      kind === 'FREE'
        ? { amount: normalizeAmount(amount), ...common }
        : {
            items: lines.map((line) => ({
              ...(line.productId ? { productId: line.productId } : { name: line.name }),
              quantity: normalizeQuantity(line.quantity),
              unitPrice: normalizeAmount(line.unitPrice),
            })),
            ...common,
          };
    void save(body, clientErrors);
  }

  if (!loaded) return <Loading />;

  return (
    <form onSubmit={onSubmit} className="max-w-xl space-y-4" noValidate>
      <h1 className="text-xl font-bold">{id ? 'Editar venta' : 'Nueva venta'}</h1>

      <fieldset className="flex gap-4">
        <legend className="sr-only">Tipo de venta</legend>
        {(['FREE', 'ITEMS'] as const).map((value) => (
          <label key={value} className="flex items-center gap-2 text-base">
            <input
              type="radio"
              name="kind"
              value={value}
              checked={kind === value}
              onChange={() => setKind(value)}
              className="h-5 w-5 accent-emerald-700"
            />
            {value === 'FREE' ? 'Venta libre' : 'Con productos'}
          </label>
        ))}
      </fieldset>

      {kind === 'FREE' ? (
        <TextField
          label="Valor"
          inputMode="decimal"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          error={errors.amount}
        />
      ) : (
        <div className="space-y-3">
          <TextField
            label="Buscar producto (nombre o código)"
            type="search"
            hint="Con un lector de códigos: escanea y el producto se agrega."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            onKeyDown={(e) => {
              if (e.key !== 'Enter') return;
              // Enter submits the form otherwise; an exact barcode adds its product.
              e.preventDefault();
              const code = search.trim();
              const product = products.find((p) => p.barcode && p.barcode === code);
              if (product) {
                addProduct(product.id);
                setSearch('');
              }
            }}
          />
          <SelectField
            label="Agregar producto"
            placeholder={products.length ? 'Elige un producto' : 'Aún no tienes productos'}
            options={Object.fromEntries(
              products
                .filter((p) => !lines.some((l) => l.productId === p.id))
                .filter((p) => matchesProduct(p, search))
                .map((p) => [p.id, productLabel(p, stock[p.id])]),
            )}
            value=""
            onChange={(e) => addProduct(e.target.value)}
            error={errors.items ?? errors.amount}
          />
          {products.length === 0 && (
            <Link to="/productos" className="text-sm font-semibold text-emerald-800 underline">
              Crear productos
            </Link>
          )}
          <ul className="space-y-3">
            {lines.map((line, index) => (
              <li key={line.key} className="rounded-lg bg-white p-3 shadow-sm">
                <div className="flex items-center justify-between">
                  <span className="font-medium">{line.name}</span>
                  <button
                    type="button"
                    className="text-sm text-red-700 underline"
                    onClick={() => setLines(lines.filter((_, i) => i !== index))}
                  >
                    Quitar {line.name}
                  </button>
                </div>
                <div className="mt-2 grid grid-cols-2 gap-3">
                  <TextField
                    label="Cantidad"
                    inputMode="decimal"
                    value={line.quantity}
                    onChange={(e) => updateLine(index, { quantity: e.target.value })}
                    error={errors[`items[${index}].quantity`]}
                  />
                  <TextField
                    label="Precio unitario"
                    inputMode="decimal"
                    value={line.unitPrice}
                    onChange={(e) => updateLine(index, { unitPrice: e.target.value })}
                    error={errors[`items[${index}].unitPrice`]}
                  />
                </div>
                {shortage(line) && (
                  <p className="mt-2 text-sm text-amber-800">
                    Solo hay {shortage(line)} en inventario.{' '}
                    {allowNegativeStock
                      ? 'La venta se registrará igual.'
                      : 'No podrás registrar esta venta: tu negocio no permite vender sin stock.'}
                  </p>
                )}
              </li>
            ))}
          </ul>
          <p className="text-right text-lg font-semibold" aria-live="polite">
            Total: {formatMoney(total)}
          </p>
        </div>
      )}

      <TextField
        label="Concepto (opcional)"
        value={concept}
        onChange={(e) => setConcept(e.target.value)}
        error={errors.concept}
      />
      <fieldset className="flex gap-4">
        <legend className="mb-1 text-sm font-medium text-slate-800">Estado del pago</legend>
        {(
          [
            ['PAID', 'Pagada'],
            ['CREDIT', 'Fiada (a crédito)'],
          ] as const
        ).map(([value, text]) => (
          <label key={value} className="flex items-center gap-2 text-base">
            <input
              type="radio"
              name="status"
              value={value}
              checked={status === value}
              onChange={() => setStatus(value)}
              className="h-5 w-5 accent-emerald-700"
            />
            {text}
          </label>
        ))}
      </fieldset>
      {status === 'PAID' ? (
        <SelectField
          label="Método de pago"
          options={cashPaymentMethods}
          value={paymentMethod}
          onChange={(e) => setPaymentMethod(e.target.value as PaymentMethod)}
          error={errors.paymentMethod}
        />
      ) : (
        <TextField
          label="Fecha de vencimiento (opcional)"
          type="date"
          hint="Hasta cuándo tiene el cliente para pagar."
          value={dueDate}
          onChange={(e) => setDueDate(e.target.value)}
          error={errors.dueDate}
        />
      )}
      <SelectField
        label="Cliente"
        placeholder={status === 'CREDIT' ? 'Elige a quién le fías' : 'Sin cliente'}
        hint={
          status === 'CREDIT'
            ? 'Obligatorio en ventas fiadas: la deuda queda a su nombre.'
            : 'Opcional.'
        }
        options={Object.fromEntries(customers.map((c) => [c.id, c.name]))}
        value={customerId}
        onChange={(e) => setCustomerId(e.target.value)}
        error={errors.customerId}
      />
      <NewCustomer
        onCreated={(customer) => {
          setCustomers([...customers, customer]);
          setCustomerId(customer.id);
        }}
      />
      <TextField
        label="Fecha"
        type="date"
        max={todayIn(business.timeZone)}
        hint="Si la dejas vacía, se usa la fecha y hora actual."
        value={date}
        onChange={(e) => setDate(e.target.value)}
        error={errors.occurredAt}
      />
      <TextField
        label="Nota (opcional)"
        value={note}
        onChange={(e) => setNote(e.target.value)}
        error={errors.note}
      />
      {/* Next to the button: after submitting, the top of a long form is out of view. */}
      <FormAlert message={alert} />
      <Button type="submit" className="w-full" disabled={busy}>
        Guardar venta
      </Button>
      {id && <ConfirmDelete label="Eliminar" question="¿Eliminar esta venta?" onConfirm={remove} />}
    </form>
  );
}
