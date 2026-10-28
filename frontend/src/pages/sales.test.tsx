import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { me, mockApi } from '../test/api-mock';
import { renderAt } from '../test/render';

const gaseosa = {
  id: 'p1',
  name: 'Gaseosa',
  price: '3000.00',
  cost: null,
  categoryId: 'c1',
  categoryName: 'Bebidas',
  barcode: '7702004003508',
  groupId: null,
  variant: null,
};
const pan = {
  ...gaseosa,
  id: 'p2',
  name: 'Pan',
  price: '500.00',
  categoryId: null,
  categoryName: null,
  barcode: null,
};
const inventory = {
  summary: { references: 2, lowStock: 0, value: '0.00' },
  items: [
    {
      productId: 'p1',
      name: 'Gaseosa',
      price: '3000.00',
      cost: null,
      stock: '10',
      minStock: null,
      low: false,
    },
    {
      productId: 'p2',
      name: 'Pan',
      price: '500.00',
      cost: null,
      stock: '20',
      minStock: null,
      low: false,
    },
  ],
};
const juan = { id: 'c1', name: 'Juan', phone: '300 123 4567', note: null };
const pos = {
  'GET /api/me': { body: me },
  'GET /api/products': { body: [gaseosa, pan] },
  'GET /api/product-categories': { body: [{ id: 'c1', name: 'Bebidas' }] },
  'GET /api/inventory': { body: inventory },
  'GET /api/customers': { body: [juan] },
  'GET /api/business/settings': { body: { allowNegativeStock: true } },
};
const receipt = {
  id: 's1',
  number: 12,
  occurredAt: '2026-10-28T15:00:00Z',
  business: {
    name: 'Tienda Doña Rosa',
    document: '1061700000-1',
    phone: '312 000 0000',
    address: 'Cra 7 # 12-34, Popayán',
  },
  customer: { name: 'Juan', document: '10203040', phone: '300 123 4567' },
  items: [
    {
      productId: 'p1',
      name: 'Gaseosa',
      unitPrice: '3000.00',
      quantity: '2',
      lineTotal: '6000.00',
      discount: '600.00',
    },
    {
      productId: 'p2',
      name: 'Pan',
      unitPrice: '500.00',
      quantity: '4',
      lineTotal: '2000.00',
      discount: '0.00',
    },
  ],
  subtotal: '8000.00',
  discountTotal: '1100.00',
  total: '6900.00',
  paymentMethod: 'CASH',
  status: 'PAID',
  dueDate: null,
  received: '10000.00',
  change: '3100.00',
  note: null,
};

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-10-28T17:00:00Z'));
});

afterEach(() => {
  vi.useRealTimers();
});

