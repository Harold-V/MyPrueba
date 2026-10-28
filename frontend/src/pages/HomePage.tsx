import { Receipt, ShoppingCart } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { api } from '../api/client';
import type {
  Balance,
  CashSession,
  Inventory,
  Movement,
  PeriodRange,
  Receivables,
} from '../api/types';
import { useMe } from '../auth/session-context';
import { BarChart } from '../components/BarChart';
import { TextField } from '../components/ui';
import { formatDate, formatDateTime, localDateOf, todayIn } from '../lib/dates';
import { formatMoney } from '../lib/money';
import { expenseCategories, formMessage, lowStockMessage, paymentMethods } from '../i18n/es';

type PeriodName = 'today' | 'week' | 'month' | 'range';
type Filter = 'all' | 'sales' | 'expenses';

const periods: Record<PeriodName, string> = {
  today: 'Hoy',
  week: 'Semana',
  month: 'Mes',
  range: 'Rango',
};
/** The screen's title names the period it shows. */
const titles: Record<PeriodName, string> = {
  today: 'Hoy',
  week: 'Esta semana',
  month: 'Este mes',
  range: 'Rango de fechas',
};
const filters: Record<Filter, string> = { all: 'Todos', sales: 'Ventas', expenses: 'Gastos' };

function Toggle<T extends string>({
  label,
  options,
  value,
  onChange,
}: {
  label: string;
  options: Record<T, string>;
  value: T;
  onChange: (value: T) => void;
}) {
  return (
    <div role="group" aria-label={label} className="inline-flex rounded-control bg-raised p-[3px]">
      {(Object.keys(options) as T[]).map((key) => (
        <button
          key={key}
          type="button"
          aria-pressed={value === key}
          onClick={() => onChange(key)}
          className={`h-8 rounded-[4px] px-3 text-sm ${
            value === key ? 'bg-surface font-medium text-ink outline outline-line' : 'text-muted'
          }`}
        >
          {options[key]}
        </button>
      ))}
    </div>
  );
}

/** Payments received or money owed either way: only then is the credit line worth showing. */
function hasCredit(balance: Balance | null): balance is Balance {
  return [balance?.payments, balance?.receivable, balance?.payables].some(
    (v) => v !== undefined && v !== '0.00',
  );
}

/** "Abonos: $5.000 · Por cobrar: $15.000 · Por pagar: $60.000", skipping what is zero. */
function creditLine(balance: Balance): string {
  return (
    [
      ['Abonos', balance.payments],
      ['Por cobrar', balance.receivable],
      ['Por pagar', balance.payables],
    ] as const
  )
    .filter(([, value]) => value !== undefined && value !== '0.00')
    .map(([label, value]) => `${label}: ${formatMoney(value!)}`)
    .join(' · ');
}

interface MovementView {
  title: string;
  subtitle: string;
  to: string;
  sign: string;
  tone: string;
}

