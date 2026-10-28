import { Outlet, type RouteObject } from 'react-router';
import { SessionProvider } from './auth/session';
import { ProtectedLayout, PublicLayout } from './components/Layouts';
import { LoginPage, RegisterPage } from './pages/AuthPages';
import { BusinessPage } from './pages/BusinessPage';
import { ExpenseFormPage } from './pages/ExpenseFormPage';
import { HomePage } from './pages/HomePage';
import { InventoryPage } from './pages/InventoryPage';
import { ProductsPage } from './pages/ProductsPage';
import { SaleFormPage } from './pages/SaleFormPage';

export const routes: RouteObject[] = [
  {
    element: (
      <SessionProvider>
        <Outlet />
      </SessionProvider>
    ),
    children: [
      {
        element: <PublicLayout />,
        children: [
          { path: '/entrar', element: <LoginPage /> },
          { path: '/registro', element: <RegisterPage /> },
        ],
      },
      {
        element: <ProtectedLayout />,
        children: [
          { path: '/', element: <HomePage /> },
          { path: '/ventas/nueva', element: <SaleFormPage /> },
          { path: '/ventas/:id', element: <SaleFormPage /> },
          { path: '/gastos/nuevo', element: <ExpenseFormPage /> },
          { path: '/gastos/:id', element: <ExpenseFormPage /> },
          { path: '/productos', element: <ProductsPage /> },
          { path: '/inventario', element: <InventoryPage /> },
          { path: '/negocio', element: <BusinessPage /> },
        ],
      },
    ],
  },
];
