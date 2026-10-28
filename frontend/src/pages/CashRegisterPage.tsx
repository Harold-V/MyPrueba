import { useEffect, useState, type FormEvent } from 'react';
import { api, ApiError, compact } from '../api/client';
import type { CashSession } from '../api/types';
import { useMe } from '../auth/session-context';
import { Button, FormAlert, Loading, TextField } from '../components/ui';
import { formMessage } from '../i18n/es';
import { formatDateTime } from '../lib/dates';
import { formatMoney, normalizeAmount } from '../lib/money';

/** "Faltante: $900", "Sobrante: $200" or "La caja cuadró." */
function differenceLabel(difference: string | null): string {
  if (!difference || difference === '0.00') return 'La caja cuadró.';
  return difference.startsWith('-')
    ? `Faltante: ${formatMoney(difference.slice(1))}`
    : `Sobrante: ${formatMoney(difference)}`;
}

function useForm() {
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [alert, setAlert] = useState<string | null>(null);
  return {
    errors,
    alert,
    fail(error: unknown) {
      const apiError = error instanceof ApiError ? error : new ApiError(0, 'unknown_error');
      setErrors(apiError.fieldErrors());
      setAlert(formMessage(apiError.code));
    },
  };
}

function OpenForm({ onDone }: { onDone: () => void }) {
  const [amount, setAmount] = useState('');
  const { errors, alert, fail } = useForm();

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    try {
      await api.post('/api/cash-register/open', { openingAmount: normalizeAmount(amount) || '0' });
      onDone();
    } catch (error) {
      fail(error);
    }
  }

  return (
    <form onSubmit={onSubmit} className="space-y-3 rounded-xl bg-white p-4 shadow-sm" noValidate>
      <h2 className="font-semibold">La caja está cerrada</h2>
      <TextField
        label="Base en efectivo"
        inputMode="decimal"
        hint="El dinero con el que empiezas, para dar cambio. Puede ser 0."
        value={amount}
        onChange={(e) => setAmount(e.target.value)}
        error={errors.openingAmount}
      />
      <FormAlert message={alert} />
      <Button type="submit" className="w-full">
        Abrir caja
      </Button>
    </form>
  );
}

function OpenRegister({ session, onDone }: { session: CashSession; onDone: () => void }) {
  const { business } = useMe();
  const [counted, setCounted] = useState('');
  const [note, setNote] = useState('');
  const { errors, alert, fail } = useForm();

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    try {
      await api.post(
        '/api/cash-register/close',
        compact({ countedAmount: normalizeAmount(counted), note: note.trim() }),
      );
      onDone();
    } catch (error) {
      fail(error);
    }
  }

  const flows: [string, string, '+' | '−'][] = [
    ['Ventas en efectivo', session.cashSales, '+'],
    ['Abonos de clientes', session.cashPayments, '+'],
    ['Gastos', session.cashExpenses, '−'],
    ['Pagos a proveedores', session.cashSupplierPayments, '−'],
  ];

  return (
    <div className="space-y-4 lg:grid lg:grid-cols-2 lg:items-start lg:gap-6 lg:space-y-0">
      <section aria-label="Caja abierta" className="space-y-3 rounded-xl bg-white p-4 shadow-sm">
        <h2 className="font-semibold">
          Caja abierta desde {formatDateTime(session.openedAt, business.timeZone)}
        </h2>
        <ul aria-label="Movimientos en efectivo" className="space-y-1 text-sm">
          <li className="flex justify-between">
            <span>Base</span>
            <span>{formatMoney(session.opening)}</span>
          </li>
          {flows.map(([label, amount, sign]) => (
            <li key={label} className="flex justify-between">
              <span>{label}</span>
              <span className={sign === '+' ? 'text-emerald-800' : 'text-red-700'}>
                {sign}
                {formatMoney(amount)}
              </span>
            </li>
          ))}
        </ul>
        <dl className="flex items-baseline justify-between border-t border-slate-100 pt-3">
          <dt className="font-semibold">Efectivo esperado</dt>
          <dd className="text-2xl font-bold">{formatMoney(session.expected)}</dd>
        </dl>
        <p className="text-xs text-slate-600">
          Cuenta lo registrado desde que abriste la caja. Las tarjetas, transferencias y fiados no
          entran al cajón.
        </p>
      </section>
      <form onSubmit={onSubmit} className="space-y-3 rounded-xl bg-white p-4 shadow-sm" noValidate>
        <h2 className="font-semibold">Cerrar caja</h2>
        <TextField
          label="Efectivo contado"
          inputMode="decimal"
          hint="Cuenta los billetes y monedas del cajón."
          value={counted}
          onChange={(e) => setCounted(e.target.value)}
          error={errors.countedAmount}
        />
        <TextField
          label="Nota (opcional)"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          error={errors.note}
        />
        <FormAlert message={alert} />
        <Button type="submit" className="w-full">
          Cerrar caja
        </Button>
      </form>
    </div>
  );
}

export function CashRegisterPage() {
  const { business } = useMe();
  const [state, setState] = useState<{ open: CashSession | null; last: CashSession | null } | null>(
    null,
  );
  const [history, setHistory] = useState<CashSession[]>([]);
  const [version, setVersion] = useState(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .get<{ open: CashSession | null; last: CashSession | null }>('/api/cash-register')
      .then(setState, (e: unknown) => setError(formMessage(e instanceof ApiError ? e.code : '')));
    api.get<CashSession[]>('/api/cash-register/history').then(setHistory, () => undefined);
  }, [version]);

  const reload = () => setVersion((v) => v + 1);
  if (!state) return error ? <FormAlert message={error} /> : <Loading />;

  return (
    <div className="space-y-5">
      <h1 className="text-xl font-bold">Caja</h1>
      {state.open ? (
        <OpenRegister session={state.open} onDone={reload} />
      ) : (
        <div className="space-y-4 lg:max-w-md">
          {state.last && (
            <section
              aria-label="Último cierre"
              className="space-y-1 rounded-xl bg-white p-4 shadow-sm"
            >
              <h2 className="font-semibold">
                Último cierre: {formatDateTime(state.last.closedAt!, business.timeZone)}
              </h2>
              <p className="text-sm text-slate-600">
                Esperado {formatMoney(state.last.expected)} · Contado{' '}
                {formatMoney(state.last.counted ?? '0')}
              </p>
              <p
                className={`text-lg font-semibold ${
                  state.last.difference?.startsWith('-') ? 'text-red-700' : 'text-emerald-800'
                }`}
              >
                {differenceLabel(state.last.difference)}
              </p>
            </section>
          )}
          <OpenForm onDone={reload} />
        </div>
      )}
      {history.length > 0 && (
        <section className="rounded-xl bg-white shadow-sm">
          <h2 className="px-4 pt-4 font-semibold">Cierres anteriores</h2>
          <ul aria-label="Cierres anteriores" className="divide-y divide-slate-100">
            {history.map((session) => (
              <li
                key={session.id}
                className="flex items-center justify-between gap-3 px-4 py-3 text-sm"
              >
                <span>
                  <span className="block font-medium">
                    {formatDateTime(session.closedAt!, business.timeZone)}
                  </span>
                  <span className="block text-slate-600">
                    Esperado {formatMoney(session.expected)} · Contado{' '}
                    {formatMoney(session.counted ?? '0')}
                  </span>
                </span>
                <span className="shrink-0 font-semibold">
                  {differenceLabel(session.difference)}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}