describe('point of sale', () => {
  it('sells from the cart with discounts and change', async () => {
    const calls = mockApi({
      ...pos,
      'POST /api/sales': { status: 201, body: { id: 's1' } },
      'GET /api/sales/s1/receipt': { body: receipt },
    });
    const router = renderAt('/vender');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: /Agregar Gaseosa/ }));
    await user.click(screen.getByRole('button', { name: /Agregar Gaseosa/ }));
    await user.click(screen.getByRole('button', { name: /Agregar Pan/ }));
    const cart = screen.getByRole('region', { name: 'Carrito' });
    for (let i = 0; i < 3; i++) {
      await user.click(within(cart).getByRole('button', { name: 'Uno más de Pan' }));
    }
    await user.click(within(cart).getByRole('button', { name: 'Uno menos de Pan' }));
    await user.click(within(cart).getByRole('button', { name: 'Uno más de Pan' }));
    expect(within(cart).getByLabelText('Cantidad de Pan')).toHaveValue('4');
    await user.type(within(cart).getByLabelText('Descuento de Gaseosa'), '10%');
    await user.type(within(cart).getByLabelText('Descuento a toda la venta'), '500');
    const totals = within(cart).getByRole('list', { name: 'Totales' });
    expect(within(totals).getByText('$8.000')).toBeInTheDocument();
    expect(within(totals).getByText('−$1.100')).toBeInTheDocument();
    expect(within(totals).getByText('$6.900')).toBeInTheDocument();
    await user.type(within(cart).getByLabelText('¿Con cuánto paga?'), '10.000');
    expect(within(cart).getByText('Cambio: $3.100')).toBeInTheDocument();
    await user.click(within(cart).getByRole('button', { name: 'Cobrar $6.900' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/ventas/s1/comprobante'));
    expect(calls.find((c) => c.method === 'POST')?.body).toEqual({
      items: [
        { productId: 'p1', quantity: '2', discountPercent: '10' },
        { productId: 'p2', quantity: '4' },
      ],
      discount: '500',
      paymentMethod: 'CASH',
      received: '10000',
    });
  });

  it('finds products by category, text or barcode', async () => {
    mockApi(pos);
    renderAt('/vender');
    const user = userEvent.setup();
    const catalog = await screen.findByRole('region', { name: 'Catálogo' });
    await within(catalog).findByRole('button', { name: /Agregar Pan/ });
    await user.click(within(catalog).getByRole('button', { name: 'Bebidas' }));
    expect(within(catalog).queryByRole('button', { name: /Agregar Pan/ })).not.toBeInTheDocument();
    await user.click(within(catalog).getByRole('button', { name: 'Todas' }));
    const search = within(catalog).getByLabelText('Buscar producto (nombre o código)');
    await user.type(search, 'pan');
    expect(
      within(catalog).queryByRole('button', { name: /Agregar Gaseosa/ }),
    ).not.toBeInTheDocument();
    await user.clear(search);
    await user.type(search, '7702004003508{Enter}');
    expect(search).toHaveValue('');
    const cart = screen.getByRole('region', { name: 'Carrito' });
    expect(within(cart).getByLabelText('Cantidad de Gaseosa')).toHaveValue('1');
  });

  it('sells on credit to a customer', async () => {
    const calls = mockApi({
      ...pos,
      'POST /api/sales': { status: 201, body: { id: 's2' } },
      'GET /api/sales/s2/receipt': { body: { ...receipt, id: 's2' } },
    });
    renderAt('/vender');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: /Agregar Pan/ }));
    const cart = screen.getByRole('region', { name: 'Carrito' });
    await user.click(within(cart).getByRole('radio', { name: 'Fiada' }));
    await user.click(within(cart).getByRole('button', { name: 'Cobrar $500' }));
    expect(await within(cart).findByText('Elige a quién le fías.')).toBeInTheDocument();
    await user.selectOptions(within(cart).getByLabelText('Cliente'), 'c1');
    await user.click(within(cart).getByRole('button', { name: 'Cobrar $500' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'POST')).toBe(true));
    expect(calls.find((c) => c.method === 'POST')?.body).toEqual({
      items: [{ productId: 'p2', quantity: '1' }],
      status: 'CREDIT',
      customerId: 'c1',
    });
  });

  it('explains when a discount is too big', async () => {
    mockApi({
      ...pos,
      'POST /api/sales': {
        status: 400,
        body: {
          code: 'validation_failed',
          errors: [{ field: 'items[0].discount', code: 'exceeds_line' }],
        },
      },
    });
    renderAt('/vender');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: /Agregar Pan/ }));
    const cart = screen.getByRole('region', { name: 'Carrito' });
    await user.type(within(cart).getByLabelText('Descuento de Pan'), '900');
    expect(
      within(cart).getByText('El descuento no puede superar el valor del producto.'),
    ).toBeInTheDocument();
    await user.click(within(cart).getByRole('button', { name: /Cobrar/ }));
    expect(await within(cart).findByRole('alert')).toHaveTextContent('Revisa los campos marcados.');
  });
});

