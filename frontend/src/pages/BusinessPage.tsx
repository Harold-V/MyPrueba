import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router';
import { api, ApiError, compact } from '../api/client';
import type { Business } from '../api/types';
import { useMe, useSession } from '../auth/session-context';
import { Button, FormAlert, SelectField, TextField } from '../components/ui';
import { businessCategoryGroups, formMessage } from '../i18n/es';

export function BusinessPage() {
  const me = useMe();
  const { updateMe, signOut } = useSession();
  const navigate = useNavigate();
  const [name, setName] = useState(me.business.name);
  const [category, setCategory] = useState<string>(me.business.category ?? '');
  const [documentId, setDocumentId] = useState(me.business.document ?? '');
  const [phone, setPhone] = useState(me.business.phone ?? '');
  const [address, setAddress] = useState(me.business.address ?? '');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [alert, setAlert] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [allowNegativeStock, setAllowNegativeStock] = useState<boolean | null>(null);

  useEffect(() => {
    api.get<{ allowNegativeStock: boolean }>('/api/business/settings').then(
      (s) => setAllowNegativeStock(s.allowNegativeStock),
      () => undefined,
    );
  }, []);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setSaved(false);
    setAlert(null);
    try {
      const business = await api.put<Business>('/api/business', {
        name,
        category: category || null,
        // Shown on receipts; an emptied field is cleared.
        ...compact({ document: documentId.trim(), phone: phone.trim(), address: address.trim() }),
      });
      updateMe({ ...me, business });
      setErrors({});
      setSaved(true);
    } catch (error) {
      const apiError = error instanceof ApiError ? error : new ApiError(0, 'unknown_error');
      setErrors(apiError.fieldErrors());
      setAlert(formMessage(apiError.code));
    }
  }

  async function onToggleNegativeStock(allow: boolean) {
    setAllowNegativeStock(allow);
    try {
      await api.put('/api/business/settings', { allowNegativeStock: allow });
    } catch (error) {
      setAllowNegativeStock(!allow);
      setAlert(formMessage(error instanceof ApiError ? error.code : 'unknown_error'));
    }
  }

  async function onSignOut() {
    await signOut();
    navigate('/entrar', { replace: true });
  }

  return (
    <div className="max-w-xl space-y-8">
      <form onSubmit={onSubmit} className="space-y-4" noValidate>
        <h1 className="text-xl font-bold">Tu negocio</h1>
        <FormAlert message={alert} />
        <TextField
          label="Nombre del negocio"
          value={name}
          onChange={(e) => setName(e.target.value)}
          error={errors.name}
        />
        <SelectField
          label="Tipo de negocio (opcional)"
          placeholder="Sin especificar"
          groups={businessCategoryGroups}
          value={category}
          onChange={(e) => setCategory(e.target.value)}
          error={errors.category}
        />
        <fieldset className="space-y-4">
          <legend className="font-semibold">Datos para tus comprobantes</legend>
          <TextField
            label="NIT o documento (opcional)"
            value={documentId}
            onChange={(e) => setDocumentId(e.target.value)}
            error={errors.document}
          />
          <TextField
            label="Teléfono (opcional)"
            type="tel"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            error={errors.phone}
          />
          <TextField
            label="Dirección (opcional)"
            autoComplete="street-address"
            value={address}
            onChange={(e) => setAddress(e.target.value)}
            error={errors.address}
          />
        </fieldset>
        <p className="text-sm text-slate-600">Cuenta: {me.user.email}</p>
        <Button type="submit">Guardar</Button>
        <p role="status" className="text-sm text-emerald-800">
          {saved ? 'Cambios guardados.' : ''}
        </p>
      </form>
      <section className="space-y-2 rounded-xl bg-white p-4 shadow-sm">
        <h2 className="font-semibold">Inventario</h2>
        <label className="flex items-start gap-3 text-base">
          <input
            type="checkbox"
            className="mt-1 h-5 w-5 accent-emerald-700"
            checked={allowNegativeStock ?? true}
            disabled={allowNegativeStock === null}
            onChange={(e) => void onToggleNegativeStock(e.target.checked)}
          />
          <span>
            Permitir vender sin stock
            <span className="block text-sm text-slate-600">
              Si lo desactivas, no podrás registrar ventas de productos sin unidades disponibles.
            </span>
          </span>
        </label>
      </section>
      <Button variant="secondary" className="w-full" onClick={() => void onSignOut()}>
        Cerrar sesión
      </Button>
    </div>
  );
}
