import { useEffect, useState, type FormEvent } from 'react';
import { api, ApiError, compact } from '../api/client';
import type { Category, Product } from '../api/types';
import { ConfirmDelete } from '../components/ConfirmDelete';
import { Button, FormAlert, SelectField, TextField } from '../components/ui';
import { fieldMessage, formMessage, importError } from '../i18n/es';
import { formatMoney, normalizeAmount } from '../lib/money';
import { normalizeQuantity } from '../lib/quantity';
import { matchesProduct } from '../lib/search';

const XLSX = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
/** Lets Excel open the template's accents (UTF-8) correctly. */
const BOM = String.fromCharCode(0xfeff);
const TEMPLATE =
  'Nombre;Código de barras;Costo;Precio;Categoría;Stock;Stock mínimo\n' +
  'Gaseosa 400 ml;7702004003508;2000;3000;Bebidas;24;6\n';

function useProductErrors() {
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [alert, setAlert] = useState<string | null>(null);
  return {
    errors,
    alert,
    clear: () => {
      setErrors({});
      setAlert(null);
    },
    fail: (error: unknown) => {
      const apiError = error instanceof ApiError ? error : new ApiError(0, 'unknown_error');
      setErrors(apiError.fieldErrors());
      setAlert(formMessage(apiError.code));
    },
  };
}

const plain = (amount: string | null | undefined) => amount?.replace(/\.00$/, '') ?? '';
const categoryOptions = (categories: Category[]) =>
  Object.fromEntries(categories.map((c) => [c.id, c.name]));

/** "Camiseta — M" with variant "M" belongs to the product "Camiseta". */
function groupName(product: Product): string {
  return product.variant
    ? product.name.slice(0, product.name.length - ` — ${product.variant}`.length)
    : product.name;
}

function ProductRow({
  product,
  categories,
  onChanged,
}: {
  product: Product;
  categories: Category[];
  onChanged: () => void;
}) {
  const isVariant = Boolean(product.groupId);
  const label = isVariant ? (product.variant ?? product.name) : product.name;
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(label);
  const [price, setPrice] = useState(plain(product.price));
  const [cost, setCost] = useState(plain(product.cost));
  const [categoryId, setCategoryId] = useState(product.categoryId ?? '');
  const [barcode, setBarcode] = useState(product.barcode ?? '');
  const { errors, alert, clear, fail } = useProductErrors();

  async function onSave(event: FormEvent) {
    event.preventDefault();
    try {
      await api.put(`/api/products/${product.id}`, {
        // A variant is renamed through its label; its full name follows the product.
        ...(isVariant ? { variant: name } : { name, categoryId: categoryId || null }),
        price: normalizeAmount(price),
        cost: normalizeAmount(cost) || null,
        barcode: barcode.trim() || null,
      });
      clear();
      setEditing(false);
      onChanged();
    } catch (error) {
      fail(error);
    }
  }

  async function onDelete() {
    try {
      await api.delete(`/api/products/${product.id}`);
      onChanged();
    } catch (error) {
      fail(error);
    }
  }

  if (editing) {
    return (
      <li className="p-4">
        <form onSubmit={onSave} className="space-y-3" noValidate>
          <FormAlert message={alert} />
          <TextField
            label={isVariant ? `Variante ${product.name}` : `Nombre de ${product.name}`}
            value={name}
            onChange={(e) => setName(e.target.value)}
            error={errors.name ?? errors.variant}
          />
          <TextField
            label={`Precio de ${product.name}`}
            inputMode="decimal"
            value={price}
            onChange={(e) => setPrice(e.target.value)}
            error={errors.price}
          />
          <TextField
            label={`Costo de ${product.name} (opcional)`}
            inputMode="decimal"
            value={cost}
            onChange={(e) => setCost(e.target.value)}
            error={errors.cost}
          />
          {!isVariant && (
            <SelectField
              label={`Categoría de ${product.name}`}
              placeholder="Sin categoría"
              options={categoryOptions(categories)}
              value={categoryId}
              onChange={(e) => setCategoryId(e.target.value)}
              error={errors.categoryId}
            />
          )}
          <TextField
            label={`Código de barras de ${product.name} (opcional)`}
            value={barcode}
            onChange={(e) => setBarcode(e.target.value)}
            error={errors.barcode}
          />
          <div className="flex gap-2">
            <Button type="submit" aria-label={`Guardar ${product.name}`}>
              Guardar
            </Button>
            <Button variant="secondary" onClick={() => setEditing(false)}>
              Cancelar
            </Button>
          </div>
        </form>
      </li>
    );
  }

  const details = [isVariant ? null : product.categoryName, product.barcode].filter(Boolean);
  return (
    <li className="space-y-2 p-4">
      <div className="flex items-center justify-between gap-3">
        <span className="min-w-0">
          <span className="block font-medium">{label}</span>
          {details.length > 0 && (
            <span className="block text-sm text-muted">{details.join(' · ')}</span>
          )}
        </span>
        <span className="shrink-0 font-semibold">{formatMoney(product.price)}</span>
      </div>
      <FormAlert message={alert} />
      <div className="flex flex-wrap gap-2">
        <Button
          variant="secondary"
          size="sm"
          aria-label={`Editar ${product.name}`}
          onClick={() => setEditing(true)}
        >
          Editar
        </Button>
        <ConfirmDelete
          size="sm"
          label={`Eliminar ${product.name}`}
          question={`¿Eliminar ${product.name}? Las ventas ya hechas no cambian.`}
          onConfirm={onDelete}
        />
      </div>
    </li>
  );
}

