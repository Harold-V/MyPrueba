import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { api, ApiError, compact } from '../api/client';
import type { Me } from '../api/types';
import { useSession } from '../auth/session-context';
import { Button, FormAlert, SelectField, TextField } from '../components/ui';
import { businessCategoryGroups, formMessage } from '../i18n/es';

function useAuthSubmit(path: string) {
  const { signIn } = useSession();
  const navigate = useNavigate();
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [alert, setAlert] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(body: Record<string, unknown>) {
    setBusy(true);
    setAlert(null);
    try {
      signIn(await api.post<Me>(path, body));
      navigate('/', { replace: true });
    } catch (error) {
      const apiError = error instanceof ApiError ? error : new ApiError(0, 'unknown_error');
      setErrors(apiError.fieldErrors());
      setAlert(apiError.errors.length > 0 ? null : formMessage(apiError.code));
    } finally {
      setBusy(false);
    }
  }
  return { errors, alert, busy, submit };
}

export function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const { alert, busy, submit } = useAuthSubmit('/api/auth/login');

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    void submit({ email, password });
  }

  return (
    <form onSubmit={onSubmit} className="space-y-4" noValidate>
      <h1 className="text-xl font-bold">Entrar</h1>
      <FormAlert message={alert} />
      <TextField
        label="Email"
        type="email"
        autoComplete="email"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
      />
      <TextField
        label="Contraseña"
        type="password"
        autoComplete="current-password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
      />
      <Button type="submit" className="w-full" disabled={busy}>
        Entrar
      </Button>
      <p className="text-center text-sm">
        ¿No tienes cuenta?{' '}
        <Link to="/registro" className="font-medium text-accent underline underline-offset-2">
          Crea una gratis
        </Link>
      </p>
    </form>
  );
}

export function RegisterPage() {
  const [form, setForm] = useState({
    email: '',
    password: '',
    businessName: '',
    businessCategory: '',
  });
  const { errors, alert, busy, submit } = useAuthSubmit('/api/auth/register');
  const set = (field: keyof typeof form) => (e: { target: { value: string } }) =>
    setForm({ ...form, [field]: e.target.value });

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    void submit(compact(form));
  }

  return (
    <form onSubmit={onSubmit} className="space-y-4" noValidate>
      <h1 className="text-xl font-bold">Crea tu cuenta</h1>
      <FormAlert message={alert} />
      <TextField
        label="Email"
        type="email"
        autoComplete="email"
        value={form.email}
        onChange={set('email')}
        error={errors.email}
      />
      <TextField
        label="Contraseña"
        type="password"
        autoComplete="new-password"
        hint="Mínimo 8 caracteres."
        value={form.password}
        onChange={set('password')}
        error={errors.password}
      />
      <TextField
        label="Nombre del negocio"
        autoComplete="organization"
        value={form.businessName}
        onChange={set('businessName')}
        error={errors.businessName}
      />
      <SelectField
        label="Tipo de negocio (opcional)"
        placeholder="Sin especificar"
        groups={businessCategoryGroups}
        value={form.businessCategory}
        onChange={set('businessCategory')}
        error={errors.businessCategory}
      />
      <Button type="submit" className="w-full" disabled={busy}>
        Crear cuenta
      </Button>
      <p className="text-center text-sm">
        ¿Ya tienes cuenta?{' '}
        <Link to="/entrar" className="font-medium text-accent underline underline-offset-2">
          Entra
        </Link>
      </p>
    </form>
  );
}
