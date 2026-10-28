import { useEffect, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router';
import { api, compact } from '../api/client';
import type { PaymentMethod, Product, Sale } from '../api/types';
import { useMe } from '../auth/session-context';
import { ConfirmDelete } from '../components/ConfirmDelete';
import { Button, FormAlert, Loading, SelectField, TextField } from '../components/ui';
import { paymentMethods } from '../i18n/es';
import { localDateOf, todayIn } from '../lib/dates';
import { formatMoney, lineTotal, normalizeAmount, sumAmounts } from '../lib/money';
import { occurredAtFor, useMovementForm } from './useMovementForm';

interface Line {
  key: string;
  /** null when the product was deleted after the sale; the line keeps its own name and price. */
  productId: string | null;
  name: string;
  quantity: string;
  unitPrice: string;
}

const normalizeQuantity = (value: string) => value.trim().replace(',', '.');

export function SaleFormPage() {
  const { id } = useParams();
  const { business } = useMe();
  const { errors, alert, busy, save, remove, fail } = useMovementForm('/api/sales', id);
  const [loaded, setLoaded] = useState(!id);
  const [products, setProducts] = useState<Product[]>([]);
  const [kind, setKind] = useState<'FREE' | 'ITEMS'>('FREE');
  const [amount, setAmount] = useState('');
  const [lines, setLines] = useState<Line[]>([]);
  const [concept, setConcept] = useState('');
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('CASH');
  const [date, setDate] = useState('');
  const [original, setOriginal] = useState<string | null>(null);
  const [note, setNote] = useState('');

  useEffect(() => {
    api.get<Product[]>('/api/products').then(setProducts, fail);
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
      setConcept(sale.concept ?? '');
      setPaymentMethod(sale.paymentMethod);
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
    const common = compact({
      concept: concept.trim(),
      paymentMethod,
      occurredAt: occurredAtFor(date, original, business.timeZone),
      note: note.trim(),
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
    <form onSubmit={onSubmit} className="space-y-4" noValidate>
      <h1 className="text-xl font-bold">{id ? 'Editar venta' : 'Nueva venta'}</h1>
      <FormAlert message={alert} />

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
          <SelectField
            label="Agregar producto"
            placeholder={products.length ? 'Elige un producto' : 'Aún no tienes productos'}
            options={Object.fromEntries(
              products
                .filter((p) => !lines.some((l) => l.productId === p.id))
                .map((p) => [p.id, `${p.name} — ${formatMoney(p.price)}`]),
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
      <SelectField
        label="Método de pago"
        options={paymentMethods}
        value={paymentMethod}
        onChange={(e) => setPaymentMethod(e.target.value as PaymentMethod)}
        error={errors.paymentMethod}
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
      <Button type="submit" className="w-full" disabled={busy}>
        Guardar venta
      </Button>
      {id && <ConfirmDelete label="Eliminar" question="¿Eliminar esta venta?" onConfirm={remove} />}
    </form>
  );
}
