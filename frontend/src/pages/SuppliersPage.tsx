import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { api, ApiError, compact } from '../api/client';
import type {
  Payables,
  PaymentMethod,
  Supplier,
  SupplierAccount,
  SupplierPayment,
  SupplierPurchases,
} from '../api/types';
import { useMe } from '../auth/session-context';
import { ConfirmDelete } from '../components/ConfirmDelete';
import { Button, FormAlert, Loading, SelectField, TextField } from '../components/ui';
import { cashPaymentMethods, formMessage } from '../i18n/es';
import { formatDate, formatDateTime, localDateOf, todayIn } from '../lib/dates';
import { formatMoney, normalizeAmount } from '../lib/money';
import { occurredAtFor } from './useMovementForm';

type CashMethod = Exclude<PaymentMethod, 'CREDIT'>;

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

/** "Le debes $60.000", "Saldo a favor $5.000" or "Al día". */
function debtLabel(debt: string | undefined): string {
  if (!debt || debt === '0.00') return 'Al día';
  return debt.startsWith('-')
    ? `Saldo a favor ${formatMoney(debt.slice(1))}`
    : `Le debes ${formatMoney(debt)}`;
}

/** Debts always have two decimals, so dropping the dot gives exact integer cents. */
const toCents = (debt: string | undefined) => BigInt((debt ?? '0.00').replace('.', ''));

