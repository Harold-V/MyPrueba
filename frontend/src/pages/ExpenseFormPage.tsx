import { useEffect, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router';
import { api, ApiError, compact } from '../api/client';
import type { Expense, ExpenseCategory, PaymentMethod, Product, Supplier } from '../api/types';
import { useMe } from '../auth/session-context';
import { ConfirmDelete } from '../components/ConfirmDelete';
import { Button, FormAlert, Loading, SelectField, TextField } from '../components/ui';
import { cashPaymentMethods, expenseCategories } from '../i18n/es';
import { localDateOf, todayIn } from '../lib/dates';
import { formatMoney, lineTotal, normalizeAmount, sumAmounts } from '../lib/money';
import { normalizeQuantity } from '../lib/quantity';
import { occurredAtFor, useMovementForm } from './useMovementForm';

interface Line {
  key: string;
  /** null when the product was deleted after the purchase; the line keeps its own name and cost. */
  productId: string | null;
  name: string;
  quantity: string;
  unitCost: string;
}

/** Registers a supplier while recording an expense, without leaving the form. */
function NewSupplier({ onCreated }: { onCreated: (supplier: Supplier) => void }) {
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});

  async function create() {
    try {
      const supplier = await api.post<Supplier>('/api/suppliers', compact({ name, phone }));
      onCreated(supplier);
      setOpen(false);
      setName('');
      setPhone('');
      setErrors({});
    } catch (error) {
      setErrors(error instanceof ApiError ? error.fieldErrors() : {});
    }
  }

  if (!open) {
    return (
      <button
        type="button"
        className="text-sm font-medium text-accent underline underline-offset-2"
        onClick={() => setOpen(true)}
      >
        Nuevo proveedor
      </button>
    );
  }
  return (
    <div className="space-y-3 rounded-lg border border-line bg-surface p-3">
      <TextField
        label="Nombre del proveedor"
        value={name}
        onChange={(e) => setName(e.target.value)}
        error={errors.name}
      />
      <TextField
        label="Teléfono del proveedor (opcional)"
        type="tel"
        value={phone}
        onChange={(e) => setPhone(e.target.value)}
        error={errors.phone}
      />
      <div className="flex gap-2">
        <Button onClick={() => void create()}>Crear proveedor</Button>
        <Button variant="secondary" onClick={() => setOpen(false)}>
          Cancelar
        </Button>
      </div>
    </div>
  );
}

