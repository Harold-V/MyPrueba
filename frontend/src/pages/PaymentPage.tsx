import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router';
import { api, ApiError, compact } from '../api/client';
import type { CustomerPayment, PaymentMethod } from '../api/types';
import { useMe } from '../auth/session-context';
import { ConfirmDelete } from '../components/ConfirmDelete';
import { Button, FormAlert, Loading, SelectField, TextField } from '../components/ui';
import { cashPaymentMethods, formMessage } from '../i18n/es';
import { localDateOf, todayIn } from '../lib/dates';
import { normalizeAmount } from '../lib/money';
import { occurredAtFor } from './useMovementForm';

/** Edit or delete a customer payment (abono). */
export function PaymentPage() {
  const { id = '' } = useParams();
  const { business } = useMe();
  const navigate = useNavigate();
  const [payment, setPayment] = useState<CustomerPayment | null>(null);
  const [amount, setAmount] = useState('');
  const [method, setMethod] = useState<PaymentMethod>('CASH');
  const [date, setDate] = useState('');
  const [note, setNote] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [alert, setAlert] = useState<string | null>(null);

  function fail(error: unknown) {
    const apiError = error instanceof ApiError ? error : new ApiError(0, 'unknown_error');
    setErrors(apiError.fieldErrors());
    setAlert(formMessage(apiError.code));
  }

  useEffect(() => {
    api.get<CustomerPayment>(`/api/customer-payments/${id}`).then((p) => {
      setPayment(p);
      setAmount(p.amount.replace(/\.00$/, ''));
      setMethod(p.paymentMethod);
      setDate(localDateOf(p.occurredAt, business.timeZone));
      setNote(p.note ?? '');
    }, fail);
  }, [id, business.timeZone]);

  const back = () => navigate(payment?.customerId ? `/clientes/${payment.customerId}` : '/');

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    try {
      await api.put(
        `/api/customer-payments/${id}`,
        compact({
          customerId: payment?.customerId,
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
      await api.delete(`/api/customer-payments/${id}`);
      back();
    } catch (error) {
      fail(error);
    }
  }

  if (!payment) return alert ? <FormAlert message={alert} /> : <Loading />;

  return (
    <form onSubmit={onSubmit} className="max-w-xl space-y-4" noValidate>
      <h1 className="text-xl font-bold">Abono de {payment.customerName}</h1>
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
      {!payment.customerId && (
        <p className="text-sm text-muted">
          El cliente fue eliminado: este abono ya no se puede editar, solo eliminar.
        </p>
      )}
      <Button type="submit" className="w-full" disabled={!payment.customerId}>
        Guardar abono
      </Button>
      <ConfirmDelete label="Eliminar" question="¿Eliminar este abono?" onConfirm={onDelete} />
    </form>
  );
}
