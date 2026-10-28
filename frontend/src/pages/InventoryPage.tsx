import { useEffect, useState, type FormEvent } from 'react';
import { api, ApiError, compact } from '../api/client';
import type { Category, Inventory, InventoryItem, StockMovement } from '../api/types';
import { useMe } from '../auth/session-context';
import { Button, FormAlert, SelectField, TextField } from '../components/ui';
import { adjustmentTypes, formMessage, stockMovementTypes } from '../i18n/es';
import { formatDateTime } from '../lib/dates';
import { formatMoney } from '../lib/money';
import { normalizeQuantity, signed } from '../lib/quantity';

type Panel = 'configure' | 'adjust' | 'history' | null;

function useErrors() {
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

/** Every product has stock; only the alert threshold is configurable here. */
function MinStockForm({
  item,
  onSaved,
  onCancel,
}: {
  item: InventoryItem;
  onSaved: (item: InventoryItem) => void;
  onCancel: () => void;
}) {
  const [minStock, setMinStock] = useState(item.minStock ?? '');
  const { errors, alert, fail } = useErrors();

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    try {
      onSaved(
        await api.put<InventoryItem>(
          `/api/inventory/${item.productId}`,
          compact({ minStock: normalizeQuantity(minStock) }),
        ),
      );
    } catch (error) {
      fail(error);
    }
  }

  return (
    <form onSubmit={onSubmit} className="mt-3 space-y-3" noValidate>
      <FormAlert message={alert} />
      <TextField
        label="Stock mínimo (opcional)"
        inputMode="decimal"
        hint="Te avisamos cuando el stock llegue a este número."
        value={minStock}
        onChange={(e) => setMinStock(e.target.value)}
        error={errors.minStock}
      />
      <div className="flex flex-wrap gap-2">
        <Button type="submit">Guardar</Button>
        <Button variant="secondary" onClick={onCancel}>
          Cancelar
        </Button>
      </div>
    </form>
  );
}

function AdjustmentForm({
  item,
  onSaved,
  onCancel,
}: {
  item: InventoryItem;
  onSaved: (item: InventoryItem) => void;
  onCancel: () => void;
}) {
  const [type, setType] = useState<keyof typeof adjustmentTypes>('PURCHASE');
  const [quantity, setQuantity] = useState('');
  const [note, setNote] = useState('');
  const { errors, alert, fail } = useErrors();

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    try {
      onSaved(
        await api.post<InventoryItem>(
          `/api/inventory/${item.productId}/adjustments`,
          compact({ type, quantity: normalizeQuantity(quantity), note: note.trim() }),
        ),
      );
    } catch (error) {
      fail(error);
    }
  }

  return (
    <form onSubmit={onSubmit} className="mt-3 space-y-3" noValidate>
      <FormAlert message={alert} />
      <SelectField
        label="Tipo de ajuste"
        options={adjustmentTypes}
        value={type}
        onChange={(e) => setType(e.target.value as keyof typeof adjustmentTypes)}
        error={errors.type}
      />
      <TextField
        label="Cantidad"
        inputMode="decimal"
        hint={type === 'COUNT' ? 'Lo que contaste: reemplaza el stock actual.' : undefined}
        value={quantity}
        onChange={(e) => setQuantity(e.target.value)}
        error={errors.quantity}
      />
      <TextField
        label="Nota (opcional)"
        value={note}
        onChange={(e) => setNote(e.target.value)}
        error={errors.note}
      />
      <div className="flex gap-2">
        <Button type="submit">Registrar ajuste</Button>
        <Button variant="secondary" onClick={onCancel}>
          Cancelar
        </Button>
      </div>
    </form>
  );
}

function History({ item }: { item: InventoryItem }) {
  const { business } = useMe();
  const [movements, setMovements] = useState<StockMovement[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .get<StockMovement[]>(`/api/inventory/${item.productId}/movements`)
      .then(setMovements, (e: { code?: string }) => setError(formMessage(e.code ?? '')));
  }, [item.productId]);

  if (error) return <FormAlert message={error} />;
  if (movements?.length === 0) {
    return <p className="mt-3 text-sm text-muted">Sin movimientos todavía.</p>;
  }
  return (
    <ul aria-label={`Historial de ${item.name}`} className="mt-3 divide-y divide-rule text-sm">
      {movements?.map((m, index) => (
        <li key={index} className="flex justify-between gap-3 py-2">
          <span>
            <span className="block font-medium">{stockMovementTypes[m.type]}</span>
            <span className="block text-muted">
              {[formatDateTime(m.occurredAt, business.timeZone), m.note]
                .filter(Boolean)
                .join(' · ')}
            </span>
          </span>
          <span className="shrink-0 font-semibold">{`${signed(m.change)} → ${m.stockAfter}`}</span>
        </li>
      ))}
    </ul>
  );
}

