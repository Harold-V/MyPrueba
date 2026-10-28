import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { api, ApiError, compact } from '../api/client';
import type {
  Customer,
  CustomerAccount,
  CustomerPurchases,
  PaymentMethod,
  Receivables,
} from '../api/types';
import { useMe } from '../auth/session-context';
import { ConfirmDelete } from '../components/ConfirmDelete';
import { Button, FormAlert, Loading, SelectField, TextField } from '../components/ui';
import { cashPaymentMethods, formMessage } from '../i18n/es';
import { formatDate, formatDateTime, todayIn } from '../lib/dates';
import { formatMoney, normalizeAmount } from '../lib/money';
import { occurredAtFor } from './useMovementForm';

function useErrors() {
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [alert, setAlert] = useState<string | null>(null);
  return {
    errors,
    alert,
    clear() {
      setErrors({});
      setAlert(null);
    },
    fail(error: unknown) {
      const apiError = error instanceof ApiError ? error : new ApiError(0, 'unknown_error');
      setErrors(apiError.fieldErrors());
      setAlert(formMessage(apiError.code));
    },
  };
}

/** "Debe $15.000", "Saldo a favor $5.000" or "Al día". */
function debtLabel(debt: string | undefined): string {
  if (!debt || debt === '0.00') return 'Al día';
  return debt.startsWith('-')
    ? `Saldo a favor ${formatMoney(debt.slice(1))}`
    : `Debe ${formatMoney(debt)}`;
}

function CustomerFields({
  name,
  phone,
  errors,
  onName,
  onPhone,
}: {
  name: string;
  phone: string;
  errors: Record<string, string>;
  onName: (value: string) => void;
  onPhone: (value: string) => void;
}) {
  return (
    <>
      <TextField
        label="Nombre"
        value={name}
        onChange={(e) => onName(e.target.value)}
        error={errors.name}
      />
      <TextField
        label="Teléfono (opcional)"
        type="tel"
        autoComplete="tel"
        value={phone}
        onChange={(e) => onPhone(e.target.value)}
        error={errors.phone}
      />
    </>
  );
}

