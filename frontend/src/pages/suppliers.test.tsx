import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { emptyBalance, me, mockApi } from '../test/api-mock';
import { renderAt } from '../test/render';

const andina = {
  id: 's1',
  name: 'Distribuidora Andina',
  phone: '601 555 0101',
  document: '900123456-7',
  note: null,
};
const alpina = { id: 's2', name: 'Alpina', phone: null, document: null, note: null };
const payables = {
  total: '60000.00',
  suppliers: [
    {
      supplierId: 's1',
      name: 'Distribuidora Andina',
      phone: '601 555 0101',
      debt: '60000.00',
      oldestDebtAt: '2026-10-20T15:00:00Z',
      overdue: true,
    },
  ],
};
const account = {
  supplier: { id: 's1', name: 'Distribuidora Andina', phone: '601 555 0101' },
  debt: '60000.00',
  entries: [
    {
      id: 'sp1',
      type: 'SUPPLIER_PAYMENT',
      amount: '40000.00',
      description: null,
      occurredAt: '2026-10-26T16:00:00Z',
    },
    {
      id: 'e1',
      type: 'EXPENSE',
      amount: '100000.00',
      description: '50 × Gaseosa',
      occurredAt: '2026-10-20T15:00:00Z',
    },
  ],
};
const purchases = {
  total: '100000.00',
  count: 1,
  items: [
    {
      id: 'e1',
      status: 'CREDIT',
      total: '100000.00',
      description: '50 × Gaseosa',
      occurredAt: '2026-10-20T15:00:00Z',
    },
  ],
};

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-10-26T17:00:00Z'));
});

afterEach(() => {
  vi.useRealTimers();
});

describe('suppliers and payables', () => {
  it('lists suppliers with what is owed to them', async () => {
    mockApi({
      'GET /api/me': { body: me },
      'GET /api/suppliers': { body: [alpina, andina] },
      'GET /api/payables': { body: payables },
    });
    renderAt('/proveedores');
    const summary = await screen.findByRole('region', { name: 'Por pagar' });
    expect(await within(summary).findByText('$60.000')).toBeInTheDocument();
    const row = await screen.findByRole('listitem', { name: 'Distribuidora Andina' });
    expect(within(row).getByText('Le debes $60.000')).toBeInTheDocument();
    expect(within(row).getByText(/Desde el 20 de oct/)).toBeInTheDocument();
    expect(within(row).getByText('Vencida')).toBeInTheDocument();
    expect(
      within(screen.getByRole('listitem', { name: 'Alpina' })).getByText('Al día'),
    ).toBeInTheDocument();
    // Who you owe the most comes first.
    const names = screen.getAllByRole('listitem').map((li) => li.getAttribute('aria-label'));
    expect(names).toEqual(['Distribuidora Andina', 'Alpina']);
  });

  it('creates a supplier', async () => {
    const calls = mockApi({
      'GET /api/me': { body: me },
      'GET /api/suppliers': { body: [] },
      'GET /api/payables': { body: { total: '0.00', suppliers: [] } },
      'POST /api/suppliers': { status: 201, body: alpina },
    });
    renderAt('/proveedores');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText('Nombre'), 'Alpina');
    await user.type(screen.getByLabelText('NIT o documento (opcional)'), '860025900');
    await user.click(screen.getByRole('button', { name: 'Agregar proveedor' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'POST')).toBe(true));
    expect(calls.find((c) => c.method === 'POST')?.body).toEqual({
      name: 'Alpina',
      document: '860025900',
    });
  });

  it('shows a supplier account, purchases and records a payment', async () => {
    const calls = mockApi({
      'GET /api/me': { body: me },
      'GET /api/suppliers': { body: [andina] },
      'GET /api/suppliers/s1/account': { body: account },
      'GET /api/suppliers/s1/purchases': { body: purchases },
      'POST /api/supplier-payments': { status: 201, body: {} },
    });
    renderAt('/proveedores/s1');
    expect(await screen.findByText('Le debes $60.000')).toBeInTheDocument();
    const history = await screen.findByRole('list', { name: 'Historial de compras' });
    expect(within(history).getByText('50 × Gaseosa')).toBeInTheDocument();
    expect(within(history).getByText('A crédito')).toBeInTheDocument();
    const entries = screen.getByRole('list', { name: 'Compras a crédito y pagos' });
    expect(within(entries).getByText('Pago')).toBeInTheDocument();
    expect(within(entries).getByRole('link', { name: /Pago/ })).toHaveAttribute(
      'href',
      '/pagos-proveedor/sp1',
    );

    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Valor del pago'), '20.000');
    await user.click(screen.getByRole('button', { name: 'Registrar pago' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'POST')).toBe(true));
    expect(calls.find((c) => c.method === 'POST')?.body).toEqual({
      supplierId: 's1',
      amount: '20000',
      paymentMethod: 'CASH',
    });
  });

  it('explains why a supplier with debt cannot be deleted', async () => {
    mockApi({
      'GET /api/me': { body: me },
      'GET /api/suppliers': { body: [andina] },
      'GET /api/suppliers/s1/account': { body: account },
      'GET /api/suppliers/s1/purchases': { body: purchases },
      'DELETE /api/suppliers/s1': { status: 409, body: { code: 'supplier_has_debt' } },
    });
    renderAt('/proveedores/s1');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Eliminar proveedor' }));
    await user.click(await screen.findByRole('button', { name: 'Sí, eliminar' }));
    expect(
      await screen.findByText('No puedes eliminar un proveedor al que le debes dinero.'),
    ).toBeInTheDocument();
  });
});