/** A product with variants: renamed, extended and deleted as one. */
function GroupBlock({
  name,
  variants,
  categories,
  onChanged,
}: {
  name: string;
  variants: Product[];
  categories: Category[];
  onChanged: () => void;
}) {
  const groupId = variants[0]!.groupId!;
  const categoryId = variants[0]!.categoryId ?? null;
  const [panel, setPanel] = useState<'rename' | 'variant' | null>(null);
  const [newName, setNewName] = useState(name);
  const [variant, setVariant] = useState('');
  const [price, setPrice] = useState(plain(variants[0]!.price));
  const [stock, setStock] = useState('');
  const { errors, alert, clear, fail } = useProductErrors();

  async function run(action: () => Promise<unknown>) {
    try {
      await action();
      clear();
      setPanel(null);
      onChanged();
    } catch (error) {
      fail(error);
    }
  }

  const onRename = (event: FormEvent) => {
    event.preventDefault();
    void run(() =>
      api.put(`/api/product-groups/${groupId}`, compact({ name: newName, categoryId })),
    );
  };
  const onAddVariant = (event: FormEvent) => {
    event.preventDefault();
    void run(() =>
      api.post(
        `/api/product-groups/${groupId}/variants`,
        compact({
          name: variant,
          price: normalizeAmount(price),
          stock: normalizeQuantity(stock),
        }),
      ),
    );
  };

  return (
    <li role="group" aria-label={name} className="bg-raised">
      <div className="space-y-2 p-4 pb-2">
        <p className="font-semibold">
          {name}{' '}
          <span className="text-sm font-normal text-muted">
            {[variants[0]!.categoryName, `${variants.length} variantes`]
              .filter(Boolean)
              .join(' · ')}
          </span>
        </p>
        <FormAlert message={alert} />
        <div className="flex flex-wrap gap-2">
          <Button
            variant="secondary"
            size="sm"
            aria-label={`Renombrar ${name}`}
            onClick={() => setPanel('rename')}
          >
            Renombrar
          </Button>
          <Button
            variant="secondary"
            size="sm"
            aria-label={`Agregar variante a ${name}`}
            onClick={() => setPanel('variant')}
          >
            Agregar variante
          </Button>
          <ConfirmDelete
            size="sm"
            label={`Eliminar ${name} y sus variantes`}
            question={`¿Eliminar ${name} y todas sus variantes?`}
            onConfirm={() => run(() => api.delete(`/api/product-groups/${groupId}`))}
          />
        </div>
        {panel === 'rename' && (
          <form onSubmit={onRename} className="space-y-3" noValidate>
            <TextField
              label={`Nombre de ${name}`}
              hint="Cambia el nombre de todas sus variantes."
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              error={errors.name}
            />
            <Button type="submit">Guardar nombre</Button>
          </form>
        )}
        {panel === 'variant' && (
          <form onSubmit={onAddVariant} className="space-y-3" noValidate>
            <TextField
              label="Nueva variante"
              placeholder="Talla, color o presentación"
              value={variant}
              onChange={(e) => setVariant(e.target.value)}
              error={errors.name}
            />
            <div className="grid grid-cols-2 gap-3">
              <TextField
                label={`Precio de ${variant.trim() || 'la variante'}`}
                inputMode="decimal"
                value={price}
                onChange={(e) => setPrice(e.target.value)}
                error={errors.price}
              />
              <TextField
                label="Stock inicial"
                inputMode="decimal"
                placeholder="0"
                value={stock}
                onChange={(e) => setStock(e.target.value)}
                error={errors.stock}
              />
            </div>
            <Button type="submit">Crear variante</Button>
          </form>
        )}
      </div>
      <ul className="divide-y divide-line border-t border-line bg-surface pl-4">
        {variants.map((product) => (
          <ProductRow
            key={`${product.id}-${product.name}-${product.price}`}
            product={product}
            categories={categories}
            onChanged={onChanged}
          />
        ))}
      </ul>
    </li>
  );
}