describe('digital receipt', () => {
  it('shows the receipt and shares it', async () => {
    const print = vi.fn();
    vi.stubGlobal('print', print);
    mockApi({ 'GET /api/me': { body: me }, 'GET /api/sales/s1/receipt': { body: receipt } });
    renderAt('/ventas/s1/comprobante');
    expect(
      await screen.findByRole('heading', { name: 'Comprobante de venta #12' }),
    ).toBeInTheDocument();
    const doc = screen.getByRole('article', { name: 'Comprobante' });
    expect(within(doc).getByText('Tienda Doña Rosa')).toBeInTheDocument();
    expect(within(doc).getByText(/NIT 1061700000-1/)).toBeInTheDocument();
    expect(within(doc).getByText(/Juan · Doc. 10203040/)).toBeInTheDocument();
    expect(within(doc).getByText('Descuento −$600')).toBeInTheDocument();
    expect(within(doc).getByText('Recibido $10.000 · Cambio $3.100')).toBeInTheDocument();
    const whatsapp = screen.getByRole('link', { name: 'Enviar por WhatsApp' });
    expect(whatsapp.getAttribute('href')).toMatch(/^https:\/\/wa\.me\/573001234567\?text=/);
    expect(decodeURIComponent(whatsapp.getAttribute('href')!)).toContain('Total: $6.900');
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Imprimir o guardar PDF' }));
    expect(print).toHaveBeenCalled();
    expect(screen.getByRole('link', { name: 'Nueva venta' })).toHaveAttribute('href', '/vender');
  });

  it('marks credit sales with their due date', async () => {
    mockApi({
      'GET /api/me': { body: me },
      'GET /api/sales/s1/receipt': {
        body: {
          ...receipt,
          status: 'CREDIT',
          paymentMethod: 'CREDIT',
          dueDate: '2026-11-15',
          received: null,
          change: null,
        },
      },
    });
    renderAt('/ventas/s1/comprobante');
    expect(await screen.findByText('Fiado · vence el 15 de nov de 2026')).toBeInTheDocument();
  });
});

describe('cash register', () => {
  const open = {
    id: 'r1',
    openedAt: '2026-10-28T13:05:00Z',
    opening: '50000.00',
    cashSales: '6900.00',
    cashPayments: '5000.00',
    cashExpenses: '2000.00',
    cashSupplierPayments: '0.00',
    expected: '59900.00',
    closedAt: null,
    counted: null,
    difference: null,
    note: null,
  };

  it('opens the register with a float', async () => {
    const calls = mockApi({
      'GET /api/me': { body: me },
      'GET /api/cash-register': () => ({
        body: calls.some((c) => c.method === 'POST')
          ? { open, last: null }
          : { open: null, last: null },
      }),
      'GET /api/cash-register/history': { body: [] },
      'POST /api/cash-register/open': { status: 201, body: open },
    });
    renderAt('/caja');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText('Base en efectivo'), '50.000');
    await user.click(screen.getByRole('button', { name: 'Abrir caja' }));
    expect(calls.find((c) => c.method === 'POST')?.body).toEqual({ openingAmount: '50000' });
    expect(await screen.findByText('Efectivo esperado')).toBeInTheDocument();
  });

  it('shows the expected cash and closes with a count', async () => {
    const closed = {
      ...open,
      closedAt: '2026-10-28T22:00:00Z',
      counted: '59000.00',
      difference: '-900.00',
    };
    const calls = mockApi({
      'GET /api/me': { body: me },
      'GET /api/cash-register': () => ({
        body: calls.some((c) => c.method === 'POST')
          ? { open: null, last: closed }
          : { open, last: null },
      }),
      'GET /api/cash-register/history': { body: [] },
      'POST /api/cash-register/close': { body: closed },
    });
    renderAt('/caja');
    const user = userEvent.setup();
    const breakdown = await screen.findByRole('list', { name: 'Movimientos en efectivo' });
    expect(within(breakdown).getByText('+$6.900')).toBeInTheDocument();
    expect(within(breakdown).getByText('−$2.000')).toBeInTheDocument();
    expect(screen.getByText('$59.900')).toBeInTheDocument();
    await user.type(screen.getByLabelText('Efectivo contado'), '59.000');
    await user.click(screen.getByRole('button', { name: 'Cerrar caja' }));
    expect(calls.find((c) => c.method === 'POST')?.body).toEqual({ countedAmount: '59000' });
    expect(await screen.findByText('Faltante: $900')).toBeInTheDocument();
  });
});