function InventoryRow({
  item,
  onChanged,
}: {
  item: InventoryItem;
  onChanged: (item: InventoryItem) => void;
}) {
  const [panel, setPanel] = useState<Panel>(null);
  const toggle = (next: Panel) => setPanel(panel === next ? null : next);
  const saved = (updated: InventoryItem) => {
    setPanel(null);
    onChanged(updated);
  };

  return (
    <li aria-label={item.name} className="bg-surface p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span>
          <span className="block font-medium">{item.name}</span>
          <span className="block text-sm text-muted">
            <span>{item.stock} en stock</span>
            {item.minStock && <span> · mínimo {item.minStock}</span>}
          </span>
        </span>
        {item.low && (
          <span className="rounded-full bg-pill-negative px-3 py-1 text-sm font-semibold text-pill-negative-ink">
            Stock bajo
          </span>
        )}
      </div>
      <div className="mt-3 flex flex-wrap gap-2">
        <Button
          variant="secondary"
          aria-label={`Ajustar ${item.name}`}
          aria-expanded={panel === 'adjust'}
          onClick={() => toggle('adjust')}
        >
          Ajustar
        </Button>
        <Button
          variant="secondary"
          aria-label={`Historial de ${item.name}`}
          aria-expanded={panel === 'history'}
          onClick={() => toggle('history')}
        >
          Historial
        </Button>
        <Button
          variant="secondary"
          aria-label={`Stock mínimo de ${item.name}`}
          aria-expanded={panel === 'configure'}
          onClick={() => toggle('configure')}
        >
          Stock mínimo
        </Button>
      </div>
      {panel === 'configure' && (
        <MinStockForm item={item} onSaved={saved} onCancel={() => setPanel(null)} />
      )}
      {panel === 'adjust' && (
        <AdjustmentForm item={item} onSaved={saved} onCancel={() => setPanel(null)} />
      )}
      {panel === 'history' && <History item={item} />}
    </li>
  );
}

export function InventoryPage() {
  const [lowOnly, setLowOnly] = useState(false);
  const [categoryId, setCategoryId] = useState('');
  const [query, setQuery] = useState('');
  const [categories, setCategories] = useState<Category[]>([]);
  const [inventory, setInventory] = useState<Inventory | null>(null);
  const [summaryVersion, setSummaryVersion] = useState(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    const params = new URLSearchParams();
    if (lowOnly) params.set('lowOnly', 'true');
    if (categoryId) params.set('categoryId', categoryId);
    if (query.trim()) params.set('q', query.trim());
    api
      .get<Inventory>(`/api/inventory${params.size > 0 ? `?${params}` : ''}`)
      .then((data) => active && setInventory(data))
      .catch((e: { code?: string }) => active && setError(formMessage(e.code ?? '')));
    return () => {
      active = false;
    };
  }, [lowOnly, categoryId, query]);

  useEffect(() => {
    api.get<Category[]>('/api/product-categories').then(setCategories, () => undefined);
  }, []);

  // After a change, the item comes back from the API; only the summary needs refreshing.
  useEffect(() => {
    if (summaryVersion === 0) return;
    api.get<Inventory>('/api/inventory').then(
      (data) => setInventory((current) => current && { ...current, summary: data.summary }),
      () => undefined,
    );
  }, [summaryVersion]);

  function replace(updated: InventoryItem) {
    setInventory(
      (current) =>
        current && {
          ...current,
          items: current.items.map((i) => (i.productId === updated.productId ? updated : i)),
        },
    );
    setSummaryVersion((v) => v + 1);
  }

  return (
    <div className="space-y-5">
      <h1 className="text-xl font-bold">Inventario</h1>
      <FormAlert message={error} />
      <section aria-label="Resumen de inventario" className="card p-4 lg:p-6">
        <dl className="grid grid-cols-3 gap-2 text-center">
          <div>
            <dt className="text-sm text-muted">Productos</dt>
            <dd className="text-lg font-semibold lg:text-2xl">
              {inventory?.summary.references ?? 0}
            </dd>
          </div>
          <div>
            <dt className="text-sm text-muted">Stock bajo</dt>
            <dd className="text-lg font-semibold text-negative lg:text-2xl">
              {inventory?.summary.lowStock ?? 0}
            </dd>
          </div>
          <div>
            <dt className="text-sm text-muted">Valor al costo</dt>
            <dd className="text-lg font-semibold lg:text-2xl">
              {formatMoney(inventory?.summary.value ?? '0')}
            </dd>
          </div>
        </dl>
      </section>
      <div className="grid gap-3 sm:grid-cols-2">
        <TextField
          label="Buscar por nombre o código"
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <SelectField
          label="Filtrar por categoría"
          placeholder="Todas"
          options={Object.fromEntries(categories.map((c) => [c.id, c.name]))}
          value={categoryId}
          onChange={(e) => setCategoryId(e.target.value)}
        />
      </div>
      <label className="flex items-center gap-2 text-base">
        <input
          type="checkbox"
          checked={lowOnly}
          onChange={(e) => setLowOnly(e.target.checked)}
          className="h-5 w-5 accent-accent"
        />
        Solo stock bajo
      </label>
      {inventory?.items.length === 0 && (
        <p className="rounded-xl bg-surface p-6 text-center text-muted">
          {lowOnly || categoryId || query.trim()
            ? 'Ningún producto coincide con los filtros.'
            : 'Aún no tienes productos. Créalos en Productos.'}
        </p>
      )}
      <ul className="divide-y divide-line card lg:grid lg:grid-cols-2 lg:divide-y-0 lg:gap-px lg:bg-raised">
        {inventory?.items.map((item) => (
          <InventoryRow key={item.productId} item={item} onChanged={replace} />
        ))}
      </ul>
    </div>
  );
}