/** How a movement reads in the list: income in green with "+", expenses in ink, credit neutral. */
function describeMovement(m: Movement): MovementView {
  const join = (parts: (string | null | undefined)[]) => parts.filter(Boolean).join(' · ');
  if (m.type === 'PAYMENT') {
    return {
      title: `Abono de ${m.customerName}`,
      subtitle: join([m.description, paymentMethods[m.paymentMethod]]),
      to: `/abonos/${m.id}`,
      sign: '+',
      tone: 'text-positive',
    };
  }
  if (m.type === 'SUPPLIER_PAYMENT') {
    return {
      title: `Pago a ${m.supplierName}`,
      subtitle: join([m.description, paymentMethods[m.paymentMethod]]),
      to: `/pagos-proveedor/${m.id}`,
      sign: '−',
      tone: 'text-ink',
    };
  }
  if (m.type === 'EXPENSE') {
    // A purchase on credit is not money out yet: it is paid later to the supplier.
    const credit = m.paymentMethod === 'CREDIT';
    return {
      title: m.description ?? expenseCategories[m.category ?? 'OTHER'],
      subtitle: join([
        m.description && m.category ? expenseCategories[m.category] : null,
        credit ? `A crédito con ${m.supplierName}` : paymentMethods[m.paymentMethod],
        credit ? null : m.supplierName,
      ]),
      to: `/gastos/${m.id}`,
      sign: credit ? '' : '−',
      tone: credit ? 'text-muted' : 'text-ink',
    };
  }
  const credit = m.paymentMethod === 'CREDIT';
  return {
    title: m.description ?? (credit ? `Venta fiada a ${m.customerName}` : 'Venta'),
    subtitle: join([
      credit && m.description ? `Fiado a ${m.customerName}` : paymentMethods[m.paymentMethod],
      !credit ? m.customerName : null,
    ]),
    to: `/ventas/${m.id}`,
    sign: credit ? '' : '+',
    tone: credit ? 'text-muted' : 'text-positive',
  };
}

const hourOf = (instant: string, timeZone: string) =>
  Number(
    new Intl.DateTimeFormat('en-GB', { timeZone, hour: '2-digit', hourCycle: 'h23' }).format(
      new Date(instant),
    ),
  );

/** Paid sales by hour (today) or by day (longer periods), from the movements on screen. */
function salesSeries(
  movements: Movement[],
  period: PeriodName,
  range: PeriodRange | undefined,
  timeZone: string,
) {
  const paid = movements.filter((m) => m.type === 'SALE' && m.paymentMethod !== 'CREDIT');
  const amount = (m: Movement) => Number(m.amount);
  if (period === 'today' || !range) {
    const seen = paid.map((m) => hourOf(m.occurredAt, timeZone));
    const first = Math.min(6, ...seen);
    const last = Math.max(20, ...seen);
    const hours = Array.from({ length: last - first + 1 }, (_, i) => first + i);
    const values = hours.map((h) =>
      paid.filter((m) => hourOf(m.occurredAt, timeZone) === h).reduce((s, m) => s + amount(m), 0),
    );
    return {
      title: 'Ventas por hora',
      labels: hours.map((h) => `${h}:00`),
      values,
      highlight: hours.map((_, i) => i),
    };
  }
  const days: string[] = [];
  for (
    let d = new Date(`${range.from}T12:00:00Z`);
    d <= new Date(`${range.to}T12:00:00Z`);
    d = new Date(d.getTime() + 86_400_000)
  ) {
    days.push(d.toISOString().slice(0, 10));
  }
  const today = todayIn(timeZone);
  return {
    title: 'Ventas por día',
    labels: days.map((day) => String(Number(day.slice(8)))),
    values: days.map((day) =>
      paid
        .filter((m) => localDateOf(m.occurredAt, timeZone) === day)
        .reduce((s, m) => s + amount(m), 0),
    ),
    // The current day carries the accent; the past days stay muted.
    highlight: days.includes(today) ? [days.indexOf(today)] : days.map((_, i) => i),
  };
}

const axisMoney = (v: number) =>
  v >= 1_000_000
    ? `$${(v / 1_000_000).toLocaleString('es-CO', { maximumFractionDigits: 1 })} M`
    : `$${Math.round(v).toLocaleString('es-CO')}`;

