export type BusinessCategory =
  'GROCERY' | 'FOOD' | 'CLOTHING' | 'BEAUTY' | 'CATALOG_SALES' | 'SERVICES' | 'OTHER';
export type PaymentMethod =
  'CASH' | 'CARD' | 'TRANSFER' | 'NEQUI' | 'DAVIPLATA' | 'PAYMENT_LINK' | 'OTHER' | 'CREDIT';
export type ExpenseCategory =
  | 'UTILITIES'
  | 'PURCHASES'
  | 'RENT'
  | 'PAYROLL'
  | 'ADMINISTRATIVE'
  | 'ADVERTISING'
  | 'TRANSPORT'
  | 'MAINTENANCE'
  | 'EQUIPMENT'
  | 'OTHER';

export interface Business {
  id: string;
  name: string;
  category: BusinessCategory | null;
  currency: string;
  timeZone: string;
}

export interface Me {
  user: { id: string; email: string };
  business: Business;
}

export interface Product {
  id: string;
  name: string;
  price: string;
  cost?: string | null;
}

export interface InventoryItem {
  productId: string;
  name: string;
  price: string;
  cost: string | null;
  stock: string;
  minStock: string | null;
  low: boolean;
}

export interface Inventory {
  summary: { references: number; lowStock: number; value: string };
  items: InventoryItem[];
}

export type StockMovementType =
  | 'INITIAL'
  | 'PURCHASE'
  | 'LOSS'
  | 'COUNT'
  | 'SALE'
  | 'SALE_EDIT'
  | 'SALE_DELETE'
  | 'PURCHASE_EDIT'
  | 'PURCHASE_DELETE';

export interface StockMovement {
  type: StockMovementType;
  change: string;
  stockAfter: string;
  note: string | null;
  occurredAt: string;
}

export interface SaleItem {
  productId: string | null;
  name: string;
  unitPrice: string;
  quantity: string;
  lineTotal: string;
}

export interface Sale {
  id: string;
  kind: 'FREE' | 'ITEMS';
  total: string;
  items: SaleItem[];
  concept: string | null;
  paymentMethod: PaymentMethod;
  occurredAt: string;
  note: string | null;
  customerId?: string | null;
  customerName?: string | null;
  status?: 'PAID' | 'CREDIT';
  dueDate?: string | null;
}

export interface ExpenseItem {
  productId: string | null;
  name: string;
  unitCost: string;
  quantity: string;
  lineTotal: string;
}

export interface Expense {
  id: string;
  amount: string;
  items?: ExpenseItem[];
  category: ExpenseCategory;
  concept: string | null;
  paymentMethod: PaymentMethod;
  occurredAt: string;
  note: string | null;
  supplierId?: string | null;
  supplierName?: string | null;
  status?: 'PAID' | 'CREDIT';
  dueDate?: string | null;
}

export interface PeriodRange {
  from: string;
  to: string;
}

export interface Movement {
  id: string;
  type: 'SALE' | 'EXPENSE' | 'PAYMENT' | 'SUPPLIER_PAYMENT';
  amount: string;
  description: string | null;
  paymentMethod: PaymentMethod;
  occurredAt: string;
  category: ExpenseCategory | null;
  customerName?: string | null;
  supplierName?: string | null;
}

export interface Balance {
  period: PeriodRange;
  sales: string;
  expenses: string;
  balance: string;
  payments?: string;
  receivable?: string;
  payables?: string;
}

export interface Customer {
  id: string;
  name: string;
  phone: string | null;
  note: string | null;
  document?: string | null;
  address?: string | null;
}

export interface CustomerPurchases {
  total: string;
  count: number;
  items: {
    id: string;
    status: 'PAID' | 'CREDIT';
    total: string;
    description: string | null;
    occurredAt: string;
  }[];
}

export interface Receivables {
  total: string;
  customers: {
    customerId: string;
    name: string;
    phone: string | null;
    debt: string;
    oldestDebtAt?: string | null;
    overdue?: boolean;
  }[];
}

export interface AccountEntry {
  id: string;
  type: 'SALE' | 'PAYMENT' | 'EXPENSE' | 'SUPPLIER_PAYMENT';
  amount: string;
  description: string | null;
  occurredAt: string;
}

export interface CustomerAccount {
  customer: { id: string; name: string; phone: string | null };
  debt: string;
  entries: AccountEntry[];
}

export interface CustomerPayment {
  id: string;
  customerId: string | null;
  customerName: string;
  amount: string;
  paymentMethod: PaymentMethod;
  occurredAt: string;
  note: string | null;
}

export interface Supplier {
  id: string;
  name: string;
  phone: string | null;
  document: string | null;
  note: string | null;
}

export interface Payables {
  total: string;
  suppliers: {
    supplierId: string;
    name: string;
    phone: string | null;
    debt: string;
    oldestDebtAt: string | null;
    overdue: boolean;
  }[];
}

export interface SupplierAccount {
  supplier: { id: string; name: string; phone: string | null };
  debt: string;
  entries: AccountEntry[];
}

/** Every expense with one supplier (paid or on credit), newest first. */
export type SupplierPurchases = CustomerPurchases;

export interface SupplierPayment {
  id: string;
  supplierId: string | null;
  supplierName: string;
  amount: string;
  paymentMethod: PaymentMethod;
  occurredAt: string;
  note: string | null;
}