interface VariantDraft {
  key: number;
  name: string;
  price: string;
  stock: string;
}

function NewProductForm({
  categories,
  onCreated,
}: {
  categories: Category[];
  onCreated: () => void;
}) {
  const [kind, setKind] = useState<'SIMPLE' | 'VARIANTS'>('SIMPLE');
  const [name, setName] = useState('');
  const [price, setPrice] = useState('');
  const [cost, setCost] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [barcode, setBarcode] = useState('');
  const [stock, setStock] = useState('');
  const [minStock, setMinStock] = useState('');
  const [variants, setVariants] = useState<VariantDraft[]>([
    { key: 0, name: '', price: '', stock: '' },
  ]);
  const { errors, alert, clear, fail } = useProductErrors();

  function reset() {
    setName('');
    setPrice('');
    setCost('');
    setCategoryId('');
    setBarcode('');
    setStock('');
    setMinStock('');
    setVariants([{ key: 0, name: '', price: '', stock: '' }]);
    clear();
  }

  function updateVariant(index: number, change: Partial<VariantDraft>) {
    setVariants(variants.map((v, i) => (i === index ? { ...v, ...change } : v)));
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    try {
      if (kind === 'SIMPLE') {
        await api.post(
          '/api/products',
          compact({
            name,
            price: normalizeAmount(price),
            cost: normalizeAmount(cost),
            categoryId,
            barcode: barcode.trim(),
            stock: normalizeQuantity(stock),
            minStock: normalizeQuantity(minStock),
          }),
        );
      } else {
        await api.post(
          '/api/product-groups',
          compact({
            name,
            price: normalizeAmount(price),
            cost: normalizeAmount(cost),
            categoryId,
            variants: variants.map((v) =>
              compact({
                name: v.name,
                price: normalizeAmount(v.price),
                stock: normalizeQuantity(v.stock),
              }),
            ),
          }),
        );
      }
      reset();
      onCreated();
    } catch (error) {
      fail(error);
    }
  }

  return (
    <form onSubmit={onSubmit} className="space-y-3 card p-4" noValidate>
      <h2 className="font-semibold">Nuevo producto</h2>
      <fieldset className="flex flex-wrap gap-x-4 gap-y-2">
        <legend className="sr-only">Tipo de producto</legend>
        {(
          [
            ['SIMPLE', 'Producto simple'],
            ['VARIANTS', 'Con variantes'],
          ] as const
        ).map(([value, text]) => (
          <label key={value} className="flex items-center gap-2 text-base">
            <input
              type="radio"
              name="product-kind"
              value={value}
              checked={kind === value}
              onChange={() => setKind(value)}
              className="h-5 w-5 accent-accent"
            />
            {text}
          </label>
        ))}
      </fieldset>
      <TextField
        label="Nombre"
        hint={kind === 'VARIANTS' ? 'El nombre común, por ejemplo "Camiseta".' : undefined}
        value={name}
        onChange={(e) => setName(e.target.value)}
        error={errors.name}
      />
      <TextField
        label="Precio"
        inputMode="decimal"
        hint={kind === 'VARIANTS' ? 'Se usa en las variantes sin precio propio.' : undefined}
        value={price}
        onChange={(e) => setPrice(e.target.value)}
        error={errors.price}
      />
      <TextField
        label="Costo (opcional)"
        inputMode="decimal"
        hint="Lo que te cuesta a ti. Sirve para valorar tu inventario."
        value={cost}
        onChange={(e) => setCost(e.target.value)}
        error={errors.cost}
      />
      <SelectField
        label="Categoría"
        placeholder="Sin categoría"
        options={categoryOptions(categories)}
        value={categoryId}
        onChange={(e) => setCategoryId(e.target.value)}
        error={errors.categoryId}
      />
      {kind === 'SIMPLE' ? (
        <>
          <TextField
            label="Código de barras (opcional)"
            hint="Puedes escanearlo con un lector."
            value={barcode}
            onChange={(e) => setBarcode(e.target.value)}
            error={errors.barcode}
          />
          <div className="grid grid-cols-2 gap-3">
            <TextField
              label="Stock inicial"
              inputMode="decimal"
              placeholder="0"
              value={stock}
              onChange={(e) => setStock(e.target.value)}
              error={errors.stock}
            />
            <TextField
              label="Stock mínimo (opcional)"
              inputMode="decimal"
              value={minStock}
              onChange={(e) => setMinStock(e.target.value)}
              error={errors.minStock}
            />
          </div>
        </>
      ) : (
        <div className="space-y-3">
          {errors.variants && (
            <p className="text-sm text-negative">Agrega al menos una variante.</p>
          )}
          {variants.map((v, index) => (
            <div key={v.key} className="space-y-2 rounded-lg border border-line p-3">
              <TextField
                label={`Variante ${index + 1}`}
                placeholder="Talla, color o presentación"
                value={v.name}
                onChange={(e) => updateVariant(index, { name: e.target.value })}
                error={errors[`variants[${index}].name`]}
              />
              <div className="grid grid-cols-2 gap-3">
                <TextField
                  label={`Precio de la variante ${index + 1} (opcional)`}
                  inputMode="decimal"
                  value={v.price}
                  onChange={(e) => updateVariant(index, { price: e.target.value })}
                  error={errors[`variants[${index}].price`]}
                />
                <TextField
                  label={`Stock de la variante ${index + 1}`}
                  inputMode="decimal"
                  placeholder="0"
                  value={v.stock}
                  onChange={(e) => updateVariant(index, { stock: e.target.value })}
                  error={errors[`variants[${index}].stock`]}
                />
              </div>
              {variants.length > 1 && (
                <button
                  type="button"
                  className="text-sm text-negative underline"
                  onClick={() => setVariants(variants.filter((_, i) => i !== index))}
                >
                  Quitar variante {index + 1}
                </button>
              )}
            </div>
          ))}
          <Button
            variant="secondary"
            onClick={() =>
              setVariants([
                ...variants,
                {
                  key: Math.max(...variants.map((v) => v.key)) + 1,
                  name: '',
                  price: '',
                  stock: '',
                },
              ])
            }
          >
            Agregar variante
          </Button>
        </div>
      )}
      <FormAlert message={alert} />
      <Button type="submit" className="w-full">
        Agregar producto
      </Button>
    </form>
  );
}