export function CustomersPage() {
  const { business } = useMe();
  const [customers, setCustomers] = useState<Customer[] | null>(null);
  const [receivables, setReceivables] = useState<Receivables | null>(null);
  const [version, setVersion] = useState(0);
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const { errors, alert, clear, fail } = useErrors();

  useEffect(() => {
    let active = true;
    Promise.all([
      api.get<Customer[]>('/api/customers'),
      api.get<Receivables>('/api/receivables'),
    ]).then(([list, debts]) => {
      if (!active) return;
      setCustomers(list);
      setReceivables(debts);
    }, fail);
    return () => {
      active = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [version]);

  const debts = Object.fromEntries(
    (receivables?.customers ?? []).map((c) => [c.customerId, c.debt]),
  );
  const debtInfo = Object.fromEntries((receivables?.customers ?? []).map((c) => [c.customerId, c]));
  // Customers who owe the most come first; the rest keep alphabetical order.
  // Debts always have two decimals, so dropping the dot gives exact integer cents.
  const cents = (id: string) => BigInt((debts[id] ?? '0.00').replace('.', ''));
  const sorted = [...(customers ?? [])].sort((a, b) => {
    const diff = cents(b.id) - cents(a.id);
    return diff > 0n ? 1 : diff < 0n ? -1 : 0;
  });

  async function onCreate(event: FormEvent) {
    event.preventDefault();
    try {
      await api.post('/api/customers', compact({ name, phone }));
      setName('');
      setPhone('');
      clear();
      setVersion((v) => v + 1);
    } catch (error) {
      fail(error);
    }
  }

  return (
    <div className="space-y-5">
      <h1 className="text-xl font-bold">Clientes y deudas</h1>
      <section
        aria-label="Por cobrar"
        className="rounded-xl bg-white p-4 text-center shadow-sm lg:p-6"
      >
        <p className="text-sm text-slate-600">Total por cobrar</p>
        <p className="text-2xl font-bold lg:text-3xl">{formatMoney(receivables?.total ?? '0')}</p>
      </section>
      <div className="space-y-5 lg:grid lg:grid-cols-[22rem_1fr] lg:items-start lg:gap-6 lg:space-y-0">
        <form
          onSubmit={onCreate}
          className="space-y-3 rounded-xl bg-white p-4 shadow-sm lg:sticky lg:top-8"
          noValidate
        >
          <FormAlert message={alert} />
          <CustomerFields
            name={name}
            phone={phone}
            errors={errors}
            onName={setName}
            onPhone={setPhone}
          />
          <Button type="submit" className="w-full">
            Agregar cliente
          </Button>
        </form>
        <div className="space-y-3">
          {customers?.length === 0 && (
            <p className="rounded-xl bg-white p-6 text-center text-slate-600">
              Aún no tienes clientes. Agrégalos para fiarles y llevar sus cuentas.
            </p>
          )}
          <ul className="divide-y divide-slate-200 rounded-xl bg-white shadow-sm">
            {sorted.map((customer) => (
              <li key={customer.id} aria-label={customer.name}>
                <Link
                  to={`/clientes/${customer.id}`}
                  aria-label={`Ver cuenta de ${customer.name}`}
                  className="flex items-center justify-between gap-3 px-4 py-3"
                >
                  <span className="min-w-0">
                    <span className="block truncate font-medium">{customer.name}</span>
                    <span className="block text-sm text-slate-600">
                      {[
                        customer.phone,
                        debtInfo[customer.id]?.oldestDebtAt &&
                          `Debe desde el ${formatDate(debtInfo[customer.id]!.oldestDebtAt!, business.timeZone)}`,
                      ]
                        .filter(Boolean)
                        .join(' · ')}
                    </span>
                  </span>
                  <span className="flex shrink-0 flex-col items-end gap-1">
                    <span
                      className={`text-sm font-semibold ${debts[customer.id] ? 'text-red-700' : 'text-emerald-800'}`}
                    >
                      {debtLabel(debts[customer.id])}
                    </span>
                    {debtInfo[customer.id]?.overdue && (
                      <span className="rounded-full bg-red-100 px-2 py-0.5 text-xs font-semibold text-red-800">
                        Vencida
                      </span>
                    )}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  );
}

function PaymentForm({ customerId, onSaved }: { customerId: string; onSaved: () => void }) {
  const { business } = useMe();
  const [amount, setAmount] = useState('');
  const [method, setMethod] = useState<Exclude<PaymentMethod, 'CREDIT'>>('CASH');
  const [date, setDate] = useState('');
  const [note, setNote] = useState('');
  const { errors, alert, clear, fail } = useErrors();

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    try {
      await api.post(
        '/api/customer-payments',
        compact({
          customerId,
          amount: normalizeAmount(amount),
          paymentMethod: method,
          occurredAt: occurredAtFor(date, null, business.timeZone),
          note: note.trim(),
        }),
      );
      setAmount('');
      setDate('');
      setNote('');
      clear();
      onSaved();
    } catch (error) {
      fail(error);
    }
  }

  return (
    <form onSubmit={onSubmit} className="space-y-3 rounded-xl bg-white p-4 shadow-sm" noValidate>
      <h2 className="font-semibold">Registrar abono</h2>
      <FormAlert message={alert} />
      <TextField
        label="Valor del abono"
        inputMode="decimal"
        value={amount}
        onChange={(e) => setAmount(e.target.value)}
        error={errors.amount}
      />
      <SelectField
        label="Método de pago"
        options={cashPaymentMethods}
        value={method}
        onChange={(e) => setMethod(e.target.value as Exclude<PaymentMethod, 'CREDIT'>)}
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
      <Button type="submit" className="w-full">
        Registrar abono
      </Button>
    </form>
  );
}

export function CustomerPage() {
  const { id = '' } = useParams();
  const { business } = useMe();
  const navigate = useNavigate();
  const [account, setAccount] = useState<CustomerAccount | null>(null);
  const [customer, setCustomer] = useState<Customer | null>(null);
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [documentId, setDocumentId] = useState('');
  const [address, setAddress] = useState('');
  const [purchases, setPurchases] = useState<CustomerPurchases | null>(null);
  const [version, setVersion] = useState(0);
  const [saved, setSaved] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const { errors, alert, clear, fail } = useErrors();

  useEffect(() => {
    api.get<CustomerAccount>(`/api/customers/${id}/account`).then(setAccount, fail);
    api
      .get<CustomerPurchases>(`/api/customers/${id}/purchases`)
      .then(setPurchases, () => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, version]);

  useEffect(() => {
    api.get<Customer[]>('/api/customers').then((list) => {
      const found = list.find((c) => c.id === id) ?? null;
      setCustomer(found);
      setName(found?.name ?? '');
      setPhone(found?.phone ?? '');
      setDocumentId(found?.document ?? '');
      setAddress(found?.address ?? '');
    }, fail);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  async function onSave(event: FormEvent) {
    event.preventDefault();
    setSaved(false);
    try {
      const updated = await api.put<Customer>(
        `/api/customers/${id}`,
        compact({ name, phone, note: customer?.note, document: documentId, address }),
      );
      setCustomer(updated);
      clear();
      setSaved(true);
      setVersion((v) => v + 1);
    } catch (error) {
      fail(error);
    }
  }

  async function onDelete() {
    try {
      await api.delete(`/api/customers/${id}`);
      navigate('/clientes');
    } catch (error) {
      // Shown next to the button: the page-level alert may be scrolled out of view.
      setDeleteError(formMessage(error instanceof ApiError ? error.code : 'unknown_error'));
    }
  }

  if (!account) return alert ? <FormAlert message={alert} /> : <Loading />;

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-xl font-bold">{account.customer.name}</h1>
        <p
          className={`text-lg font-semibold ${account.debt === '0.00' ? 'text-emerald-800' : 'text-red-700'}`}
        >
          {debtLabel(account.debt)}
        </p>
      </div>
      <FormAlert message={alert} />
      <div className="space-y-5 lg:grid lg:grid-cols-2 lg:items-start lg:gap-6 lg:space-y-0">
        <div className="space-y-5">
          <PaymentForm customerId={id} onSaved={() => setVersion((v) => v + 1)} />
          <form
            onSubmit={onSave}
            className="space-y-3 rounded-xl bg-white p-4 shadow-sm"
            noValidate
          >
            <h2 className="font-semibold">Datos del cliente</h2>
            <CustomerFields
              name={name}
              phone={phone}
              errors={errors}
              onName={setName}
              onPhone={setPhone}
            />
            <TextField
              label="Documento (opcional)"
              value={documentId}
              onChange={(e) => setDocumentId(e.target.value)}
              error={errors.document}
            />
            <TextField
              label="Dirección (opcional)"
              autoComplete="street-address"
              value={address}
              onChange={(e) => setAddress(e.target.value)}
              error={errors.address}
            />
            <Button type="submit">Guardar cliente</Button>
            <p role="status" className="text-sm text-emerald-800">
              {saved ? 'Cambios guardados.' : ''}
            </p>
            <ConfirmDelete
              label="Eliminar cliente"
              question={`¿Eliminar a ${account.customer.name}?`}
              onConfirm={onDelete}
            />
            <FormAlert message={deleteError} />
          </form>
        </div>
        <div className="space-y-5">
          <section className="rounded-xl bg-white shadow-sm">
            <h2 className="px-4 pt-4 font-semibold">Historial de compras</h2>
            {purchases && (
              <p className="px-4 text-sm text-slate-600">
                {`Total comprado: ${formatMoney(purchases.total)} · ${purchases.count} ${purchases.count === 1 ? 'compra' : 'compras'}`}
              </p>
            )}
            <ul aria-label="Historial de compras" className="divide-y divide-slate-100">
              {purchases?.items.map((p) => (
                <li key={p.id}>
                  <Link
                    to={`/ventas/${p.id}`}
                    className="flex items-center justify-between gap-3 px-4 py-3"
                  >
                    <span>
                      <span className="block font-medium">{p.description ?? 'Venta'}</span>
                      <span className="block text-sm text-slate-600">
                        {formatDateTime(p.occurredAt, business.timeZone)}
                      </span>
                    </span>
                    <span className="flex shrink-0 flex-col items-end">
                      <span className="font-semibold">{formatMoney(p.total)}</span>
                      <span className="text-xs text-slate-600">
                        {p.status === 'CREDIT' ? 'Fiada' : 'Pagada'}
                      </span>
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
            {purchases?.items.length === 0 && (
              <p className="p-4 text-slate-600">Este cliente aún no tiene compras.</p>
            )}
          </section>
          <section className="rounded-xl bg-white shadow-sm">
            <h2 className="px-4 pt-4 font-semibold">Fiados y abonos</h2>
            {account.entries.length === 0 && (
              <p className="p-4 text-slate-600">Sin fiados ni abonos todavía.</p>
            )}
            <ul aria-label="Fiados y abonos" className="divide-y divide-slate-100">
              {account.entries.map((entry) => {
                const isSale = entry.type === 'SALE';
                const title = isSale
                  ? entry.description
                    ? `Fiado: ${entry.description}`
                    : 'Fiado'
                  : 'Abono';
                return (
                  <li key={entry.id}>
                    <Link
                      to={isSale ? `/ventas/${entry.id}` : `/abonos/${entry.id}`}
                      className="flex items-center justify-between gap-3 px-4 py-3"
                    >
                      <span>
                        <span className="block font-medium">{title}</span>
                        <span className="block text-sm text-slate-600">
                          {[
                            formatDateTime(entry.occurredAt, business.timeZone),
                            isSale ? null : entry.description,
                          ]
                            .filter(Boolean)
                            .join(' · ')}
                        </span>
                      </span>
                      <span
                        className={`shrink-0 font-semibold ${isSale ? 'text-red-700' : 'text-emerald-800'}`}
                      >
                        {isSale ? '+' : '−'}
                        {formatMoney(entry.amount)}
                      </span>
                    </Link>
                  </li>
                );
              })}
            </ul>
          </section>
        </div>
      </div>
    </div>
  );
}
