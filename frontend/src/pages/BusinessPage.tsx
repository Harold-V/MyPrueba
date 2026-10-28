import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router';
import { api, ApiError } from '../api/client';
import type { Business } from '../api/types';
import { useMe, useSession } from '../auth/session-context';
import { Button, FormAlert, SelectField, TextField } from '../components/ui';
import { businessCategories, formMessage } from '../i18n/es';

export function BusinessPage() {
  const me = useMe();
  const { updateMe, signOut } = useSession();
  const navigate = useNavigate();
  const [name, setName] = useState(me.business.name);
  const [category, setCategory] = useState<string>(me.business.category ?? '');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [alert, setAlert] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setSaved(false);
    setAlert(null);
    try {
      const business = await api.put<Business>('/api/business', {
        name,
        category: category || null,
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
          options={businessCategories}
          value={category}
          onChange={(e) => setCategory(e.target.value)}
          error={errors.category}
        />
        <p className="text-sm text-slate-600">Cuenta: {me.user.email}</p>
        <Button type="submit">Guardar</Button>
        <p role="status" className="text-sm text-emerald-800">
          {saved ? 'Cambios guardados.' : ''}
        </p>
      </form>
      <Button variant="secondary" className="w-full" onClick={() => void onSignOut()}>
        Cerrar sesión
      </Button>
    </div>
  );
}
