// Every user-visible text lives here (constitution principle 12).
import type { ExpenseCategory, PaymentMethod, StockMovementType } from '../api/types';

export const BRAND = 'MyPrueba';

/** The 59 business types offered in Colombia, grouped as in the referent (verified with REA). */
export const businessCategoryGroups: Record<string, Record<string, string>> = {
  Comercio: {
    ACCESSORIES_STORE: 'Accesorios y bisutería',
    AGRICULTURE: 'Agricultura y ganadería',
    WATER_ICE_GAS: 'Agua, hielo y gas',
    CRAFTS_STORE: 'Artesanías y manualidades',
    AUTO_PARTS_STORE: 'Artículos automotrices',
    BEAUTY: 'Artículos de belleza',
    SPORTS_STORE: 'Artículos deportivos',
    HOME_ITEMS: 'Artículos para el hogar',
    RELIGIOUS_STORE: 'Artículos religiosos y esotéricos',
    BUTCHERY_STORE: 'Carnicería',
    WHOLESALE_STORE: 'Distribuidora / mayorista',
    COMPUTING: 'Electrónica e informática',
    PHARMACY: 'Farmacia y droguería',
    HARDWARE_STORE: 'Ferretería y construcción',
    FLOWER_SHOP: 'Floristería y vivero',
    INDUSTRY: 'Industria o manufactura',
    FARM_SUPPLIES: 'Insumos agropecuarios',
    TOY_STORE: 'Juguetería',
    MINI_MARKET: 'Minimercado',
    STATIONERY: 'Papelería y libros',
    CLEANING_STORE: 'Productos de aseo y limpieza',
    RECYCLING: 'Reciclaje y chatarra',
    CLOTHING: 'Ropa y calzado',
    DELICATESSEN_STORE: 'Salsamentaria',
    ADULT_STORE: 'Sex shop',
    LOCAL_MARKET: 'Tienda de barrio',
    PET_SHOP: 'Tienda de mascotas o vet',
    NATURAL_STORE: 'Tienda naturista y/o suplementos',
    GIFT_STORE: 'Tiendas de regalos',
    CIGARETTE_SHOP: 'Vape, tabaquería y smoke shop',
    VARIETY_STORE: 'Variedades y bazares',
    VEHICLE_DEALERSHIP: 'Venta de automóviles',
    CATALOG_SALES: 'Venta por catálogo',
  },
  'Restaurantes, cafés y licores': {
    BAR: 'Bar',
    COFFEE_SHOP: 'Cafetería',
    ICE_CREAM_AND_JUICES: 'Heladería, jugos y postres',
    LIQUOR_STORE: 'Licorería',
    BAKERY: 'Panadería y repostería',
    RESTAURANT: 'Restaurante o comida rápida',
  },
  Servicios: {
    PROPERTY_RENTAL: 'Arriendos y administración de propiedades',
    HAIR_SALON: 'Barbería y peluquería',
    MARKETING: 'Diseño, impresión y personalizados',
    ENTERTAINMENT_VENUE: 'Entretenimiento y ocio',
    HEALTH_AND_WELLNESS: 'Estética y salud',
    FITNESS_CENTER: 'Gimnasio',
    TOURISM: 'Hoteles y turismo',
    CAR_WASH: 'Lavadero de autos y detailing',
    LAUNDRY: 'Lavandería y tintorería',
    EVENT_PLANNING: 'Organización de eventos',
    FINANCIAL_SERVICES: 'Préstamos y financiamiento',
    PAYMENTS_AND_LOTTERY: 'Recargas, giros y loterías',
    MAINTENANCE_SERVICES: 'Reparaciones y mantenimiento',
    TAILORING: 'Sastrería, modistería y arreglos',
    EDUCATIONAL_SERVICES: 'Servicios educativos',
    PROFESSIONAL_SERVICES: 'Servicios profesionales',
    CARS: 'Taller automotriz',
    TATTOO_STUDIO: 'Tatuajes y piercings',
    LOGISTICS: 'Transporte y logística',
    NAILS_AND_LASHES: 'Uñas, pestañas y cejas',
  },
};

export const businessCategories: Record<string, string> = Object.assign(
  {},
  ...Object.values(businessCategoryGroups),
);

/** Methods that move money now: expenses and customer payments. */
export const cashPaymentMethods: Record<Exclude<PaymentMethod, 'CREDIT'>, string> = {
  CASH: 'Efectivo',
  CARD: 'Tarjeta',
  TRANSFER: 'Transferencia bancaria',
  NEQUI: 'Nequi',
  DAVIPLATA: 'Daviplata',
  PAYMENT_LINK: 'Link de pago',
  OTHER: 'Otro',
};

/** Sales also accept "Fiado": the customer pays later. */
export const paymentMethods: Record<PaymentMethod, string> = {
  ...cashPaymentMethods,
  CREDIT: 'Fiado',
};

export const expenseCategories: Record<ExpenseCategory, string> = {
  UTILITIES: 'Servicios públicos',
  PURCHASES: 'Compra de productos e insumos',
  RENT: 'Arriendo',
  PAYROLL: 'Nómina',
  ADMINISTRATIVE: 'Gastos administrativos',
  ADVERTISING: 'Mercadeo y publicidad',
  TRANSPORT: 'Transporte, domicilios y logística',
  MAINTENANCE: 'Mantenimiento y reparaciones',
  EQUIPMENT: 'Muebles, equipos o maquinaria',
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
  PURCHASE_EDIT: 'Compra editada',
  PURCHASE_DELETE: 'Compra eliminada',
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
  before_sale: 'No puede ser anterior a la fecha de la venta.',
  before_expense: 'No puede ser anterior a la fecha del gasto.',
  expense_kind: 'Indica un valor o agrega al menos un producto.',
  purchase_category: 'Una compra con productos va en la categoría Compra de productos e insumos.',
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
  supplier_has_debt: 'No puedes eliminar un proveedor al que le debes dinero.',
  supplier_not_found: 'El proveedor ya no existe.',
  insufficient_stock: 'No hay suficiente stock para esta venta.',
};

export function fieldMessage(code: string): string {
  return fieldMessages[code] ?? fieldMessages.invalid_format!;
}

export function formMessage(code: string, details: Record<string, unknown> = {}): string {
  if (code === 'insufficient_stock' && details.productName) {
    return `No hay suficiente stock de ${String(details.productName)} (disponibles: ${String(details.available)}). Ajusta la cantidad o permite vender sin stock en Negocio.`;
  }
  return formMessages[code] ?? 'Algo salió mal. Intenta de nuevo.';
}