export function ExpenseFormPage() {
  const { id } = useParams();
  const { business } = useMe();
  const { errors, alert, busy, save, remove, fail } = useMovementForm('/api/expenses', id);
  const [loaded, setLoaded] = useState(!id);
  const [products, setProducts] = useState<Product[]>([]);
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [kind, setKind] = useState<'FREE' | 'ITEMS'>('FREE');
  const [amount, setAmount] = useState('');
  const [lines, setLines] = useState<Line[]>([]);
  const [category, setCategory] = useState<ExpenseCategory | ''>('');
  const [concept, setConcept] = useState('');
  const [status, setStatus] = useState<'PAID' | 'CREDIT'>('PAID');
  const [dueDate, setDueDate] = useState('');
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('CASH');
  const [supplierId, setSupplierId] = useState('');
  const [date, setDate] = useState('');
  const [original, setOriginal] = useState<string | null>(null);
  const [note, setNote] = useState('');

  useEffect(() => {
    api.get<Product[]>('/api/products').then(setProducts, () => undefined);
    api.get<Supplier[]>('/api/suppliers').then(setSuppliers, () => undefined);
    if (!id) return;
    api.get<Expense>(`/api/expenses/${id}`).then((expense) => {
      const items = expense.items ?? [];
      setKind(items.length > 0 ? 'ITEMS' : 'FREE');
      setAmount(items.length > 0 ? '' : expense.amount.replace(/\.00$/, ''));
      setLines(
        items.map((item, index) => ({
          key: item.productId ?? `line-${index}`,
          productId: item.productId,
          name: item.name,
          quantity: item.quantity,
          unitCost: item.unitCost,
        })),
      );
      setCategory(expense.category);
      setConcept(expense.concept ?? '');
      const credit = expense.status === 'CREDIT' || expense.paymentMethod === 'CREDIT';
      setStatus(credit ? 'CREDIT' : 'PAID');
      setPaymentMethod(credit ? 'CASH' : expense.paymentMethod);
      setDueDate(expense.dueDate ?? '');
      setSupplierId(expense.supplierId ?? '');
      setOriginal(expense.occurredAt);
      setDate(localDateOf(expense.occurredAt, business.timeZone));
      setNote(expense.note ?? '');
      setLoaded(true);
    }, fail);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, business.timeZone]);

  const total = sumAmounts(
    lines.map(
      (line) => lineTotal(normalizeQuantity(line.quantity), normalizeAmount(line.unitCost)) ?? '0',
    ),
  );

  function addProduct(productId: string) {
    const product = products.find((p) => p.id === productId);
    if (!product || lines.some((l) => l.productId === productId)) return;
    setLines([
      ...lines,
      {
        key: productId,
        productId,
        name: product.name,
        quantity: '1',
        unitCost: product.cost ?? '',
      },
    ]);
  }

  function updateLine(index: number, change: Partial<Line>) {
    setLines(lines.map((line, i) => (i === index ? { ...line, ...change } : line)));
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    const clientErrors: Record<string, string> = {};
    if (kind === 'FREE' && !amount.trim()) clientErrors.amount = 'required';
    if (kind === 'FREE' && !category) clientErrors.category = 'required';
    if (kind === 'ITEMS' && lines.length === 0) clientErrors.items = 'expense_kind';
    if (status === 'CREDIT' && !supplierId) clientErrors.supplierId = 'required';
    const common = compact({
      concept: concept.trim(),
      // An expense on credit has no payment method yet: it is paid later to the supplier.
      ...(status === 'CREDIT' ? { status, dueDate } : { paymentMethod }),
      occurredAt: occurredAtFor(date, original, business.timeZone),
      note: note.trim(),
      supplierId,
    });
    const body =
      kind === 'FREE'
        ? { amount: normalizeAmount(amount), category, ...common }
        : {
            items: lines.map((line) =>
              compact({
                ...(line.productId ? { productId: line.productId } : { name: line.name }),
                quantity: normalizeQuantity(line.quantity),
                unitCost: normalizeAmount(line.unitCost),
              }),
            ),
            category: 'PURCHASES',
            ...common,
          };
    void save(body, clientErrors);
  }

  if (!loaded) return <Loading />;

  return (
    <form onSubmit={onSubmit} className="max-w-xl space-y-4" noValidate>
      <h1 className="text-xl font-bold">{id ? 'Editar gasto' : 'Nuevo gasto'}</h1>

      <fieldset className="flex flex-wrap gap-x-4 gap-y-2">
        <legend className="sr-only">Tipo de gasto</legend>
        {(
          [
            ['FREE', 'Gasto libre'],
            ['ITEMS', 'Compra con productos'],
          ] as const
        ).map(([value, text]) => (
          <label key={value} className="flex items-center gap-2 text-base">
            <input
              type="radio"
              name="kind"
              value={value}
              checked={kind === value}
              onChange={() => setKind(value)}
              className="h-5 w-5 accent-accent"
            />
            {text}
          </label>
        ))}
      </fieldset>

      {kind === 'FREE' ? (
        <>
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
        </>
      ) : (
        <div className="space-y-3">
          <p className="text-sm text-muted">
            Lo que compres entra a tu inventario. Categoría: {expenseCategories.PURCHASES}.
          </p>
          <SelectField
            label="Agregar producto"
            placeholder={products.length ? 'Elige un producto' : 'Aún no tienes productos'}
            options={Object.fromEntries(
              products
                .filter((p) => !lines.some((l) => l.productId === p.id))
                .map((p) => [p.id, p.name]),
            )}
            value=""
            onChange={(e) => addProduct(e.target.value)}
            error={errors.items ?? errors.amount ?? errors.category}
          />
          {products.length === 0 && (
            <Link
              to="/productos"
              className="text-sm font-medium text-accent underline underline-offset-2"
            >
              Crear productos
            </Link>
          )}
          <ul className="space-y-3">
            {lines.map((line, index) => (
              <li key={line.key} className="card p-3">
                <div className="flex items-center justify-between">
                  <span className="font-medium">{line.name}</span>
                  <button
                    type="button"
                    className="text-sm text-negative underline"
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
                    label="Costo unitario"
                    inputMode="decimal"
                    value={line.unitCost}
                    onChange={(e) => updateLine(index, { unitCost: e.target.value })}
                    error={errors[`items[${index}].unitCost`]}
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
      <fieldset className="flex gap-4">
        <legend className="mb-1 text-sm font-medium text-ink">Estado del pago</legend>
        {(
          [
            ['PAID', 'Pagado'],
            ['CREDIT', 'A crédito'],
          ] as const
        ).map(([value, text]) => (
          <label key={value} className="flex items-center gap-2 text-base">
            <input
              type="radio"
              name="status"
              value={value}
              checked={status === value}
              onChange={() => setStatus(value)}
              className="h-5 w-5 accent-accent"
            />
            {text}
          </label>
        ))}
      </fieldset>
      {status === 'PAID' ? (
        <SelectField
          label="Método de pago"
          options={cashPaymentMethods}
          value={paymentMethod}
          onChange={(e) => setPaymentMethod(e.target.value as PaymentMethod)}
          error={errors.paymentMethod}
        />
      ) : (
        <TextField
          label="Fecha de vencimiento (opcional)"
          type="date"
          hint="Hasta cuándo tienes para pagarle al proveedor."
          value={dueDate}
          onChange={(e) => setDueDate(e.target.value)}
          error={errors.dueDate}
        />
      )}
      <SelectField
        label="Proveedor"
        placeholder={status === 'CREDIT' ? 'Elige a quién le debes' : 'Sin proveedor'}
        hint={
          status === 'CREDIT'
            ? 'Obligatorio en gastos a crédito: la deuda queda a su nombre.'
            : 'Opcional.'
        }
        options={Object.fromEntries(suppliers.map((s) => [s.id, s.name]))}
        value={supplierId}
        onChange={(e) => setSupplierId(e.target.value)}
        error={errors.supplierId}
      />
      <NewSupplier
        onCreated={(supplier) => {
          setSuppliers([...suppliers, supplier]);
          setSupplierId(supplier.id);
        }}
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
      {/* Next to the button: after submitting, the top of a long form is out of view. */}
      <FormAlert message={alert} />
      <Button type="submit" className="w-full" disabled={busy}>
        Guardar gasto
      </Button>
      {id && <ConfirmDelete label="Eliminar" question="¿Eliminar este gasto?" onConfirm={remove} />}
    </form>
  );
}
