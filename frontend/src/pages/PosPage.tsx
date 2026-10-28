import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router';
import { api, ApiError, compact } from '../api/client';
import type {
  Category,
  Customer,
  Inventory,
  InventoryItem,
  PaymentMethod,
  Product,
} from '../api/types';
import { Button, FormAlert, SelectField, TextField } from '../components/ui';
import { cashPaymentMethods, formMessage } from '../i18n/es';
import { discountCents, parseDiscount } from '../lib/discount';
import { formatMoney, fromCents, lineTotal, normalizeAmount, toCents } from '../lib/money';
import { normalizeQuantity, toMilli } from '../lib/quantity';
import { matchesProduct } from '../lib/search';
import { NewCustomer } from './SaleFormPage';

interface CartLine {
  productId: string;
  name: string;
  price: string;
  quantity: string;
  /** As typed: "10%" or "1.500". */
  discount: string;
}

/** Thousandths back to the shortest quantity: 4000 → "4", 1500 → "1.5". */
function fromMilli(milli: bigint): string {
  const whole = milli / 1000n;
  const fraction = (milli % 1000n).toString().padStart(3, '0').replace(/0+$/, '');
  return fraction ? `${whole}.${fraction}` : `${whole}`;
}

const cents = (amount: string | null) => (amount === null ? 0n : (toCents(amount) ?? 0n));

/** Prices the cart exactly like the API does, so the total shown is the total charged. */
function price(lines: CartLine[], saleDiscount: string) {
  let subtotal = 0n;
  let afterLines = 0n;
  const lineErrors: Record<string, boolean> = {};
  for (const line of lines) {
    const gross = cents(lineTotal(normalizeQuantity(line.quantity), line.price));
    const discount = parseDiscount(line.discount);
    const off = discount ? discountCents(gross, discount) : 0n;
    lineErrors[line.productId] = discount === null || off > gross;
    subtotal += gross;
    afterLines += off > gross ? gross : gross - off;
  }
  const parsed = parseDiscount(saleDiscount);
  const saleOff = parsed ? discountCents(afterLines, parsed) : 0n;
  const saleError = parsed === null || saleOff > afterLines;
  const total = saleError ? afterLines : afterLines - saleOff;
  return { subtotal, discounts: subtotal - total, total, lineErrors, saleError };
}