describe('sale form discounts', () => {
  it('edits a sale keeping its discounts, number and cash received', async () => {
    const calls = mockApi({
      ...pos,
      'GET /api/sales/s1': {
        body: {
          id: 's1',
          number: 12,
          kind: 'ITEMS',
          subtotal: '8000.00',
          discountTotal: '1100.00',
          total: '6900.00',
          items: [
            {
              productId: 'p1',
              name: 'Gaseosa',
              unitPrice: '3000.00',
              quantity: '2',
              lineTotal: '6000.00',
              discount: '600.00',
            },
            {
              productId: 'p2',
              name: 'Pan',
              unitPrice: '500.00',
              quantity: '4',
              lineTotal: '2000.00',
              discount: '0.00',
            },
          ],
          concept: null,
          paymentMethod: 'CASH',
          occurredAt: '2026-10-28T15:00:00Z',
          note: null,
          status: 'PAID',
          received: '10000.00',
          change: '3100.00',
        },
      },
      'PUT /api/sales/s1': { body: {} },
      'GET /api/balance': {
        body: {
          period: { from: '2026-10-28', to: '2026-10-28' },
          sales: '0.00',
          expenses: '0.00',
          balance: '0.00',
        },
      },
      'GET /api/movements': {
        body: { period: { from: '2026-10-28', to: '2026-10-28' }, items: [] },
      },
    });
    renderAt('/ventas/s1');
    const user = userEvent.setup();
    expect(await screen.findByRole('heading', { name: 'Editar venta #12' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ver comprobante' })).toHaveAttribute(
      'href',
      '/ventas/s1/comprobante',
    );
    expect(screen.getAllByLabelText('Descuento (opcional)')[0]).toHaveValue('600');
    const saleDiscount = screen.getByLabelText('Descuento a toda la venta (opcional)');
    expect(saleDiscount).toHaveValue('500');
    await user.clear(saleDiscount);
    await user.type(saleDiscount, '5%');
    expect(screen.getByText('Total: $7.030')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Guardar venta' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'PUT')).toBe(true));
    expect(calls.find((c) => c.method === 'PUT')?.body).toMatchObject({
      items: [
        { productId: 'p1', quantity: '2', unitPrice: '3000.00', discount: '600' },
        { productId: 'p2', quantity: '4', unitPrice: '500.00' },
      ],
      discountPercent: '5',
      paymentMethod: 'CASH',
      received: '10000.00',
    });
  });
});

describe('sales elsewhere', () => {
  it('saves the business data shown on receipts', async () => {
    const calls = mockApi({
      'GET /api/me': { body: me },
      'GET /api/business/settings': { body: { allowNegativeStock: true } },
      'PUT /api/business': (body) => ({ body: { ...me.business, ...(body as object) } }),
    });
    renderAt('/negocio');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText('NIT o documento (opcional)'), '1061700000-1');
    await user.type(screen.getByLabelText('Teléfono (opcional)'), '312 000 0000');
    await user.type(screen.getByLabelText('Dirección (opcional)'), 'Cra 7 # 12-34');
    await user.click(screen.getByRole('button', { name: 'Guardar' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'PUT')).toBe(true));
    expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({
      name: 'Tienda Doña Rosa',
      category: 'LOCAL_MARKET',
      document: '1061700000-1',
      phone: '312 000 0000',
      address: 'Cra 7 # 12-34',
    });
  });

  it('offers selling and the register in the navigation', async () => {
    mockApi(pos);
    renderAt('/vender');
    const nav = await screen.findByRole('navigation', { name: 'Principal' });
    expect(within(nav).getByRole('link', { name: 'Vender' })).toHaveAttribute('href', '/vender');
    const user = userEvent.setup();
    await user.click(within(nav).getByRole('button', { name: 'Más' }));
    const more = screen.getByRole('dialog', { name: 'Más opciones' });
    expect(within(more).getByRole('link', { name: 'Caja' })).toHaveAttribute('href', '/caja');
    expect(within(more).getByRole('link', { name: 'Negocio' })).toHaveAttribute('href', '/negocio');
  });
});
