import {
  Boxes,
  CircleDot,
  House,
  Menu,
  Package,
  Settings,
  ShoppingCart,
  Truck,
  Users,
  Wallet,
  type LucideIcon,
} from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link, Navigate, NavLink, Outlet, useLocation } from 'react-router';
import { api } from '../api/client';
import type { CashSession } from '../api/types';
import { useSession } from '../auth/session-context';
import { BRAND } from '../i18n/es';
import { Loading } from './ui';

interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  /** Shown in the phone's bottom bar; the rest go under "Más". */
  tab: boolean;
}

const groups: [string, NavItem[]][] = [
  [
    'Día a día',
    [
      { to: '/', label: 'Inicio', icon: House, tab: true },
      { to: '/vender', label: 'Vender', icon: ShoppingCart, tab: true },
      { to: '/caja', label: 'Caja', icon: Wallet, tab: false },
    ],
  ],
  [
    'Catálogo',
    [
      { to: '/productos', label: 'Productos', icon: Package, tab: true },
      { to: '/inventario', label: 'Inventario', icon: Boxes, tab: false },
    ],
  ],
  [
    'Personas',
    [
      { to: '/clientes', label: 'Clientes', icon: Users, tab: true },
      { to: '/proveedores', label: 'Proveedores', icon: Truck, tab: false },
    ],
  ],
];
const business: NavItem = { to: '/negocio', label: 'Negocio', icon: Settings, tab: false };
const allItems = [...groups.flatMap(([, items]) => items), business];

/** The product's mark: a shop awning, drawn for it. */
export function BrandMark({ size = 28 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 28 28" aria-hidden="true" className="flex-none">
      <rect width="28" height="28" rx="7" fill="var(--color-accent)" />
      <path
        d="M6 9h16v3a2.67 2.67 0 0 1-5.33 0 2.67 2.67 0 0 1-5.34 0A2.67 2.67 0 0 1 6 12z"
        fill="#fff"
      />
      <path d="M8 15.5V21h12v-5.5" fill="none" stroke="#fff" strokeWidth="1.8" />
    </svg>
  );
}

function SideLink({ item }: { item: NavItem }) {
  const Icon = item.icon;
  return (
    <NavLink
      to={item.to}
      end={item.to === '/'}
      className={({ isActive }) =>
        `flex h-[34px] items-center gap-2.5 rounded-control px-2.5 text-sm ${
          isActive ? 'bg-active font-medium text-ink' : 'text-[#3a423d] hover:bg-raised'
        }`
      }
    >
      {({ isActive }) => (
        <>
          <Icon size={16} strokeWidth={1.6} className={isActive ? 'text-ink' : 'text-muted'} />
          {item.label}
        </>
      )}
    </NavLink>
  );
}

/** Whether the till is open, from anywhere in the app: the frame's one live fact. */
function RegisterStatus({ timeZone }: { timeZone: string }) {
  const { pathname } = useLocation();
  const [open, setOpen] = useState<CashSession | null | undefined>(undefined);
  useEffect(() => {
    let active = true;
    api.get<{ open: CashSession | null }>('/api/cash-register').then(
      (data) => active && setOpen(data.open),
      () => active && setOpen(undefined),
    );
    return () => {
      active = false;
    };
  }, [pathname]);
  if (open === undefined) return null;
  const since = open
    ? new Intl.DateTimeFormat('es-CO', { timeZone, hour: 'numeric', minute: '2-digit' }).format(
        new Date(open.openedAt),
      )
    : null;
  return (
    <Link to="/caja" className="flex items-center gap-2 text-sm text-muted hover:text-ink">
      <CircleDot size={14} className={open ? 'text-positive' : 'text-muted'} />
      {open ? `Caja abierta desde las ${since}` : 'Caja cerrada'}
    </Link>
  );
}