export function PosPage() {
  const navigate = useNavigate();
  const [products, setProducts] = useState<Product[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [stock, setStock] = useState<Record<string, InventoryItem>>({});
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [allowNegativeStock, setAllowNegativeStock] = useState(true);
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('');
  const [lines, setLines] = useState<CartLine[]>([]);
  const [saleDiscount, setSaleDiscount] = useState('');
  const [status, setStatus] = useState<'PAID' | 'CREDIT'>('PAID');
  const [method, setMethod] = useState<Exclude<PaymentMethod, 'CREDIT'>>('CASH');
  const [received, setReceived] = useState('');
  const [customerId, setCustomerId] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [alert, setAlert] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [cartOpen, setCartOpen] = useState(false);

  useEffect(() => {
    api
      .get<Product[]>('/api/products')
      .then(setProducts, (e: unknown) =>
        setAlert(formMessage(e instanceof ApiError ? e.code : '')),
      );
    api.get<Category[]>('/api/product-categories').then(setCategories, () => undefined);
    api.get<Customer[]>('/api/customers').then(setCustomers, () => undefined);
    api.get<Inventory>('/api/inventory').then(
      (data) => setStock(Object.fromEntries(data.items.map((item) => [item.productId, item]))),
      () => undefined,
    );
    api.get<{ allowNegativeStock: boolean }>('/api/business/settings').then(
      (s) => setAllowNegativeStock(s.allowNegativeStock),
      () => undefined,
    );
  }, []);

  const shown = products.filter(
    (p) => (!category || p.categoryId === category) && matchesProduct(p, search),
  );
  const totals = price(lines, saleDiscount);
  const count = lines.reduce((sum, l) => sum + (toMilli(l.quantity) ?? 0n), 0n);
  const receivedCents = received.trim() ? toCents(normalizeAmount(received)) : null;
  const cashPaid = status === 'PAID' && method === 'CASH';

  function add(product: Product) {
    setAlert(null);
    setLines((current) =>
      current.some((l) => l.productId === product.id)
        ? current.map((l) =>
            l.productId === product.id
              ? { ...l, quantity: fromMilli((toMilli(l.quantity) ?? 0n) + 1000n) }
              : l,
          )
        : [
            ...current,
            {
              productId: product.id,
              name: product.name,
              price: product.price,
              quantity: '1',
              discount: '',
            },
          ],
    );
  }

  function change(productId: string, update: Partial<CartLine>) {
    setLines((current) =>
      current.map((l) => (l.productId === productId ? { ...l, ...update } : l)),
    );
  }

  function less(line: CartLine) {
    const next = (toMilli(line.quantity) ?? 0n) - 1000n;
    if (next <= 0n) setLines((current) => current.filter((l) => l.productId !== line.productId));
    else change(line.productId, { quantity: fromMilli(next) });
  }

  /** A shortage only blocks the sale when the business does not sell without stock. */
  function shortage(line: CartLine): string | null {
    const item = stock[line.productId];
    const wanted = toMilli(line.quantity);
    if (!item || wanted === null || wanted <= (toMilli(item.stock) ?? 0n)) return null;
    return item.stock;
  }

  async function charge(event: FormEvent) {
    event.preventDefault();
    if (lines.length === 0) {
      setAlert('Agrega al menos un producto.');
      return;
    }
    if (Object.values(totals.lineErrors).some(Boolean) || totals.saleError) {
      setAlert(formMessage('validation_failed'));
      return;
    }
    if (status === 'CREDIT' && !customerId) {
      setAlert('Elige a quién le fías.');
      return;
    }
    if (cashPaid && receivedCents !== null && receivedCents < totals.total) {
      setAlert('Lo recibido no alcanza para el total.');
      return;
    }
    setBusy(true);
    try {
      const sale = await api.post<{ id: string }>(
        '/api/sales',
        compact({
          items: lines.map((line) =>
            compact({
              productId: line.productId,
              quantity: normalizeQuantity(line.quantity),
              ...parseDiscount(line.discount),
            }),
          ),
          ...parseDiscount(saleDiscount),
          ...(status === 'CREDIT'
            ? { status, dueDate }
            : { paymentMethod: method, received: cashPaid ? normalizeAmount(received) : '' }),
          customerId,
        }),
      );
      navigate(`/ventas/${sale.id}/comprobante`);
    } catch (error) {
      const apiError = error instanceof ApiError ? error : new ApiError(0, 'unknown_error');
      setAlert(formMessage(apiError.code, apiError.details));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="space-y-4 lg:grid lg:grid-cols-[1fr_24rem] lg:items-start lg:gap-6 lg:space-y-0">
      <section aria-label="Catálogo" className="space-y-3 pb-16 lg:pb-0">
        <h1 className="text-xl font-bold">Vender</h1>
        <TextField
          label="Buscar producto (nombre o código)"
          type="search"
          hint="Con un lector de códigos: escanea y el producto se agrega."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          onKeyDown={(e) => {
            if (e.key !== 'Enter') return;
            e.preventDefault();
            const product = products.find((p) => p.barcode && p.barcode === search.trim());
            if (product) {
              add(product);
              setSearch('');
            }
          }}
        />
        {categories.length > 0 && (
          <div className="flex gap-2 overflow-x-auto pb-1">
            {[{ id: '', name: 'Todas' }, ...categories].map((c) => (
              <button
                key={c.id || 'all'}
                type="button"
                aria-pressed={category === c.id}
                onClick={() => setCategory(c.id)}
                className={`shrink-0 rounded-full px-3 py-1.5 text-sm font-medium ${
                  category === c.id
                    ? 'bg-emerald-700 text-white'
                    : 'bg-white text-slate-700 shadow-sm'
                }`}
              >
                {c.name}
              </button>
            ))}
          </div>
        )}
        {products.length === 0 && (
          <p className="rounded-xl bg-white p-6 text-center text-slate-600">
            Aún no tienes productos. Créalos en Productos para vender desde aquí.
          </p>
        )}
        <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 xl:grid-cols-4">
          {shown.map((product) => {
            const item = stock[product.id];
            const inCart = lines.find((l) => l.productId === product.id);
            return (
              <li key={product.id}>
                <button
                  type="button"
                  aria-label={`Agregar ${product.name}`}
                  onClick={() => add(product)}
                  className={`flex h-full w-full flex-col items-start gap-1 rounded-xl bg-white p-3 text-left shadow-sm ${
                    inCart ? 'ring-2 ring-emerald-600' : ''
                  }`}
                >
                  <span className="font-medium leading-snug">{product.name}</span>
                  <span className="font-semibold text-emerald-800">
                    {formatMoney(product.price)}
                  </span>
                  {item && <span className="text-xs text-slate-600">{item.stock} disponibles</span>}
                  {inCart && (
                    <span className="text-xs font-semibold text-emerald-800">
                      {inCart.quantity} en el carrito
                    </span>
                  )}
                </button>
              </li>
            );
          })}
        </ul>
      </section>

      {/* Phones: a bar with the total opens the cart; desktop: the cart is always beside the catalog. */}
      <button
        type="button"
        onClick={() => setCartOpen(true)}
        className="fixed inset-x-3 bottom-16 z-10 flex items-center justify-between rounded-xl bg-emerald-700 px-4 py-3 font-semibold text-white shadow-lg lg:hidden"
      >
        <span>Ver carrito ({fromMilli(count)})</span>
        <span>{formatMoney(fromCents(totals.total))}</span>
      </button>

      <section
        aria-label="Carrito"
        className={`${
          cartOpen ? 'fixed inset-0 z-30 overflow-y-auto bg-slate-50 p-4' : 'hidden'
        } lg:sticky lg:top-8 lg:block lg:p-0`}
      >
        <form onSubmit={charge} className="space-y-4 rounded-xl bg-white p-4 shadow-sm" noValidate>
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold">Carrito</h2>
            <button
              type="button"
              className="text-sm font-semibold text-emerald-800 underline lg:hidden"
              onClick={() => setCartOpen(false)}
            >
              Seguir agregando
            </button>
          </div>
          {lines.length === 0 && (
            <p className="text-sm text-slate-600">Toca un producto para agregarlo.</p>
          )}
          <ul className="divide-y divide-slate-100">
            {lines.map((line) => {
              const short = shortage(line);
              return (
                <li key={line.productId} className="space-y-2 py-3">
                  <div className="flex items-start justify-between gap-2">
                    <span className="font-medium">{line.name}</span>
                    <span className="shrink-0 font-semibold">
                      {formatMoney(lineTotal(normalizeQuantity(line.quantity), line.price) ?? '0')}
                    </span>
                  </div>
                  <div className="flex items-end gap-2">
                    <Button
                      variant="secondary"
                      aria-label={`Uno menos de ${line.name}`}
                      onClick={() => less(line)}
                    >
                      −
                    </Button>
                    <div className="w-20">
                      <TextField
                        label={`Cantidad de ${line.name}`}
                        inputMode="decimal"
                        value={line.quantity}
                        onChange={(e) => change(line.productId, { quantity: e.target.value })}
                      />
                    </div>
                    <Button
                      variant="secondary"
                      aria-label={`Uno más de ${line.name}`}
                      onClick={() =>
                        change(line.productId, {
                          quantity: fromMilli((toMilli(line.quantity) ?? 0n) + 1000n),
                        })
                      }
                    >
                      +
                    </Button>
                    <div className="flex-1">
                      <TextField
                        label={`Descuento de ${line.name}`}
                        placeholder="$ o %"
                        value={line.discount}
                        onChange={(e) => change(line.productId, { discount: e.target.value })}
                      />
                    </div>
                  </div>
                  {totals.lineErrors[line.productId] && line.discount.trim() && (
                    <p className="text-sm text-red-700">
                      {parseDiscount(line.discount) === null
                        ? 'Escribe un valor (1.500) o un porcentaje (10%).'
                        : 'El descuento no puede superar el valor del producto.'}
                    </p>
                  )}
                  {short !== null && (
                    <p className="text-sm text-amber-800">
                      Solo hay {short} en inventario.
                      {allowNegativeStock
                        ? ' La venta se registrará igual.'
                        : ' No podrás registrar esta venta.'}
                    </p>
                  )}
                </li>
              );
            })}
          </ul>
          {lines.length > 0 && (
            <>
              <TextField
                label="Descuento a toda la venta"
                placeholder="$ o %"
                value={saleDiscount}
                onChange={(e) => setSaleDiscount(e.target.value)}
                error={totals.saleError && saleDiscount.trim() ? 'exceeds_subtotal' : undefined}
              />
              <ul aria-label="Totales" className="space-y-1 border-t border-slate-100 pt-3">
                <li className="flex justify-between text-slate-700">
                  <span>Subtotal</span>
                  <span>{formatMoney(fromCents(totals.subtotal))}</span>
                </li>
                {totals.discounts > 0n && (
                  <li className="flex justify-between text-slate-700">
                    <span>Descuentos</span>
                    <span>−{formatMoney(fromCents(totals.discounts))}</span>
                  </li>
                )}
                <li className="flex justify-between text-xl font-bold">
                  <span>Total</span>
                  <span>{formatMoney(fromCents(totals.total))}</span>
                </li>
              </ul>
              <fieldset className="flex gap-4">
                <legend className="mb-1 text-sm font-medium text-slate-800">Pago</legend>
                {(
                  [
                    ['PAID', 'Pagada'],
                    ['CREDIT', 'Fiada'],
                  ] as const
                ).map(([value, text]) => (
                  <label key={value} className="flex items-center gap-2 text-base">
                    <input
                      type="radio"
                      name="pos-status"
                      checked={status === value}
                      onChange={() => setStatus(value)}
                      className="h-5 w-5 accent-emerald-700"
                    />
                    {text}
                  </label>
                ))}
              </fieldset>
              {status === 'PAID' ? (
                <>
                  <SelectField
                    label="Método de pago"
                    options={cashPaymentMethods}
                    value={method}
                    onChange={(e) => setMethod(e.target.value as Exclude<PaymentMethod, 'CREDIT'>)}
                  />
                  {method === 'CASH' && (
                    <>
                      <TextField
                        label="¿Con cuánto paga?"
                        inputMode="decimal"
                        hint="Opcional: calcula el cambio."
                        value={received}
                        onChange={(e) => setReceived(e.target.value)}
                      />
                      {receivedCents !== null && (
                        <p className="text-lg font-semibold" aria-live="polite">
                          {receivedCents >= totals.total
                            ? `Cambio: ${formatMoney(fromCents(receivedCents - totals.total))}`
                            : `Faltan ${formatMoney(fromCents(totals.total - receivedCents))}`}
                        </p>
                      )}
                    </>
                  )}
                </>
              ) : (
                <TextField
                  label="Fecha de vencimiento (opcional)"
                  type="date"
                  value={dueDate}
                  onChange={(e) => setDueDate(e.target.value)}
                />
              )}
              <SelectField
                label="Cliente"
                placeholder={status === 'CREDIT' ? 'Elige a quién le fías' : 'Sin cliente'}
                options={Object.fromEntries(customers.map((c) => [c.id, c.name]))}
                value={customerId}
                onChange={(e) => setCustomerId(e.target.value)}
              />
              <NewCustomer
                onCreated={(customer) => {
                  setCustomers([...customers, customer]);
                  setCustomerId(customer.id);
                }}
              />
            </>
          )}
          <FormAlert message={alert} />
          <Button
            type="submit"
            className="w-full py-3 text-lg"
            disabled={busy || lines.length === 0}
          >
            {`Cobrar ${formatMoney(fromCents(totals.total))}`}
          </Button>
        </form>
      </section>
    </div>
  );
}
