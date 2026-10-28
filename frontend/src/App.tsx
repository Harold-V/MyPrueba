import { Outlet, type RouteObject } from 'react-router';
import { SessionProvider } from './auth/session';
import { ProtectedLayout, PublicLayout } from './components/Layouts';
import { LoginPage, RegisterPage } from './pages/AuthPages';
import { BusinessPage } from './pages/BusinessPage';
import { ExpenseFormPage } from './pages/ExpenseFormPage';
import { CustomerPage, CustomersPage } from './pages/CustomersPage';
import { HomePage } from './pages/HomePage';
import { PaymentPage } from './pages/PaymentPage';
import { InventoryPage } from './pages/InventoryPage';
import { ProductsPage } from './pages/ProductsPage';
import { CashRegisterPage } from './pages/CashRegisterPage';
import { PosPage } from './pages/PosPage';
import { ReceiptPage } from './pages/ReceiptPage';
import { SaleFormPage } from './pages/SaleFormPage';
import { SupplierPage, SupplierPaymentPage, SuppliersPage } from './pages/SuppliersPage';

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
          { path: '/vender', element: <PosPage /> },
          { path: '/caja', element: <CashRegisterPage /> },
          { path: '/ventas/nueva', element: <SaleFormPage /> },
          { path: '/ventas/:id/comprobante', element: <ReceiptPage /> },
          { path: '/ventas/:id', element: <SaleFormPage /> },
          { path: '/gastos/nuevo', element: <ExpenseFormPage /> },
          { path: '/gastos/:id', element: <ExpenseFormPage /> },
          { path: '/productos', element: <ProductsPage /> },
          { path: '/inventario', element: <InventoryPage /> },
          { path: '/clientes', element: <CustomersPage /> },
          { path: '/clientes/:id', element: <CustomerPage /> },
          { path: '/abonos/:id', element: <PaymentPage /> },
          { path: '/proveedores', element: <SuppliersPage /> },
          { path: '/proveedores/:id', element: <SupplierPage /> },
          { path: '/pagos-proveedor/:id', element: <SupplierPaymentPage /> },
          { path: '/negocio', element: <BusinessPage /> },
        ],
      },
    ],
  },
];
