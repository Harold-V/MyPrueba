// Every user-visible text lives here (constitution principle 12).
import type {
  BusinessCategory,
  ExpenseCategory,
  PaymentMethod,
  StockMovementType,
} from '../api/types';

export const BRAND = 'MyPrueba';

export const businessCategories: Record<BusinessCategory, string> = {
  GROCERY: 'Tienda / abarrotes',
  FOOD: 'Restaurante / comidas',
  CLOTHING: 'Ropa / calzado',
  BEAUTY: 'Belleza / peluquería',
  CATALOG_SALES: 'Venta por catálogo',
  SERVICES: 'Servicios',
  OTHER: 'Otro',
};

/** Methods that move money now: expenses and customer payments. */
export const cashPaymentMethods: Record<Exclude<PaymentMethod, 'CREDIT'>, string> = {
  CASH: 'Efectivo',
  CARD: 'Tarjeta',
  TRANSFER: 'Transferencia',
  OTHER: 'Otro',
};

/** Sales also accept "Fiado": the customer pays later. */
export const paymentMethods: Record<PaymentMethod, string> = {
  ...cashPaymentMethods,
  CREDIT: 'Fiado',
};

export const expenseCategories: Record<ExpenseCategory, string> = {
  PURCHASES: 'Compra de productos / insumos',
  RENT: 'Arriendo',
  UTILITIES: 'Servicios públicos',
  PAYROLL: 'Nómina',
  TRANSPORT: 'Transporte',
  MAINTENANCE: 'Mantenimiento',
  TAXES: 'Impuestos',
  OTHER: 'Otros',
};

export const stockMovementTypes: Record<StockMovementType, string> = {
  INITIAL: 'Stock inicial',
  PURCHASE: 'Entrada',
  LOSS: 'Pérdida',
  COUNT: 'Conteo',
  SALE: 'Venta',
  SALE_EDIT: 'Venta editada',
  SALE_DELETE: 'Venta eliminada',
};

export const adjustmentTypes = {
  PURCHASE: 'Entrada (compra de mercancía)',
  LOSS: 'Pérdida o daño',
  COUNT: 'Conteo físico (fija el stock)',
};

export function lowStockMessage(count: number): string {
  return count === 1 ? '1 producto con stock bajo' : `${count} productos con stock bajo`;
}

const fieldMessages: Record<string, string> = {
  required: 'Este campo es obligatorio.',
  too_short: 'Debe tener al menos 8 caracteres.',
  too_long: 'Es demasiado largo.',
  invalid_format: 'El formato no es válido.',
  invalid_value: 'Elige una opción válida.',
  must_be_positive: 'Debe ser mayor que cero.',
  amount_too_large: 'El valor es demasiado grande.',
  future_date: 'La fecha no puede ser futura.',
  sale_kind: 'Indica un valor o agrega al menos un producto.',
  before_from: 'Debe ser igual o posterior a la fecha inicial.',
  range_too_long: 'El rango no puede superar un año.',
  exceeds_debt: 'El abono no puede superar la deuda.',
};

const formMessages: Record<string, string> = {
  email_taken: 'Ya existe una cuenta con este email.',
  invalid_credentials: 'Email o contraseña incorrectos.',
  product_name_taken: 'Ya tienes un producto con ese nombre.',
  product_not_found: 'Uno de los productos ya no existe. Revisa la venta.',
  not_found: 'No encontramos este registro.',
  network_error: 'No hay conexión. Revisa tu internet e intenta de nuevo.',
  validation_failed: 'Revisa los campos marcados.',
  not_tracked: 'Este producto no tiene control de inventario.',
  customer_has_debt: 'No puedes eliminar un cliente con saldo pendiente.',
  customer_not_found: 'El cliente ya no existe.',
};

export function fieldMessage(code: string): string {
  return fieldMessages[code] ?? fieldMessages.invalid_format!;
}

export function formMessage(code: string): string {
  return formMessages[code] ?? 'Algo salió mal. Intenta de nuevo.';
}