/** The cash drawer as a ledger: what should be in it right now. */
function RegisterCard({
  register,
}: {
  register: { open: CashSession | null; last: CashSession | null };
}) {
  const { business } = useMe();
  const open = register.open;
  if (!open) {
    return (
      <section aria-label="Caja" className="card flex flex-col gap-3 p-5">
        <header className="flex items-center justify-between gap-3">
          <h2 className="text-[15px] font-semibold">Caja</h2>
          <span className="pill pill-neutral">Cerrada</span>
        </header>
        <p className="text-sm text-muted">
          {register.last?.closedAt
            ? `Último cierre: ${formatDateTime(register.last.closedAt, business.timeZone)}.`
            : 'Abre la caja con la base para saber cuánto debe haber en el cajón.'}
        </p>
        <Link
          to="/caja"
          className="mt-auto inline-flex h-10 items-center justify-center rounded-control border border-control font-medium"
        >
          Abrir caja
        </Link>
      </section>
    );
  }
  const rows: [string, string][] = [
    ['Base', open.opening],
    ['+ Ventas en efectivo', open.cashSales],
    ['+ Abonos en efectivo', open.cashPayments],
    ['− Gastos en efectivo', open.cashExpenses],
    ['− Pagos a proveedores', open.cashSupplierPayments],
  ];
  return (
    <section aria-label="Caja" className="card flex flex-col p-5">
      <header className="mb-2 flex items-center justify-between gap-3">
        <h2 className="text-[15px] font-semibold">Caja</h2>
        <span className="pill pill-positive">
          Abierta desde las{' '}
          {new Intl.DateTimeFormat('es-CO', {
            timeZone: business.timeZone,
            hour: 'numeric',
            minute: '2-digit',
          }).format(new Date(open.openedAt))}
        </span>
      </header>
      <dl className="mb-4 text-sm">
        {rows
          .filter(([label, value]) => label === 'Base' || value !== '0.00')
          .map(([label, value]) => (
            <div key={label} className="flex justify-between border-b border-rule py-2">
              <dt className="text-muted">{label}</dt>
              <dd>{formatMoney(value)}</dd>
            </div>
          ))}
        <div className="flex items-baseline justify-between pt-3">
          <dt className="font-medium">Debe haber en el cajón</dt>
          <dd className="text-xl font-semibold">{formatMoney(open.expected)}</dd>
        </div>
      </dl>
      <Link
        to="/caja"
        className="mt-auto inline-flex h-10 items-center justify-center rounded-control border border-control font-medium"
      >
        Cerrar caja
      </Link>
    </section>
  );
}

const daysSince = (instant: string, timeZone: string) =>
  Math.round(
    (new Date(`${todayIn(timeZone)}T12:00:00Z`).getTime() -
      new Date(`${localDateOf(instant, timeZone)}T12:00:00Z`).getTime()) /
      86_400_000,
  );

