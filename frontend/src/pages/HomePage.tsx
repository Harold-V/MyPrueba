import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { api } from '../api/client';
import type { Balance, Movement, PeriodRange } from '../api/types';
import { useMe } from '../auth/session-context';
import { TextField } from '../components/ui';
import { formatDateTime } from '../lib/dates';
import { formatMoney } from '../lib/money';
import { expenseCategories, formMessage, paymentMethods } from '../i18n/es';

type PeriodName = 'today' | 'week' | 'month' | 'range';
type Filter = 'all' | 'sales' | 'expenses';

const periods: Record<PeriodName, string> = {
  today: 'Hoy',
  week: 'Semana',
  month: 'Mes',
  range: 'Rango',
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
    <div role="group" aria-label={label} className="flex rounded-lg bg-slate-200 p-1">
      {(Object.keys(options) as T[]).map((key) => (
        <button
          key={key}
          type="button"
          aria-pressed={value === key}
          onClick={() => onChange(key)}
          className={`flex-1 rounded-md px-2 py-2 text-sm font-medium ${
            value === key ? 'bg-white text-slate-900 shadow' : 'text-slate-700'
          }`}
        >
          {options[key]}
        </button>
      ))}
    </div>
  );
}

export function HomePage() {
  const { business } = useMe();
  const [period, setPeriod] = useState<PeriodName>('today');
  const [range, setRange] = useState<PeriodRange>({ from: '', to: '' });
  const [filter, setFilter] = useState<Filter>('all');
  const [balance, setBalance] = useState<Balance | null>(null);
  const [movements, setMovements] = useState<Movement[] | null>(null);
  const [error, setError] = useState<string | null>(null);

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

  return (
    <div className="space-y-5">
      <div className="lg:max-w-md">
        <Toggle label="Periodo" options={periods} value={period} onChange={setPeriod} />
      </div>
      {period === 'range' && (
        <div className="grid grid-cols-2 gap-3 lg:grid-cols-1">
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
      {error && (
        <p role="alert" className="text-red-700">
          {error}
        </p>
      )}

      <div className="space-y-5 lg:grid lg:grid-cols-[1fr_16rem] lg:gap-5 lg:space-y-0">
        <section aria-label="Resumen" className="rounded-xl bg-white p-4 shadow-sm lg:p-6">
          <dl className="grid grid-cols-3 gap-2 text-center">
            <div>
              <dt className="text-sm text-slate-600">Ventas</dt>
              <dd className="text-lg font-semibold text-emerald-800 lg:text-3xl">
                {formatMoney(balance?.sales ?? '0')}
              </dd>
            </div>
            <div>
              <dt className="text-sm text-slate-600">Gastos</dt>
              <dd className="text-lg font-semibold text-red-700 lg:text-3xl">
                {formatMoney(balance?.expenses ?? '0')}
              </dd>
            </div>
            <div>
              <dt className="text-sm text-slate-600">Balance</dt>
              <dd className="text-lg font-bold lg:text-3xl">
                {formatMoney(balance?.balance ?? '0')}
              </dd>
            </div>
          </dl>
        </section>

        <div className="grid grid-cols-2 gap-3">
          <Link
            to="/ventas/nueva"
            className="flex items-center justify-center rounded-lg bg-emerald-700 py-3 font-semibold text-white"
          >
            Nueva venta
          </Link>
          <Link
            to="/gastos/nuevo"
            className="flex items-center justify-center rounded-lg bg-red-700 py-3 font-semibold text-white"
          >
            Nuevo gasto
          </Link>
        </div>
      </div>

      <section aria-label="Movimientos" className="space-y-3">
        <div className="lg:max-w-sm">
          <Toggle
            label="Tipo de movimiento"
            options={filters}
            value={filter}
            onChange={setFilter}
          />
        </div>
        {movements !== null && movements.length === 0 && (
          <p className="rounded-xl bg-white p-6 text-center text-slate-600">
            Aún no hay movimientos en este periodo.
          </p>
        )}
        <ul className="divide-y divide-slate-200 rounded-xl bg-white shadow-sm">
          {movements?.map((m) => {
            const isSale = m.type === 'SALE';
            const title =
              m.description ?? (isSale ? 'Venta' : expenseCategories[m.category ?? 'OTHER']);
            const subtitle = [
              !isSale && m.description && m.category ? expenseCategories[m.category] : null,
              paymentMethods[m.paymentMethod],
              formatDateTime(m.occurredAt, business.timeZone),
            ]
              .filter(Boolean)
              .join(' · ');
            return (
              <li key={m.id}>
                <Link
                  to={isSale ? `/ventas/${m.id}` : `/gastos/${m.id}`}
                  className="flex items-center justify-between gap-3 px-4 py-3"
                >
                  <span className="min-w-0">
                    <span className="block truncate font-medium">{title}</span>
                    <span className="block text-sm text-slate-600">{subtitle}</span>
                  </span>
                  <span
                    className={`shrink-0 font-semibold ${isSale ? 'text-emerald-800' : 'text-red-700'}`}
                  >
                    {isSale ? '+' : '−'}
                    {formatMoney(m.amount)}
                  </span>
                </Link>
              </li>
            );
          })}
        </ul>
      </section>
    </div>
  );
}