describe('supplier details', () => {
  it('edits the supplier data', async () => {
    const calls = mockApi({
      'GET /api/me': { body: me },
      'GET /api/suppliers': { body: [andina] },
      'GET /api/suppliers/s1/account': { body: account },
      'GET /api/suppliers/s1/purchases': { body: purchases },
      'PUT /api/suppliers/s1': (body) => ({ body: { ...andina, ...(body as object) } }),
    });
    renderAt('/proveedores/s1');
    const user = userEvent.setup();
    const form = (await screen.findByRole('heading', { name: 'Datos del proveedor' })).closest(
      'form',
    )!;
    await waitFor(() =>
      expect(within(form).getByLabelText('NIT o documento (opcional)')).toHaveValue('900123456-7'),
    );
    await user.type(within(form).getByLabelText('Nota (opcional)'), 'Visita los lunes');
    await user.click(screen.getByRole('button', { name: 'Guardar proveedor' }));
    expect(await screen.findByText('Cambios guardados.')).toBeInTheDocument();
    expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({
      name: 'Distribuidora Andina',
      phone: '601 555 0101',
      document: '900123456-7',
      note: 'Visita los lunes',
    });
  });

  it('edits and deletes a supplier payment', async () => {
    const payment = {
      id: 'sp1',
      supplierId: 's1',
      supplierName: 'Distribuidora Andina',
      amount: '40000.00',
      paymentMethod: 'TRANSFER',
      occurredAt: '2026-10-26T16:00:00Z',
      note: null,
    };
    const calls = mockApi({
      'GET /api/me': { body: me },
      'GET /api/supplier-payments/sp1': { body: payment },
      'PUT /api/supplier-payments/sp1': (body) => ({ body: { ...payment, ...(body as object) } }),
      'DELETE /api/supplier-payments/sp1': { status: 204 },
      'GET /api/suppliers': { body: [andina] },
      'GET /api/suppliers/s1/account': { body: account },
      'GET /api/suppliers/s1/purchases': { body: purchases },
    });
    const router = renderAt('/pagos-proveedor/sp1');
    const user = userEvent.setup();
    expect(
      await screen.findByRole('heading', { name: 'Pago a Distribuidora Andina' }),
    ).toBeInTheDocument();
    const amount = screen.getByLabelText('Valor del pago');
    expect(amount).toHaveValue('40000');
    await user.clear(amount);
    await user.type(amount, '35.000');
    await user.click(screen.getByRole('button', { name: 'Guardar pago' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/proveedores/s1'));
    expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({
      supplierId: 's1',
      amount: '35000',
      paymentMethod: 'TRANSFER',
      occurredAt: '2026-10-26T16:00:00Z',
    });

    // Wait for the supplier screen (the old form is gone) and for the payment to load again.
    await screen.findByText('Le debes $60.000');
    await router.navigate('/pagos-proveedor/sp1');
    await waitFor(() => expect(screen.getByLabelText('Valor del pago')).toHaveValue('40000'));
    await user.click(screen.getByRole('button', { name: 'Eliminar' }));
    await user.click(await screen.findByRole('button', { name: 'Sí, eliminar' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'DELETE')).toBe(true));
  });
});

describe('purchases', () => {
  const products = [{ id: 'p1', name: 'Gaseosa', price: '3000.00', cost: '2000.00' }];

  it('records a purchase with products on credit', async () => {
    const calls = mockApi({
      'GET /api/me': { body: me },
      'GET /api/products': { body: products },
      'GET /api/suppliers': { body: [andina] },
      'POST /api/expenses': { status: 201, body: {} },
      'GET /api/balance': { body: emptyBalance },
      'GET /api/movements': { body: { period: emptyBalance.period, items: [] } },
    });
    renderAt('/gastos/nuevo');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('radio', { name: 'Compra con productos' }));
    await user.selectOptions(await screen.findByLabelText('Agregar producto'), 'p1');
    const quantity = screen.getByLabelText('Cantidad');
    await user.clear(quantity);
    await user.type(quantity, '24');
    expect(screen.getByLabelText('Costo unitario')).toHaveValue('2000.00');
    expect(screen.getByText('Total: $48.000')).toBeInTheDocument();
    await user.click(screen.getByRole('radio', { name: 'A crédito' }));
    await user.click(screen.getByRole('button', { name: 'Guardar gasto' }));
    expect(await screen.findByText('Este campo es obligatorio.')).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText('Proveedor'), 's1');
    await user.type(screen.getByLabelText('Fecha de vencimiento (opcional)'), '2026-11-15');
    await user.click(screen.getByRole('button', { name: 'Guardar gasto' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'POST')).toBe(true));
    expect(calls.find((c) => c.method === 'POST')?.body).toEqual({
      items: [{ productId: 'p1', quantity: '24', unitCost: '2000.00' }],
      category: 'PURCHASES',
      status: 'CREDIT',
      dueDate: '2026-11-15',
      supplierId: 's1',
    });
  });

  it('creates a supplier without leaving the expense', async () => {
    const calls = mockApi({
      'GET /api/me': { body: me },
      'GET /api/products': { body: products },
      'GET /api/suppliers': { body: [] },
      'POST /api/suppliers': { status: 201, body: alpina },
    });
    renderAt('/gastos/nuevo');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Nuevo proveedor' }));
    await user.type(screen.getByLabelText('Nombre del proveedor'), 'Alpina');
    await user.click(screen.getByRole('button', { name: 'Crear proveedor' }));
    await waitFor(() => expect(screen.getByLabelText('Proveedor')).toHaveValue('s2'));
    expect(calls.find((c) => c.method === 'POST')?.body).toEqual({ name: 'Alpina' });
  });

  it('shows supplier payments and what is owed on the home page', async () => {
    mockApi({
      'GET /api/me': { body: me },
      'GET /api/balance': {
        body: { ...emptyBalance, expenses: '40000.00', payables: '60000.00' },
      },
      'GET /api/movements': {
        body: {
          period: emptyBalance.period,
          items: [
            {
              id: 'sp1',
              type: 'SUPPLIER_PAYMENT',
              amount: '40000.00',
              description: null,
              paymentMethod: 'TRANSFER',
              occurredAt: '2026-10-26T16:00:00Z',
              category: null,
              supplierName: 'Distribuidora Andina',
            },
            {
              id: 'e1',
              type: 'EXPENSE',
              amount: '100000.00',
              description: '50 × Gaseosa',
              paymentMethod: 'CREDIT',
              occurredAt: '2026-10-26T15:00:00Z',
              category: 'PURCHASES',
              supplierName: 'Distribuidora Andina',
            },
          ],
        },
      },
      'GET /api/inventory': {
        body: { summary: { references: 0, lowStock: 0, value: '0.00' }, items: [] },
      },
    });
    renderAt('/');
    expect(await screen.findByText(/Por pagar: \$60\.000/)).toBeInTheDocument();
    const payment = await screen.findByRole('link', { name: /Pago a Distribuidora Andina/ });
    expect(payment).toHaveAttribute('href', '/pagos-proveedor/sp1');
    const credit = screen.getByRole('link', { name: /50 × Gaseosa/ });
    expect(within(credit).getByText(/A crédito con Distribuidora Andina/)).toBeInTheDocument();
  });

  it('offers suppliers in the navigation', async () => {
    mockApi({
      'GET /api/me': { body: me },
      'GET /api/suppliers': { body: [] },
      'GET /api/payables': { body: { total: '0.00', suppliers: [] } },
    });
    renderAt('/proveedores');
    const nav = await screen.findByRole('navigation', { name: 'Principal' });
    expect(within(nav).getByRole('link', { name: 'Proveedores' })).toHaveAttribute(
      'href',
      '/proveedores',
    );
  });
});