function CategoryItem({ category, onChanged }: { category: Category; onChanged: () => void }) {
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(category.name);
  const { errors, alert, clear, fail } = useProductErrors();

  async function run(action: () => Promise<unknown>) {
    try {
      await action();
      clear();
      setEditing(false);
      onChanged();
    } catch (error) {
      fail(error);
    }
  }

  return (
    <li className="space-y-2 py-2">
      {editing ? (
        <form
          className="flex items-end gap-2"
          noValidate
          onSubmit={(e) => {
            e.preventDefault();
            void run(() => api.put(`/api/product-categories/${category.id}`, { name }));
          }}
        >
          <div className="flex-1">
            <TextField
              label={`Nombre de ${category.name}`}
              value={name}
              onChange={(e) => setName(e.target.value)}
              error={errors.name}
            />
          </div>
          <Button type="submit" aria-label={`Guardar ${category.name}`}>
            Guardar
          </Button>
        </form>
      ) : (
        <div className="flex flex-wrap items-center justify-between gap-2">
          <span className="font-medium">{category.name}</span>
          <span className="flex flex-wrap gap-2">
            <Button
              variant="secondary"
              size="sm"
              aria-label={`Renombrar ${category.name}`}
              onClick={() => setEditing(true)}
            >
              Renombrar
            </Button>
            <ConfirmDelete
              size="sm"
              label={`Eliminar ${category.name}`}
              question={`¿Eliminar ${category.name}? Sus productos quedan sin categoría.`}
              onConfirm={() => run(() => api.delete(`/api/product-categories/${category.id}`))}
            />
          </span>
        </div>
      )}
      <FormAlert message={alert} />
    </li>
  );
}

