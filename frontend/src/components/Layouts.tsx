import { useState } from 'react';
import { Navigate, NavLink, Outlet } from 'react-router';
import { useSession } from '../auth/session-context';
import { BRAND } from '../i18n/es';
import { Loading } from './ui';

/** Every section. On phones only `tab` items fit in the bottom bar; the rest go under "Más". */
const navItems: { to: string; label: string; tab: boolean }[] = [
  { to: '/', label: 'Inicio', tab: true },
  { to: '/vender', label: 'Vender', tab: true },
  { to: '/productos', label: 'Productos', tab: true },
  { to: '/inventario', label: 'Inventario', tab: false },
  { to: '/clientes', label: 'Clientes', tab: true },
  { to: '/proveedores', label: 'Proveedores', tab: false },
  { to: '/caja', label: 'Caja', tab: false },
  { to: '/negocio', label: 'Negocio', tab: false },
];

export function ProtectedLayout() {
  const { state } = useSession();
  const [moreOpen, setMoreOpen] = useState(false);
  if (state.status === 'loading') return <Loading />;
  if (state.status === 'anonymous') return <Navigate to="/entrar" replace />;

  // Phones: bottom tab bar with "Más" for the other sections. Desktop (lg, ≥ 1024 px): every
  // section in the sidebar.
  const link = ({ isActive }: { isActive: boolean }) =>
    `flex-1 px-0.5 py-3 text-center text-xs font-medium sm:text-sm lg:flex-none lg:rounded-lg lg:px-4 lg:py-2.5 lg:text-left lg:text-base ${
      isActive
        ? 'text-emerald-800 lg:bg-emerald-900 lg:text-white'
        : 'text-slate-600 lg:text-emerald-50 lg:hover:bg-emerald-800'
    }`;
  return (
    <div className="min-h-dvh lg:grid lg:grid-cols-[16rem_1fr] print:block">
      <aside className="bg-emerald-700 text-white lg:sticky lg:top-0 lg:flex lg:h-dvh lg:flex-col lg:gap-6 lg:p-4 print:hidden">
        <header className="px-4 py-3 lg:px-2 lg:py-2">
          <p className="text-xs uppercase tracking-wide opacity-90">{BRAND}</p>
          <p className="truncate text-lg font-semibold">{state.me.business.name}</p>
        </header>
        <nav
          aria-label="Principal"
          className="fixed inset-x-0 bottom-0 z-20 flex border-t border-slate-200 bg-white lg:static lg:flex-col lg:gap-1 lg:border-0 lg:bg-transparent"
        >
          {navItems.map(({ to, label, tab }) => (
            <NavLink
              key={to}
              to={to}
              end={to === '/'}
              className={(s) => `${link(s)} ${tab ? '' : 'hidden lg:block'}`}
            >
              {label}
            </NavLink>
          ))}
          <button
            type="button"
            aria-expanded={moreOpen}
            onClick={() => setMoreOpen((open) => !open)}
            className={`${link({ isActive: moreOpen })} lg:hidden`}
          >
            Más
          </button>
        </nav>
      </aside>
      {moreOpen && (
        <div
          role="dialog"
          aria-label="Más opciones"
          className="fixed inset-x-0 bottom-12 z-20 rounded-t-2xl border-t border-slate-200 bg-white p-2 shadow-lg lg:hidden"
        >
          <ul className="grid grid-cols-2 gap-1">
            {navItems
              .filter((item) => !item.tab)
              .map(({ to, label }) => (
                <li key={to}>
                  <NavLink
                    to={to}
                    onClick={() => setMoreOpen(false)}
                    className={({ isActive }) =>
                      `block rounded-lg px-4 py-3 font-medium ${
                        isActive ? 'bg-emerald-50 text-emerald-900' : 'text-slate-700'
                      }`
                    }
                  >
                    {label}
                  </NavLink>
                </li>
              ))}
          </ul>
        </div>
      )}
      <main className="px-4 pt-4 pb-24 lg:px-10 lg:py-8 print:p-0">
        <div className="mx-auto max-w-5xl">
          <Outlet />
        </div>
      </main>
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
