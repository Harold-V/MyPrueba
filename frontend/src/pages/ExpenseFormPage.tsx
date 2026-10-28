import { useEffect, useState, type FormEvent } from 'react';
import { useParams } from 'react-router';
import { api, compact } from '../api/client';
import type { Expense, ExpenseCategory, PaymentMethod } from '../api/types';
import { useMe } from '../auth/session-context';
import { ConfirmDelete } from '../components/ConfirmDelete';
import { Button, FormAlert, Loading, SelectField, TextField } from '../components/ui';
import { cashPaymentMethods, expenseCategories } from '../i18n/es';
import { localDateOf, todayIn } from '../lib/dates';
import { normalizeAmount } from '../lib/money';
import { occurredAtFor, useMovementForm } from './useMovementForm';

export function ExpenseFormPage() {
  const { id } = useParams();
  const { business } = useMe();
  const { errors, alert, busy, save, remove, fail } = useMovementForm('/api/expenses', id);
  const [loaded, setLoaded] = useState(!id);
  const [amount, setAmount] = useState('');
  const [category, setCategory] = useState<ExpenseCategory | ''>('');
  const [concept, setConcept] = useState('');
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('CASH');
  const [date, setDate] = useState('');
  const [original, setOriginal] = useState<string | null>(null);
  const [note, setNote] = useState('');

  useEffect(() => {
    if (!id) return;
    api.get<Expense>(`/api/expenses/${id}`).then((expense) => {
      setAmount(expense.amount.replace(/\.00$/, ''));
      setCategory(expense.category);
      setConcept(expense.concept ?? '');
      setPaymentMethod(expense.paymentMethod);
      setOriginal(expense.occurredAt);
      setDate(localDateOf(expense.occurredAt, business.timeZone));
      setNote(expense.note ?? '');
      setLoaded(true);
    }, fail);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, business.timeZone]);

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    const clientErrors: Record<string, string> = {};
    if (!amount.trim()) clientErrors.amount = 'required';
    if (!category) clientErrors.category = 'required';
    void save(
      compact({
        amount: normalizeAmount(amount),
        category,
        concept: concept.trim(),
        paymentMethod,
        occurredAt: occurredAtFor(date, original, business.timeZone),
        note: note.trim(),
      }),
      clientErrors,
    );
  }

  if (!loaded) return <Loading />;

  return (
    <form onSubmit={onSubmit} className="max-w-xl space-y-4" noValidate>
      <h1 className="text-xl font-bold">{id ? 'Editar gasto' : 'Nuevo gasto'}</h1>
      <TextField
        label="Valor"
        inputMode="decimal"
        value={amount}
        onChange={(e) => setAmount(e.target.value)}
        error={errors.amount}
      />
      <SelectField
        label="Categoría"
        placeholder="Elige una categoría"
        options={expenseCategories}
        value={category}
        onChange={(e) => setCategory(e.target.value as ExpenseCategory)}
        error={errors.category}
      />
      <TextField
        label="Concepto (opcional)"
        value={concept}
        onChange={(e) => setConcept(e.target.value)}
        error={errors.concept}
      />
      <SelectField
        label="Método de pago"
        options={cashPaymentMethods}
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
      <FormAlert message={alert} />
      <Button type="submit" className="w-full" disabled={busy}>
        Guardar gasto
      </Button>
      {id && <ConfirmDelete label="Eliminar" question="¿Eliminar este gasto?" onConfirm={remove} />}
    </form>
  );
}
