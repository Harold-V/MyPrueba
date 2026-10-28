import { useEffect, useState, type FormEvent } from 'react';
import { api, ApiError } from '../api/client';
import type { Product } from '../api/types';
import { ConfirmDelete } from '../components/ConfirmDelete';
import { Button, FormAlert, TextField } from '../components/ui';
import { formMessage } from '../i18n/es';
import { formatMoney, normalizeAmount } from '../lib/money';

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

function ProductRow({ product, onChanged }: { product: Product; onChanged: () => void }) {
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(product.name);
  const [price, setPrice] = useState(product.price.replace(/\.00$/, ''));
  const { errors, alert, clear, fail } = useProductErrors();

  async function onSave(event: FormEvent) {
    event.preventDefault();
    try {
      await api.put(`/api/products/${product.id}`, { name, price: normalizeAmount(price) });
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
            label={`Nombre de ${product.name}`}
            value={name}
            onChange={(e) => setName(e.target.value)}
            error={errors.name}
          />
          <TextField
            label={`Precio de ${product.name}`}
            inputMode="decimal"
            value={price}
            onChange={(e) => setPrice(e.target.value)}
            error={errors.price}
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

  return (
    <li className="space-y-2 p-4">
      <div className="flex items-center justify-between">
        <span className="font-medium">{product.name}</span>
        <span className="font-semibold">{formatMoney(product.price)}</span>
      </div>
      <FormAlert message={alert} />
      <div className="flex flex-wrap gap-2">
        <Button
          variant="secondary"
          aria-label={`Editar ${product.name}`}
          onClick={() => setEditing(true)}
        >
          Editar
        </Button>
        <ConfirmDelete
          label={`Eliminar ${product.name}`}
          question={`¿Eliminar ${product.name}? Las ventas ya hechas no cambian.`}
          onConfirm={onDelete}
        />
      </div>
    </li>
  );
}

export function ProductsPage() {
  const [products, setProducts] = useState<Product[] | null>(null);
  const [name, setName] = useState('');
  const [price, setPrice] = useState('');
  const [version, setVersion] = useState(0);
  const { errors, alert, clear, fail } = useProductErrors();

  useEffect(() => {
    let active = true;
    api.get<Product[]>('/api/products').then((list) => active && setProducts(list), fail);
    return () => {
      active = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [version]);

  const reload = () => setVersion((v) => v + 1);

  async function onCreate(event: FormEvent) {
    event.preventDefault();
    try {
      await api.post('/api/products', { name, price: normalizeAmount(price) });
      setName('');
      setPrice('');
      clear();
      reload();
    } catch (error) {
      fail(error);
    }
  }

  return (
    <div className="space-y-5">
      <h1 className="text-xl font-bold">Productos</h1>
      <form onSubmit={onCreate} className="space-y-3 rounded-xl bg-white p-4 shadow-sm" noValidate>
        <FormAlert message={alert} />
        <TextField
          label="Nombre"
          value={name}
          onChange={(e) => setName(e.target.value)}
          error={errors.name}
        />
        <TextField
          label="Precio"
          inputMode="decimal"
          value={price}
          onChange={(e) => setPrice(e.target.value)}
          error={errors.price}
        />
        <Button type="submit" className="w-full">
          Agregar producto
        </Button>
      </form>
      {products?.length === 0 && (
        <p className="text-center text-slate-600">
          Aún no tienes productos. Agrégalos para vender más rápido.
        </p>
      )}
      <ul className="divide-y divide-slate-200 rounded-xl bg-white shadow-sm">
        {products?.map((product) => (
          <ProductRow
            key={`${product.id}-${product.name}-${product.price}`}
            product={product}
            onChanged={reload}
          />
        ))}
      </ul>
    </div>
  );
}
