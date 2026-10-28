export type BusinessCategory =
  'GROCERY' | 'FOOD' | 'CLOTHING' | 'BEAUTY' | 'CATALOG_SALES' | 'SERVICES' | 'OTHER';
export type PaymentMethod = 'CASH' | 'CARD' | 'TRANSFER' | 'OTHER';
export type ExpenseCategory =
  'PURCHASES' | 'RENT' | 'UTILITIES' | 'PAYROLL' | 'TRANSPORT' | 'MAINTENANCE' | 'TAXES' | 'OTHER';

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
  tracked: boolean;
  stock: string | null;
  minStock: string | null;
  low: boolean;
}

export interface Inventory {
  summary: { tracked: number; lowStock: number; value: string };
  items: InventoryItem[];
}

export type StockMovementType =
  'INITIAL' | 'PURCHASE' | 'LOSS' | 'COUNT' | 'SALE' | 'SALE_EDIT' | 'SALE_DELETE';

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
}

export interface Expense {
  id: string;
  amount: string;
  category: ExpenseCategory;
  concept: string | null;
  paymentMethod: PaymentMethod;
  occurredAt: string;
  note: string | null;
}

export interface PeriodRange {
  from: string;
  to: string;
}

export interface Movement {
  id: string;
  type: 'SALE' | 'EXPENSE';
  amount: string;
  description: string | null;
  paymentMethod: PaymentMethod;
  occurredAt: string;
  category: ExpenseCategory | null;
}

export interface Balance {
  period: PeriodRange;
  sales: string;
  expenses: string;
  balance: string;
}
