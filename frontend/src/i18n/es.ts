// Every user-visible text lives here (constitution principle 12).
import type { BusinessCategory, ExpenseCategory, PaymentMethod } from '../api/types';

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

export const paymentMethods: Record<PaymentMethod, string> = {
  CASH: 'Efectivo',
  CARD: 'Tarjeta',
  TRANSFER: 'Transferencia',
  OTHER: 'Otro',
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
};

const formMessages: Record<string, string> = {
  email_taken: 'Ya existe una cuenta con este email.',
  invalid_credentials: 'Email o contraseña incorrectos.',
  product_name_taken: 'Ya tienes un producto con ese nombre.',
  product_not_found: 'Uno de los productos ya no existe. Revisa la venta.',
  not_found: 'No encontramos este registro.',
  network_error: 'No hay conexión. Revisa tu internet e intenta de nuevo.',
  validation_failed: 'Revisa los campos marcados.',
};

export function fieldMessage(code: string): string {
  return fieldMessages[code] ?? fieldMessages.invalid_format!;
}

export function formMessage(code: string): string {
  return formMessages[code] ?? 'Algo salió mal. Intenta de nuevo.';
}