export function HomePage() {
  const { business } = useMe();
  const [period, setPeriod] = useState<PeriodName>('today');
  const [range, setRange] = useState<PeriodRange>({ from: '', to: '' });
  const [filter, setFilter] = useState<Filter>('all');
  const [balance, setBalance] = useState<Balance | null>(null);
  const [movements, setMovements] = useState<Movement[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [register, setRegister] = useState<{
    open: CashSession | null;
    last: CashSession | null;
  }>();
  const [receivables, setReceivables] = useState<Receivables>();
  const [lowStock, setLowStock] = useState<Inventory>();

  const periodQuery =
    period === 'range'
      ? range.from && range.to
        ? new URLSearchParams({ period, from: range.from, to: range.to })
        : null
      : new URLSearchParams(period === 'today' ? {} : { period });
  const periodKey = periodQuery?.toString() ?? null;

  useEffect(() => {
    if (periodKey === null) return;
    let active = true;
    const movementQuery = new URLSearchParams(periodKey);
    if (filter !== 'all') movementQuery.set('type', filter);
    const suffix = (query: URLSearchParams) => (query.size > 0 ? `?${query}` : '');
    Promise.all([
      api.get<Balance>(`/api/balance${suffix(new URLSearchParams(periodKey))}`),
      api.get<{ items: Movement[] }>(`/api/movements${suffix(movementQuery)}`),
    ])
      .then(([b, m]) => {
        if (!active) return;
        setBalance(b);
        setMovements(m.items);
        setError(null);
      })
      .catch((e: { code?: string }) => active && setError(formMessage(e.code ?? '')));
    return () => {
      active = false;
    };
  }, [periodKey, filter]);

  // The side blocks are optional: a failure leaves them out instead of breaking the screen.
  useEffect(() => {
    let active = true;
    api.get<{ open: CashSession | null; last: CashSession | null }>('/api/cash-register').then(
      (data) => active && setRegister(data),
      () => undefined,
    );
    api.get<Receivables>('/api/receivables').then(
      (data) => active && setReceivables(data),
      () => undefined,
    );
    api.get<Inventory>('/api/inventory?lowOnly=true').then(
      (data) => active && setLowStock(data),
      () => undefined,
    );
    return () => {
      active = false;
    };
  }, []);

  // The finding: the oldest overdue debt, else what is running out.
  const overdue = (receivables?.customers ?? [])
    .filter((c) => c.overdue && c.oldestDebtAt)
    .sort((a, b) => (a.oldestDebtAt! < b.oldestDebtAt! ? -1 : 1))[0];
  const finding = overdue
    ? `${overdue.name} lleva ${daysSince(overdue.oldestDebtAt!, business.timeZone)} días con un fiado vencido de ${formatMoney(overdue.debt)}.`
    : lowStock && lowStock.summary.lowStock > 0
      ? `${lowStockMessage(lowStock.summary.lowStock)}.`
      : null;
  const today = new Intl.DateTimeFormat('es-CO', {
    timeZone: business.timeZone,
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  }).format(new Date());
  const periodLabel =
    period === 'today'
      ? today.charAt(0).toUpperCase() + today.slice(1)
      : balance
        ? `${formatDate(`${balance.period.from}T12:00:00Z`, business.timeZone)} – ${formatDate(`${balance.period.to}T12:00:00Z`, business.timeZone)}`
        : '';

  const series =
    movements && filter !== 'expenses'
      ? salesSeries(movements, period, balance?.period, business.timeZone)
      : null;
  const debtors = (receivables?.customers ?? []).slice(0, 3);
  const running = (lowStock?.items ?? []).slice(0, 3);
  const hasSide = debtors.length > 0 || running.length > 0;

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-[22px] leading-tight font-semibold">{titles[period]}</h1>
          <p className="mt-1 text-sm text-muted">
            {[periodLabel, finding].filter(Boolean).join(' · ')}
          </p>
        </div>
        <div className="grid w-full grid-cols-2 gap-2 sm:flex sm:w-auto sm:items-center">
          <Link
            to="/ventas/nueva"
            className="hidden px-2 text-sm font-medium text-accent sm:inline"
          >
            Venta libre
          </Link>
          <Link
            to="/gastos/nuevo"
            className="inline-flex h-11 items-center justify-center gap-2 rounded-control border border-control bg-surface px-3.5 font-medium sm:h-10"
          >
            <Receipt size={16} strokeWidth={1.6} aria-hidden="true" />
            Nuevo gasto
          </Link>
          <Link
            to="/vender"
            className="order-first inline-flex h-11 items-center justify-center gap-2 rounded-control bg-ink px-3.5 font-medium text-white sm:order-none sm:h-10"
          >
            <ShoppingCart size={16} strokeWidth={1.6} aria-hidden="true" />
            Nueva venta
          </Link>
        </div>
      </div>

      <div className="flex flex-wrap items-end gap-3">
        <Toggle label="Periodo" options={periods} value={period} onChange={setPeriod} />
        {period === 'range' && (
          <div className="grid grid-cols-2 gap-3">
            <TextField
              label="Desde"
              type="date"
              value={range.from}
              onChange={(e) => setRange({ ...range, from: e.target.value })}
            />
            <TextField
              label="Hasta"
              type="date"
              value={range.to}
              onChange={(e) => setRange({ ...range, to: e.target.value })}
            />
          </div>
        )}
      </div>
      {error && (
        <p role="alert" className="text-negative">
          {error}
        </p>
      )}

      <section aria-label="Resumen" className="card">
        <dl className="grid grid-cols-3">
          {(
            [
              ['Ventas', balance?.sales, 'pagadas, sin fiados', true],
              ['Gastos', balance?.expenses, 'pagados, con pagos a proveedores', false],
              ['Balance', balance?.balance, 'ventas + abonos − gastos', false],
            ] as const
          ).map(([label, value, sub, lead]) => (
            <div
              key={label}
              className="flex flex-col gap-0.5 border-l border-line px-4 py-4 first:border-l-0 sm:px-5"
            >
              <dt className="text-[13px] text-muted">{label}</dt>
              <dd
                className={
                  lead
                    ? 'text-2xl leading-9 font-semibold sm:text-[28px]'
                    : 'text-lg leading-8 font-medium sm:text-[22px]'
                }
              >
                {formatMoney(value ?? '0')}
              </dd>
              <span className="hidden text-xs text-muted sm:block">{sub}</span>
            </div>
          ))}
        </dl>
        {hasCredit(balance) && (
          <p className="border-t border-line px-5 py-2.5 text-sm text-muted">
            {creditLine(balance)}
          </p>
        )}
      </section>

      <div className="grid gap-5 lg:grid-cols-12 [&>*]:min-w-0">
        {series && (
          <section
            aria-label={series.title}
            className={`card p-5 ${register ? 'lg:col-span-8' : 'lg:col-span-12'}`}
          >
            <header className="mb-3 flex items-center justify-between gap-3">
              <h2 className="text-[15px] font-semibold">{series.title}</h2>
              <span className="text-xs text-muted">Ventas pagadas, sin fiados</span>
            </header>
            {series.values.some((v) => v > 0) ? (
              <BarChart
                labels={series.labels}
                values={series.values}
                highlight={series.highlight}
                format={axisMoney}
                label={`${series.title}: ${series.labels
                  .map((l, i) => `${l} ${formatMoney(String(series.values[i]))}`)
                  .filter((_, i) => series.values[i]! > 0)
                  .join(', ')}`}
              />
            ) : (
              <p className="rounded-control border border-dashed border-control px-4 py-3 text-sm text-muted">
                Aún no hay ventas pagadas en este periodo.
              </p>
            )}
          </section>
        )}
        {register && (
          <div className={`lg:col-span-4 ${series ? '' : 'lg:col-start-9'}`}>
            <RegisterCard register={register} />
          </div>
        )}

        <section
          aria-label="Movimientos"
          className={`card p-5 ${hasSide ? 'lg:col-span-8' : 'lg:col-span-12'}`}
        >
          <header className="mb-2 flex flex-wrap items-center justify-between gap-3">
            <h2 className="text-[15px] font-semibold">Movimientos</h2>
            <Toggle
              label="Tipo de movimiento"
              options={filters}
              value={filter}
              onChange={setFilter}
            />
          </header>
          {movements !== null && movements.length === 0 && (
            <p className="rounded-control border border-dashed border-control px-4 py-3 text-sm text-muted">
              Aún no hay movimientos en este periodo.
            </p>
          )}
          <ul className="divide-y divide-rule">
            {movements?.map((m) => {
              const view = describeMovement(m);
              return (
                <li key={m.id}>
                  <Link
                    to={view.to}
                    className="flex min-h-[52px] items-center gap-3 py-2 hover:bg-raised"
                  >
                    <span className="hidden w-[72px] flex-none text-[13px] whitespace-nowrap text-muted sm:block">
                      {new Intl.DateTimeFormat('es-CO', {
                        timeZone: business.timeZone,
                        ...(period === 'today'
                          ? { hour: 'numeric', minute: '2-digit' }
                          : { day: 'numeric', month: 'short' }),
                      }).format(new Date(m.occurredAt))}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate font-medium">{view.title}</span>
                      <span className="block truncate text-xs text-muted">
                        <span className="sm:hidden">
                          {new Intl.DateTimeFormat('es-CO', {
                            timeZone: business.timeZone,
                            ...(period === 'today'
                              ? { hour: 'numeric', minute: '2-digit' }
                              : { day: 'numeric', month: 'short' }),
                          }).format(new Date(m.occurredAt))}
                          {' · '}
                        </span>
                        {view.subtitle}
                      </span>
                    </span>
                    <span className={`flex-none font-medium ${view.tone}`}>
                      {view.sign}
                      {formatMoney(m.amount)}
                    </span>
                  </Link>
                </li>
              );
            })}
          </ul>
        </section>

        {hasSide && (
          <div className="flex flex-col gap-5 lg:col-span-4">
            {debtors.length > 0 && (
              <section aria-label="Fiados por cobrar" className="card p-5">
                <header className="mb-1 flex items-center justify-between gap-3">
                  <h2 className="text-[15px] font-semibold">Fiados por cobrar</h2>
                  <Link to="/clientes" className="text-[13px] font-medium text-accent">
                    {formatMoney(receivables!.total)}
                  </Link>
                </header>
                <ul className="divide-y divide-rule">
                  {debtors.map((c, i) => (
                    <li key={c.customerId}>
                      <Link
                        to={`/clientes/${c.customerId}`}
                        className="flex min-h-[52px] items-center gap-3 py-2"
                      >
                        <span
                          aria-hidden="true"
                          className={`grid h-9 w-9 flex-none place-items-center rounded-full text-xs font-semibold text-tint-ink ${['bg-tint-a', 'bg-tint-b', 'bg-tint-c'][i]}`}
                        >
                          {c.name
                            .split(/\s+/)
                            .slice(0, 2)
                            .map((w) => w[0]?.toUpperCase())
                            .join('')}
                        </span>
                        <span className="min-w-0 flex-1">
                          <span className="block truncate font-medium">{c.name}</span>
                          {c.oldestDebtAt && (
                            <span className="block text-xs text-muted">
                              Desde el {formatDate(c.oldestDebtAt, business.timeZone)} ·{' '}
                              {daysSince(c.oldestDebtAt, business.timeZone)} días
                            </span>
                          )}
                        </span>
                        <span className="flex flex-none flex-col items-end gap-0.5">
                          <span className="font-medium">{formatMoney(c.debt)}</span>
                          {c.overdue && <span className="pill pill-negative">Vencido</span>}
                        </span>
                      </Link>
                    </li>
                  ))}
                </ul>
              </section>
            )}
            {running.length > 0 && (
              <section aria-label="Se está acabando" className="card p-5">
                <header className="mb-1 flex items-center justify-between gap-3">
                  <h2 className="text-[15px] font-semibold">Se está acabando</h2>
                  <Link to="/inventario" className="text-[13px] font-medium text-accent">
                    {lowStockMessage(lowStock!.summary.lowStock)}
                  </Link>
                </header>
                <ul className="divide-y divide-rule">
                  {running.map((item) => (
                    <li key={item.productId} className="flex min-h-[52px] items-center gap-3 py-2">
                      <span className="min-w-0 flex-1">
                        <span className="block truncate font-medium">{item.name}</span>
                        {item.minStock && Number(item.minStock) > 0 && (
                          <span className="mt-1.5 block h-1 max-w-40 rounded-full bg-rule">
                            <span
                              className="block h-1 rounded-full bg-negative"
                              style={{
                                width: `${Math.max(0, Math.min(100, (Number(item.stock) / Number(item.minStock)) * 100))}%`,
                              }}
                            />
                          </span>
                        )}
                      </span>
                      <span className="flex-none font-medium">
                        {item.stock}
                        {item.minStock && (
                          <small className="font-normal text-muted"> de {item.minStock}</small>
                        )}
                      </span>
                    </li>
                  ))}
                </ul>
              </section>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