function CategoriesPanel({
  categories,
  onChanged,
}: {
  categories: Category[];
  onChanged: () => void;
}) {
  const [name, setName] = useState('');
  const { errors, alert, clear, fail } = useProductErrors();

  async function onCreate(event: FormEvent) {
    event.preventDefault();
    try {
      await api.post('/api/product-categories', { name });
      setName('');
      clear();
      onChanged();
    } catch (error) {
      fail(error);
    }
  }

  return (
    <section aria-label="Categorías" className="space-y-3 card p-4">
      <h2 className="font-semibold">Categorías</h2>
      <form onSubmit={onCreate} className="flex items-end gap-2" noValidate>
        <div className="flex-1">
          <TextField
            label="Nueva categoría"
            placeholder="Bebidas, aseo, ropa…"
            value={name}
            onChange={(e) => setName(e.target.value)}
            error={errors.name}
          />
        </div>
        <Button type="submit" aria-label="Crear categoría">
          Crear
        </Button>
      </form>
      <FormAlert message={alert} />
      {categories.length === 0 ? (
        <p className="text-sm text-muted">Aún no tienes categorías.</p>
      ) : (
        <ul className="divide-y divide-rule">
          {categories.map((category) => (
            <CategoryItem
              key={`${category.id}-${category.name}`}
              category={category}
              onChanged={onChanged}
            />
          ))}
        </ul>
      )}
    </section>
  );
}

