import { Navigate, NavLink, Outlet } from 'react-router';
import { useSession } from '../auth/session-context';
import { BRAND } from '../i18n/es';
import { Loading } from './ui';

export function ProtectedLayout() {
  const { state } = useSession();
  if (state.status === 'loading') return <Loading />;
  if (state.status === 'anonymous') return <Navigate to="/entrar" replace />;

  const link = ({ isActive }: { isActive: boolean }) =>
    `flex-1 py-3 text-center text-sm font-medium ${isActive ? 'text-emerald-800' : 'text-slate-600'}`;
  return (
    <div className="mx-auto flex min-h-dvh max-w-xl flex-col">
      <header className="bg-emerald-700 px-4 py-3 text-white">
        <p className="text-xs uppercase tracking-wide opacity-90">{BRAND}</p>
        <p className="text-lg font-semibold">{state.me.business.name}</p>
      </header>
      <main className="flex-1 px-4 py-4">
        <Outlet />
      </main>
      <nav
        aria-label="Principal"
        className="sticky bottom-0 flex border-t border-slate-200 bg-white"
      >
        <NavLink to="/" end className={link}>
          Inicio
        </NavLink>
        <NavLink to="/productos" className={link}>
          Productos
        </NavLink>
        <NavLink to="/negocio" className={link}>
          Negocio
        </NavLink>
      </nav>
    </div>
  );
}

export function PublicLayout() {
  const { state } = useSession();
  if (state.status === 'loading') return <Loading />;
  if (state.status === 'authenticated') return <Navigate to="/" replace />;
  return (
    <main className="mx-auto max-w-sm px-4 py-10">
      <p className="mb-6 text-center text-2xl font-bold text-emerald-800">{BRAND}</p>
      <Outlet />
    </main>
  );
}