export function SuppliersPage() {
  const { business } = useMe();
  const [suppliers, setSuppliers] = useState<Supplier[] | null>(null);
  const [payables, setPayables] = useState<Payables | null>(null);
  const [version, setVersion] = useState(0);
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [documentId, setDocumentId] = useState('');
  const { errors, alert, clear, fail } = useErrors();

  useEffect(() => {
    let active = true;
    Promise.all([api.get<Supplier[]>('/api/suppliers'), api.get<Payables>('/api/payables')]).then(
      ([list, debts]) => {
        if (!active) return;
        setSuppliers(list);
        setPayables(debts);
      },
      fail,
    );
    return () => {
      active = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [version]);

  const debts = Object.fromEntries((payables?.suppliers ?? []).map((s) => [s.supplierId, s]));
  // Suppliers you owe the most come first; the rest keep alphabetical order.
  const sorted = [...(suppliers ?? [])].sort((a, b) => {
    const diff = toCents(debts[b.id]?.debt) - toCents(debts[a.id]?.debt);
    return diff > 0n ? 1 : diff < 0n ? -1 : 0;
  });

  async function onCreate(event: FormEvent) {
    event.preventDefault();
    try {
      await api.post('/api/suppliers', compact({ name, phone, document: documentId }));
      setName('');
      setPhone('');
      setDocumentId('');
      clear();
      setVersion((v) => v + 1);
    } catch (error) {
      fail(error);
    }
  }

  return (
    <div className="space-y-5">
      <h1 className="text-xl font-bold">Proveedores y cuentas por pagar</h1>
      <section
        aria-label="Por pagar"
        className="rounded-xl bg-white p-4 text-center shadow-sm lg:p-6"
      >
        <p className="text-sm text-slate-600">Total por pagar</p>
        <p className="text-2xl font-bold lg:text-3xl">{formatMoney(payables?.total ?? '0')}</p>
      </section>
      <div className="space-y-5 lg:grid lg:grid-cols-[22rem_1fr] lg:items-start lg:gap-6 lg:space-y-0">
        <form
          onSubmit={onCreate}
          className="space-y-3 rounded-xl bg-white p-4 shadow-sm lg:sticky lg:top-8"
          noValidate
        >
          <FormAlert message={alert} />
          <TextField
            label="Nombre"
            value={name}
            onChange={(e) => setName(e.target.value)}
            error={errors.name}
          />
          <TextField
            label="Teléfono (opcional)"
            type="tel"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            error={errors.phone}
          />
          <TextField
            label="NIT o documento (opcional)"
            value={documentId}
            onChange={(e) => setDocumentId(e.target.value)}
            error={errors.document}
          />
          <Button type="submit" className="w-full">
            Agregar proveedor
          </Button>
        </form>
        <div className="space-y-3">
          {suppliers?.length === 0 && (
            <p className="rounded-xl bg-white p-6 text-center text-slate-600">
              Aún no tienes proveedores. Agrégalos para registrar compras y lo que les debes.
            </p>
          )}
          <ul className="divide-y divide-slate-200 rounded-xl bg-white shadow-sm">
            {sorted.map((supplier) => {
              const debt = debts[supplier.id];
              return (
                <li key={supplier.id} aria-label={supplier.name}>
                  <Link
                    to={`/proveedores/${supplier.id}`}
                    aria-label={`Ver cuenta de ${supplier.name}`}
                    className="flex items-center justify-between gap-3 px-4 py-3"
                  >
                    <span className="min-w-0">
                      <span className="block truncate font-medium">{supplier.name}</span>
                      <span className="block text-sm text-slate-600">
                        {[
                          supplier.phone,
                          debt?.oldestDebtAt &&
                            `Desde el ${formatDate(debt.oldestDebtAt, business.timeZone)}`,
                        ]
                          .filter(Boolean)
                          .join(' · ')}
                      </span>
                    </span>
                    <span className="flex shrink-0 flex-col items-end gap-1">
                      <span
                        className={`text-sm font-semibold ${debt ? 'text-red-700' : 'text-emerald-800'}`}
                      >
                        {debtLabel(debt?.debt)}
                      </span>
                      {debt?.overdue && (
                        <span className="rounded-full bg-red-100 px-2 py-0.5 text-xs font-semibold text-red-800">
                          Vencida
                        </span>
                      )}
                    </span>
                  </Link>
                </li>
              );
            })}
          </ul>
        </div>
      </div>
    </div>
  );
}

function SupplierPaymentForm({ supplierId, onSaved }: { supplierId: string; onSaved: () => void }) {
  const { business } = useMe();
  const [amount, setAmount] = useState('');
  const [method, setMethod] = useState<CashMethod>('CASH');
  const [date, setDate] = useState('');
  const [note, setNote] = useState('');
  const { errors, alert, clear, fail } = useErrors();

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    try {
      await api.post(
        '/api/supplier-payments',
        compact({
          supplierId,
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
      <h2 className="font-semibold">Registrar pago</h2>
      <FormAlert message={alert} />
      <TextField
        label="Valor del pago"
        inputMode="decimal"
        value={amount}
        onChange={(e) => setAmount(e.target.value)}
        error={errors.amount}
      />
      <SelectField
        label="Método de pago"
        options={cashPaymentMethods}
        value={method}
        onChange={(e) => setMethod(e.target.value as CashMethod)}
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
        Registrar pago
      </Button>
    </form>
  );
}

export function SupplierPage() {
  const { id = '' } = useParams();
  const { business } = useMe();
  const navigate = useNavigate();
  const [account, setAccount] = useState<SupplierAccount | null>(null);
  const [supplier, setSupplier] = useState<Supplier | null>(null);
  const [purchases, setPurchases] = useState<SupplierPurchases | null>(null);
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [documentId, setDocumentId] = useState('');
  const [note, setNote] = useState('');
  const [version, setVersion] = useState(0);
  const [saved, setSaved] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const { errors, alert, clear, fail } = useErrors();

  useEffect(() => {
    api.get<SupplierAccount>(`/api/suppliers/${id}/account`).then(setAccount, fail);
    api
      .get<SupplierPurchases>(`/api/suppliers/${id}/purchases`)
      .then(setPurchases, () => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, version]);

  useEffect(() => {
    api.get<Supplier[]>('/api/suppliers').then((list) => {
      const found = list.find((s) => s.id === id) ?? null;
      setSupplier(found);
      setName(found?.name ?? '');
      setPhone(found?.phone ?? '');
      setDocumentId(found?.document ?? '');
      setNote(found?.note ?? '');
    }, fail);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  async function onSave(event: FormEvent) {
    event.preventDefault();
    setSaved(false);
    try {
      const updated = await api.put<Supplier>(
        `/api/suppliers/${id}`,
        compact({ name, phone, document: documentId, note }),
      );
      setSupplier(updated);
      clear();
      setSaved(true);
      setVersion((v) => v + 1);
    } catch (error) {
      fail(error);
    }
  }

  async function onDelete() {
    try {
      await api.delete(`/api/suppliers/${id}`);
      navigate('/proveedores');
    } catch (error) {
      // Shown next to the button: the page-level alert may be scrolled out of view.
      setDeleteError(formMessage(error instanceof ApiError ? error.code : 'unknown_error'));
    }
  }

  if (!account) return alert ? <FormAlert message={alert} /> : <Loading />;

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-xl font-bold">{account.supplier.name}</h1>
        <p
          className={`text-lg font-semibold ${account.debt === '0.00' ? 'text-emerald-800' : 'text-red-700'}`}
        >
          {debtLabel(account.debt)}
        </p>
      </div>
      <FormAlert message={alert} />
      <div className="space-y-5 lg:grid lg:grid-cols-2 lg:items-start lg:gap-6 lg:space-y-0">
        <div className="space-y-5">
          <SupplierPaymentForm supplierId={id} onSaved={() => setVersion((v) => v + 1)} />
          <form
            onSubmit={onSave}
            className="space-y-3 rounded-xl bg-white p-4 shadow-sm"
            noValidate
          >
            <h2 className="font-semibold">Datos del proveedor</h2>
            <TextField
              label="Nombre"
              value={name}
              onChange={(e) => setName(e.target.value)}
              error={errors.name}
            />
            <TextField
              label="Teléfono (opcional)"
              type="tel"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              error={errors.phone}
            />
            <TextField
              label="NIT o documento (opcional)"
              value={documentId}
              onChange={(e) => setDocumentId(e.target.value)}
              error={errors.document}
            />
            <TextField
              label="Nota (opcional)"
              hint="Por ejemplo, qué días visita o cómo hacer pedidos."
              value={note}
              onChange={(e) => setNote(e.target.value)}
              error={errors.note}
            />
            <Button type="submit">Guardar proveedor</Button>
            <p role="status" className="text-sm text-emerald-800">
              {saved && supplier ? 'Cambios guardados.' : ''}
            </p>
            <ConfirmDelete
              label="Eliminar proveedor"
              question={`¿Eliminar a ${account.supplier.name}?`}
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
                    to={`/gastos/${p.id}`}
                    className="flex items-center justify-between gap-3 px-4 py-3"
                  >
                    <span>
                      <span className="block font-medium">{p.description ?? 'Compra'}</span>
                      <span className="block text-sm text-slate-600">
                        {formatDateTime(p.occurredAt, business.timeZone)}
                      </span>
                    </span>
                    <span className="flex shrink-0 flex-col items-end">
                      <span className="font-semibold">{formatMoney(p.total)}</span>
                      <span className="text-xs text-slate-600">
                        {p.status === 'CREDIT' ? 'A crédito' : 'Pagada'}
                      </span>
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
            {purchases?.items.length === 0 && (
              <p className="p-4 text-slate-600">Aún no le has comprado a este proveedor.</p>
            )}
          </section>
          <section className="rounded-xl bg-white shadow-sm">
            <h2 className="px-4 pt-4 font-semibold">Compras a crédito y pagos</h2>
            {account.entries.length === 0 && (
              <p className="p-4 text-slate-600">Sin compras a crédito ni pagos todavía.</p>
            )}
            <ul aria-label="Compras a crédito y pagos" className="divide-y divide-slate-100">
              {account.entries.map((entry) => {
                const isPurchase = entry.type === 'EXPENSE';
                const title = isPurchase
                  ? entry.description
                    ? `A crédito: ${entry.description}`
                    : 'Compra a crédito'
                  : 'Pago';
                return (
                  <li key={entry.id}>
                    <Link
                      to={isPurchase ? `/gastos/${entry.id}` : `/pagos-proveedor/${entry.id}`}
                      className="flex items-center justify-between gap-3 px-4 py-3"
                    >
                      <span>
                        <span className="block font-medium">{title}</span>
                        <span className="block text-sm text-slate-600">
                          {[
                            formatDateTime(entry.occurredAt, business.timeZone),
                            isPurchase ? null : entry.description,
                          ]
                            .filter(Boolean)
                            .join(' · ')}
                        </span>
                      </span>
                      <span
                        className={`shrink-0 font-semibold ${isPurchase ? 'text-red-700' : 'text-emerald-800'}`}
                      >
                        {isPurchase ? '+' : '−'}
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

/** Edit or delete a payment to a supplier. */
export function SupplierPaymentPage() {
  const { id = '' } = useParams();
  const { business } = useMe();
  const navigate = useNavigate();
  const [payment, setPayment] = useState<SupplierPayment | null>(null);
  const [amount, setAmount] = useState('');
  const [method, setMethod] = useState<PaymentMethod>('CASH');
  const [date, setDate] = useState('');
  const [note, setNote] = useState('');
  const { errors, alert, fail } = useErrors();

  useEffect(() => {
    api.get<SupplierPayment>(`/api/supplier-payments/${id}`).then((p) => {
      setPayment(p);
      setAmount(p.amount.replace(/\.00$/, ''));
      setMethod(p.paymentMethod);
      setDate(localDateOf(p.occurredAt, business.timeZone));
      setNote(p.note ?? '');
    }, fail);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, business.timeZone]);

  const back = () => navigate(payment?.supplierId ? `/proveedores/${payment.supplierId}` : '/');

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    try {
      await api.put(
        `/api/supplier-payments/${id}`,
        compact({
          supplierId: payment?.supplierId,
          amount: normalizeAmount(amount),
          paymentMethod: method,
          occurredAt: occurredAtFor(date, payment?.occurredAt ?? null, business.timeZone),
          note: note.trim(),
        }),
      );
      back();
    } catch (error) {
      fail(error);
    }
  }

  async function onDelete() {
    try {
      await api.delete(`/api/supplier-payments/${id}`);
      back();
    } catch (error) {
      fail(error);
    }
  }

  if (!payment) return alert ? <FormAlert message={alert} /> : <Loading />;

  return (
    <form onSubmit={onSubmit} className="max-w-xl space-y-4" noValidate>
      <h1 className="text-xl font-bold">Pago a {payment.supplierName}</h1>
      <TextField
        label="Valor del pago"
        inputMode="decimal"
        value={amount}
        onChange={(e) => setAmount(e.target.value)}
        error={errors.amount}
      />
      <SelectField
        label="Método de pago"
        options={cashPaymentMethods}
        value={method}
        onChange={(e) => setMethod(e.target.value as PaymentMethod)}
        error={errors.paymentMethod}
      />
      <TextField
        label="Fecha"
        type="date"
        max={todayIn(business.timeZone)}
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
      {!payment.supplierId && (
        <p className="text-sm text-slate-600">
          El proveedor fue eliminado: este pago ya no se puede editar, solo eliminar.
        </p>
      )}
      <FormAlert message={alert} />
      <Button type="submit" className="w-full" disabled={!payment.supplierId}>
        Guardar pago
      </Button>
      <ConfirmDelete label="Eliminar" question="¿Eliminar este pago?" onConfirm={onDelete} />
    </form>
  );
}