function ImportPanel({ onImported }: { onImported: () => void }) {
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [errors, setErrors] = useState<string[]>([]);
  const [done, setDone] = useState<string | null>(null);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setDone(null);
    if (!file) {
      setErrors([fieldMessage('required')]);
      return;
    }
    setBusy(true);
    try {
      const type = file.name.toLowerCase().endsWith('.xlsx') ? XLSX : 'text/csv';
      const result = await api.upload<{ created: number }>('/api/products/import', file, type);
      setErrors([]);
      setDone(
        result.created === 1 ? '1 producto cargado.' : `${result.created} productos cargados.`,
      );
      onImported();
    } catch (error) {
      const apiError = error instanceof ApiError ? error : new ApiError(0, 'unknown_error');
      setErrors(
        apiError.errors.length > 0
          ? apiError.errors.map((e) => importError(e.field, e.code))
          : [formMessage(apiError.code)],
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <section aria-label="Cargar desde Excel" className="space-y-3 card p-4">
      <h2 className="font-semibold">Cargar desde Excel</h2>
      <p className="text-sm text-muted">
        Sube tu lista con las columnas Nombre, Código de barras, Costo, Precio y Categoría (Stock y
        Stock mínimo son opcionales). Si una fila tiene un error, no se carga ninguna.
      </p>
      <a
        href={`data:text/csv;charset=utf-8,${encodeURIComponent(BOM + TEMPLATE)}`}
        download="plantilla-productos.csv"
        className="inline-block text-sm font-medium text-accent underline underline-offset-2"
      >
        Descargar plantilla
      </a>
      <form onSubmit={onSubmit} className="space-y-3" noValidate>
        <label className="block text-sm font-medium text-ink">
          Archivo .xlsx o .csv
          <input
            type="file"
            accept=".xlsx,.csv"
            className="mt-1 block w-full text-base"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          />
        </label>
        <Button type="submit" disabled={busy}>
          Cargar productos
        </Button>
      </form>
      {errors.length > 0 && (
        <ul
          aria-label="Errores del archivo"
          className="list-disc space-y-1 pl-5 text-sm text-negative"
        >
          {errors.map((message) => (
            <li key={message}>{message}</li>
          ))}
        </ul>
      )}
      <p role="status" className="text-sm text-positive">
        {done ?? ''}
      </p>
    </section>
  );
}

type Entry =
  { kind: 'product'; product: Product } | { kind: 'group'; name: string; variants: Product[] };

/** Products in list order, with the variants of a product gathered where its first one appears. */
function entries(products: Product[]): Entry[] {
  const result: Entry[] = [];
  const groups = new Map<string, Product[]>();
  for (const product of products) {
    if (!product.groupId) {
      result.push({ kind: 'product', product });
      continue;
    }
    const variants = groups.get(product.groupId);
    if (variants) {
      variants.push(product);
    } else {
      const created = [product];
      groups.set(product.groupId, created);
      result.push({ kind: 'group', name: groupName(product), variants: created });
    }
  }
  return result;
}

export function ProductsPage() {
  const [products, setProducts] = useState<Product[] | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);
  const [query, setQuery] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('');
  const [version, setVersion] = useState(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    api.get<Product[]>('/api/products').then(
      (list) => active && setProducts(list),
      (e: unknown) => active && setError(formMessage(e instanceof ApiError ? e.code : '')),
    );
    // Without categories the page still works: filters and selects just stay empty.
    api.get<Category[]>('/api/product-categories').then(
      (list) => active && setCategories(list),
      () => undefined,
    );
    return () => {
      active = false;
    };
  }, [version]);

  const reload = () => setVersion((v) => v + 1);
  const shown = (products ?? []).filter(
    (p) => (!categoryFilter || p.categoryId === categoryFilter) && matchesProduct(p, query),
  );

  return (
    <div className="space-y-5">
      <h1 className="text-xl font-bold">Productos</h1>
      <FormAlert message={error} />
      <div className="space-y-5 lg:grid lg:grid-cols-[22rem_1fr] lg:items-start lg:gap-6 lg:space-y-0">
        <div className="space-y-5">
          <NewProductForm categories={categories} onCreated={reload} />
          <CategoriesPanel categories={categories} onChanged={reload} />
          <ImportPanel onImported={reload} />
        </div>
        <div className="space-y-3 lg:order-none">
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
              options={categoryOptions(categories)}
              value={categoryFilter}
              onChange={(e) => setCategoryFilter(e.target.value)}
            />
          </div>
          {products?.length === 0 && (
            <p className="text-center text-muted">
              Aún no tienes productos. Agrégalos para vender más rápido.
            </p>
          )}
          {products !== null && products.length > 0 && shown.length === 0 && (
            <p className="text-center text-muted">Ningún producto coincide con la búsqueda.</p>
          )}
          <ul aria-label="Productos" className="divide-y divide-line card">
            {entries(shown).map((entry) =>
              entry.kind === 'product' ? (
                <ProductRow
                  key={`${entry.product.id}-${entry.product.name}-${entry.product.price}`}
                  product={entry.product}
                  categories={categories}
                  onChanged={reload}
                />
              ) : (
                <GroupBlock
                  key={`${entry.variants[0]!.groupId}-${entry.name}`}
                  name={entry.name}
                  variants={entry.variants}
                  categories={categories}
                  onChanged={reload}
                />
              ),
            )}
          </ul>
        </div>
      </div>
    </div>
  );
}