export function ProtectedLayout() {
  const { state } = useSession();
  const [moreOpen, setMoreOpen] = useState(false);
  if (state.status === 'loading') return <Loading />;
  if (state.status === 'anonymous') return <Navigate to="/entrar" replace />;

  return (
    <div className="min-h-dvh bg-ground lg:grid lg:grid-cols-[228px_1fr] print:block print:bg-surface">
      {/* Desktop: a labelled sidebar on the frame. */}
      <aside className="hidden lg:sticky lg:top-0 lg:flex lg:h-dvh lg:flex-col lg:px-3 lg:py-3.5 print:hidden">
        <div className="flex items-center gap-2.5 px-2 pb-4">
          <BrandMark />
          <span className="flex min-w-0 flex-col leading-tight">
            <b className="text-[15px] font-semibold">{BRAND}</b>
            <small className="truncate text-xs text-muted">{state.me.business.name}</small>
          </span>
        </div>
        <nav aria-label="Principal" className="flex flex-1 flex-col gap-0.5">
          {groups.map(([label, items]) => (
            <div key={label} className="flex flex-col gap-0.5">
              <p className="px-2.5 pt-4 pb-1.5 text-xs text-muted">{label}</p>
              {items.map((item) => (
                <SideLink key={item.to} item={item} />
              ))}
            </div>
          ))}
          <div className="mt-auto">
            <SideLink item={business} />
          </div>
        </nav>
      </aside>

      <div className="flex min-w-0 flex-col lg:min-h-dvh">
        {/* Desktop top bar on the frame; phones get a compact bar with the mark. */}
        <header className="hidden h-12 items-center justify-end px-5 lg:flex print:hidden">
          <RegisterStatus timeZone={state.me.business.timeZone} />
        </header>
        <header className="sticky top-0 z-10 flex h-14 items-center gap-2.5 border-b border-line bg-ground px-4 lg:hidden print:hidden">
          <BrandMark />
          <b className="text-[15px] font-semibold">{BRAND}</b>
          <span className="ml-auto truncate text-xs text-muted">{state.me.business.name}</span>
        </header>
        {/* The work sits on one white sheet, inset from the frame. */}
        <main className="flex-1 bg-surface px-4 pt-4 pb-28 lg:mr-2 lg:mb-2 lg:rounded-sheet lg:border lg:border-line lg:px-7 lg:pt-6 lg:pb-10 print:m-0 print:border-0 print:p-0">
          <div className="mx-auto max-w-6xl">
            <Outlet />
          </div>
        </main>
      </div>

      {/* Phones and tablets: five tabs, the rest under "Más". */}
      <nav
        aria-label="Pestañas"
        className="fixed inset-x-0 bottom-0 z-20 grid h-[60px] grid-cols-5 border-t border-line bg-surface lg:hidden print:hidden"
      >
        {allItems
          .filter((item) => item.tab)
          .map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) =>
                `flex flex-col items-center justify-center gap-0.5 text-[11px] ${
                  isActive ? 'font-semibold text-ink' : 'text-muted'
                }`
              }
            >
              <item.icon size={20} strokeWidth={1.6} aria-hidden="true" />
              {item.label}
            </NavLink>
          ))}
        <button
          type="button"
          aria-expanded={moreOpen}
          onClick={() => setMoreOpen((open) => !open)}
          className={`flex flex-col items-center justify-center gap-0.5 text-[11px] ${moreOpen ? 'font-semibold text-ink' : 'text-muted'}`}
        >
          <Menu size={20} strokeWidth={1.6} aria-hidden="true" />
          Más
        </button>
      </nav>
      {moreOpen && (
        <div
          role="dialog"
          aria-label="Más opciones"
          className="fixed inset-x-0 bottom-[60px] z-20 rounded-t-2xl border-t border-line bg-surface p-2 shadow-[0_-8px_24px_rgba(0,0,0,0.08)] lg:hidden"
        >
          <ul className="grid grid-cols-2 gap-1">
            {allItems
              .filter((item) => !item.tab)
              .map((item) => (
                <li key={item.to}>
                  <NavLink
                    to={item.to}
                    onClick={() => setMoreOpen(false)}
                    className={({ isActive }) =>
                      `flex items-center gap-2.5 rounded-control px-4 py-3 font-medium ${
                        isActive ? 'bg-active text-ink' : 'text-ink'
                      }`
                    }
                  >
                    <item.icon
                      size={18}
                      strokeWidth={1.6}
                      className="text-muted"
                      aria-hidden="true"
                    />
                    {item.label}
                  </NavLink>
                </li>
              ))}
          </ul>
        </div>
      )}
    </div>
  );
}

export function PublicLayout() {
  const { state } = useSession();
  if (state.status === 'loading') return <Loading />;
  if (state.status === 'authenticated') return <Navigate to="/" replace />;
  return (
    <main className="mx-auto max-w-sm px-4 py-10">
      <div className="mb-6 flex items-center justify-center gap-2.5">
        <BrandMark size={36} />
        <p className="text-2xl font-semibold">{BRAND}</p>
      </div>
      <div className="card p-6">
        <Outlet />
      </div>
    </main>
  );
}
