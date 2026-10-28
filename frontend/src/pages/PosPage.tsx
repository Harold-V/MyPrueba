import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
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

  // Payment in one control: the common methods, "Otro" for the rest, and fiado.
  type Choice = 'CASH' | 'NEQUI' | 'CARD' | 'MORE' | 'CREDIT';
  const main: Choice[] = ['CASH', 'NEQUI', 'CARD'];
  const choice: Choice =
    status === 'CREDIT'
      ? 'CREDIT'
      : (main as string[]).includes(method)
        ? (method as Choice)
        : 'MORE';
  function choose(next: Choice) {
    setAlert(null);
    if (next === 'CREDIT') return setStatus('CREDIT');
    setStatus('PAID');
    if (next === 'MORE') {
      if ((main as string[]).includes(method)) setMethod('TRANSFER');
    } else {
      setMethod(next);
    }
  }
  const choiceLabels: Record<Choice, string> = {
    CASH: 'Efectivo',
    NEQUI: 'Nequi',
    CARD: 'Tarjeta',
    MORE: 'Otro',
    CREDIT: 'Fiado',
  };
  const otherMethods = Object.fromEntries(
    Object.entries(cashPaymentMethods).filter(([key]) => !(main as string[]).includes(key)),
  );
  // Bills the customer is likely to hand over: the next two at or above the total.
  const bills = [200000n, 500000n, 1000000n, 2000000n, 5000000n, 10000000n]
    .filter((b) => b >= totals.total)
    .slice(0, 2);

  return (
    <div className="lg:grid lg:grid-cols-[minmax(0,1fr)_380px] lg:items-start lg:gap-5">
      <section aria-label="Catálogo" className="flex flex-col gap-3 pb-20 lg:pb-0">
        <div className="flex items-start justify-between gap-3">
          <div>
            <h1 className="text-[22px] leading-tight font-semibold">Vender</h1>
            <p className="mt-1 text-sm text-muted">
              {lines.length === 0
                ? 'Toca un producto o escanea su código.'
                : `${fromMilli(count)} ${count === 1000n ? 'producto' : 'productos'} en el carrito`}
            </p>
          </div>
          <Link to="/ventas/nueva" className="flex-none pt-1 text-sm font-medium text-accent">
            Venta libre
          </Link>
        </div>
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
          <div className="flex flex-wrap gap-2">
            {[{ id: '', name: 'Todas' }, ...categories].map((c) => (
              <button
                key={c.id || 'all'}
                type="button"
                aria-pressed={category === c.id}
                onClick={() => setCategory(c.id)}
                className={`h-9 rounded-full border px-3.5 text-sm ${
                  category === c.id
                    ? 'border-ink bg-ink font-medium text-white'
                    : 'border-control bg-surface text-ink hover:bg-raised'
                }`}
              >
                {c.name}
              </button>
            ))}
          </div>
        )}
        {products.length === 0 && (
          <p className="rounded-control border border-dashed border-control p-6 text-center text-muted">
            Aún no tienes productos. Créalos en Productos para vender desde aquí.
          </p>
        )}
        <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 xl:grid-cols-4">
          {shown.map((product) => {
            const item = stock[product.id];
            const inCart = lines.find((l) => l.productId === product.id);
            const tint = ['bg-tint-a', 'bg-tint-b', 'bg-tint-c'][
              Math.max(
                0,
                categories.findIndex((c) => c.id === product.categoryId),
              ) % 3
            ];
            return (
              <li key={product.id} className="flex">
                <button
                  type="button"
                  aria-label={`Agregar ${product.name}`}
                  onClick={() => add(product)}
                  className={`relative flex w-full flex-col items-start gap-1 rounded-card border p-3.5 text-left ${
                    inCart
                      ? 'border-control bg-raised'
                      : 'border-line bg-surface hover:border-control'
                  }`}
                >
                  <span
                    aria-hidden="true"
                    className={`mb-1.5 grid h-9 w-9 place-items-center rounded-card text-xs font-semibold text-tint-ink ${tint}`}
                  >
                    {initials(product.name)}
                  </span>
                  <span className="leading-snug font-medium">{product.name}</span>
                  <span className="text-[15px] font-semibold">{formatMoney(product.price)}</span>
                  {item && (
                    <span className={`text-xs ${item.low ? 'text-negative' : 'text-muted'}`}>
                      {item.low ? `Quedan ${item.stock}` : `${item.stock} disponibles`}
                    </span>
                  )}
                  {inCart && (
                    <span className="absolute top-2.5 right-2.5 grid h-6 min-w-6 place-items-center rounded-full bg-accent px-1.5 text-xs font-semibold text-white">
                      {inCart.quantity}
                      <span className="sr-only"> en el carrito</span>
                    </span>
                  )}
                </button>
              </li>
            );
          })}
        </ul>
      </section>

      {/* Phones: a bar with the total opens the cart; desktop: the cart sits beside the catalog. */}
      <button
        type="button"
        onClick={() => setCartOpen(true)}
        className="fixed inset-x-3 bottom-[70px] z-10 flex h-[52px] items-center justify-between rounded-xl bg-ink px-4 font-semibold text-white lg:hidden"
      >
        <span>Ver carrito ({fromMilli(count)})</span>
        <span>{formatMoney(fromCents(totals.total))}</span>
      </button>

      <section
        aria-label="Carrito"
        className={`${
          cartOpen ? 'fixed inset-0 z-30 overflow-y-auto bg-ground p-3' : 'hidden'
        } lg:sticky lg:top-4 lg:block lg:p-0`}
      >
        <form onSubmit={charge} className="card flex flex-col gap-3.5 p-5" noValidate>
          <div className="flex items-center justify-between">
            <h2 className="text-[15px] font-semibold">Carrito</h2>
            <button
              type="button"
              className="text-sm font-medium text-accent lg:hidden"
              onClick={() => setCartOpen(false)}
            >
              Seguir agregando
            </button>
          </div>
          {lines.length === 0 && (
            <p className="rounded-control border border-dashed border-control px-4 py-3 text-sm text-muted">
              Toca un producto para agregarlo.
            </p>
          )}
          <ul className="divide-y divide-rule">
            {lines.map((line) => {
              const short = shortage(line);
              return (
                <li key={line.productId} className="space-y-2 py-3 first:pt-0">
                  <div className="flex items-start justify-between gap-3">
                    <span className="min-w-0">
                      <span className="block font-medium">{line.name}</span>
                      <span className="block text-xs text-muted">
                        {formatMoney(line.price)} c/u
                      </span>
                    </span>
                    <span className="flex-none font-medium">
                      {formatMoney(lineTotal(normalizeQuantity(line.quantity), line.price) ?? '0')}
                    </span>
                  </div>
                  <div className="flex items-end gap-2">
                    <div className="flex h-11 items-center rounded-control border border-control sm:h-10">
                      <button
                        type="button"
                        aria-label={`Uno menos de ${line.name}`}
                        onClick={() => less(line)}
                        className="h-full w-9 text-lg"
                      >
                        −
                      </button>
                      <label className="sr-only" htmlFor={`qty-${line.productId}`}>
                        Cantidad de {line.name}
                      </label>
                      <input
                        id={`qty-${line.productId}`}
                        inputMode="decimal"
                        value={line.quantity}
                        onChange={(e) => change(line.productId, { quantity: e.target.value })}
                        className="h-full w-12 border-x border-control text-center"
                      />
                      <button
                        type="button"
                        aria-label={`Uno más de ${line.name}`}
                        onClick={() =>
                          change(line.productId, {
                            quantity: fromMilli((toMilli(line.quantity) ?? 0n) + 1000n),
                          })
                        }
                        className="h-full w-9 text-lg"
                      >
                        +
                      </button>
                    </div>
                    <div className="min-w-0 flex-1">
                      <label className="sr-only" htmlFor={`disc-${line.productId}`}>
                        Descuento de {line.name}
                      </label>
                      <input
                        id={`disc-${line.productId}`}
                        placeholder="Descuento ($ o %)"
                        value={line.discount}
                        onChange={(e) => change(line.productId, { discount: e.target.value })}
                        className="h-11 w-full rounded-control border border-control px-3 sm:h-10"
                      />
                    </div>
                  </div>
                  {totals.lineErrors[line.productId] && line.discount.trim() && (
                    <p className="text-sm text-negative">
                      {parseDiscount(line.discount) === null
                        ? 'Escribe un valor (1.500) o un porcentaje (10%).'
                        : 'El descuento no puede superar el valor del producto.'}
                    </p>
                  )}
                  {short !== null && (
                    <p className="text-sm text-warning">
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
              <ul aria-label="Totales" className="space-y-1 border-t border-line pt-3">
                <li className="flex justify-between text-sm text-muted">
                  <span>Subtotal</span>
                  <span>{formatMoney(fromCents(totals.subtotal))}</span>
                </li>
                {totals.discounts > 0n && (
                  <li className="flex justify-between text-sm text-muted">
                    <span>Descuentos</span>
                    <span>−{formatMoney(fromCents(totals.discounts))}</span>
                  </li>
                )}
                <li className="flex justify-between pt-1 text-[22px] leading-8 font-semibold">
                  <span>Total</span>
                  <span>{formatMoney(fromCents(totals.total))}</span>
                </li>
              </ul>
              <div
                role="group"
                aria-label="Forma de pago"
                className="grid grid-cols-5 gap-[3px] rounded-control bg-raised p-[3px]"
              >
                {(Object.keys(choiceLabels) as Choice[]).map((key) => (
                  <button
                    key={key}
                    type="button"
                    aria-pressed={choice === key}
                    onClick={() => choose(key)}
                    className={`h-9 rounded-[4px] text-[13px] ${
                      choice === key
                        ? 'bg-surface font-semibold text-ink outline outline-line'
                        : 'text-muted'
                    }`}
                  >
                    {choiceLabels[key]}
                  </button>
                ))}
              </div>
              {choice === 'MORE' && (
                <SelectField
                  label="Método de pago"
                  options={otherMethods}
                  value={method}
                  onChange={(e) => setMethod(e.target.value as Exclude<PaymentMethod, 'CREDIT'>)}
                />
              )}
              {cashPaid && (
                <>
                  <div className="flex items-end gap-2">
                    <div className="min-w-0 flex-1">
                      <TextField
                        label="¿Con cuánto paga?"
                        inputMode="decimal"
                        value={received}
                        onChange={(e) => setReceived(e.target.value)}
                      />
                    </div>
                    {bills.map((b) => (
                      <button
                        key={String(b)}
                        type="button"
                        onClick={() => setReceived(String(b / 100n))}
                        className="h-11 flex-none rounded-control border border-control px-2.5 text-[13px] sm:h-10"
                      >
                        {formatMoney(fromCents(b))}
                      </button>
                    ))}
                  </div>
                  {receivedCents !== null && (
                    <p
                      className="flex items-baseline justify-between rounded-control bg-raised px-3 py-2.5"
                      aria-live="polite"
                    >
                      {receivedCents >= totals.total
                        ? `Cambio: ${formatMoney(fromCents(receivedCents - totals.total))}`
                        : `Faltan ${formatMoney(fromCents(totals.total - receivedCents))}`}
                    </p>
                  )}
                </>
              )}
              {status === 'CREDIT' && (
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
            className="h-12 w-full text-[17px] sm:h-12"
            disabled={busy || lines.length === 0}
          >
            {`Cobrar ${formatMoney(fromCents(totals.total))}`}
          </Button>
        </form>
      </section>
    </div>
  );
}

/** Two letters for a product's tile: "Gaseosa 400 ml" → GA, "Leche Alpina" → LA. */
function initials(name: string): string {
  const words = name
    .replace(/[0-9×].*$/, '')
    .split(/\s+/)
    .filter(Boolean);
  if (words.length === 0) return name.slice(0, 2).toUpperCase();
  if (words.length === 1) return words[0]!.slice(0, 2).toUpperCase();
  return (words[0]![0]! + words[1]![0]!).toUpperCase();
}
