import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { emptyBalance, me, mockApi } from '../test/api-mock';
import { renderAt } from '../test/render';

const period = { from: '2026-10-26', to: '2026-10-26' };

const movements = [
  {
    id: 's1',
    type: 'SALE',
    amount: '8000.00',
    description: '2 × Gaseosa, 4 × Pan',
    paymentMethod: 'CASH',
    occurredAt: '2026-10-26T15:00:00Z',
    category: null,
  },
  {
    id: 'e1',
    type: 'EXPENSE',
    amount: '50000.00',
    description: null,
    paymentMethod: 'TRANSFER',
    occurredAt: '2026-10-26T14:00:00Z',
    category: 'RENT',
  },
];

const products = [
  { id: 'p1', name: 'Gaseosa', price: '3000.00' },
  { id: 'p2', name: 'Pan', price: '500.00' },
];

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-10-26T15:00:00Z'));
});

afterEach(() => {
  vi.useRealTimers();
});

describe('home', () => {
  it('invites to record the first sale when the period is empty', async () => {
    mockApi({
      'GET /api/me': { body: me },
      'GET /api/balance': { body: emptyBalance },
      'GET /api/movements': { body: { period, items: [] } },
    });
    renderAt('/');
    expect(await screen.findByText('Aún no hay movimientos en este periodo.')).toBeInTheDocument();
    const summary = screen.getByRole('region', { name: 'Resumen' });
    expect(within(summary).getByText('Balance').nextSibling).toHaveTextContent('$0');
    expect(screen.getByRole('link', { name: 'Nueva venta' })).toHaveAttribute(
      'href',
      '/ventas/nueva',
    );
  });

  it('shows totals and movements, and changes period and filter', async () => {
    const calls = mockApi({
      'GET /api/me': { body: me },
      'GET /api/balance': {
        body: { period, sales: '100000.00', expenses: '130000.00', balance: '-30000.00' },
      },
      'GET /api/movements': (_, url) => ({
        body: {
          period,
          items:
            url.searchParams.get('type') === 'expenses'
              ? movements.filter((m) => m.type === 'EXPENSE')
              : movements,
        },
      }),
    });
    renderAt('/');
    const summary = await screen.findByRole('region', { name: 'Resumen' });
    expect(await within(summary).findByText('-$30.000')).toBeInTheDocument();
    expect(within(summary).getByText('$100.000')).toBeInTheDocument();
    expect(await screen.findByText('2 × Gaseosa, 4 × Pan')).toBeInTheDocument();
    expect(screen.getByText('Arriendo')).toBeInTheDocument();

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Mes' }));
    await waitFor(() =>
      expect(calls.some((c) => c.path === '/api/balance' && c.search === '?period=month')).toBe(
        true,
      ),
    );
    await user.click(screen.getByRole('button', { name: 'Gastos' }));
    await waitFor(() => expect(screen.queryByText('2 × Gaseosa, 4 × Pan')).not.toBeInTheDocument());
    expect(
      calls.some((c) => c.path === '/api/movements' && c.search === '?period=month&type=expenses'),
    ).toBe(true);
  });

  it('queries a custom date range', async () => {
    const calls = mockApi({
      'GET /api/me': { body: me },
      'GET /api/balance': { body: emptyBalance },
      'GET /api/movements': { body: { period, items: [] } },
    });
    renderAt('/');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Rango' }));
    await user.type(screen.getByLabelText('Desde'), '2026-10-01');
    await user.type(screen.getByLabelText('Hasta'), '2026-10-15');
    await waitFor(() =>
      expect(
        calls.some(
          (c) =>
            c.path === '/api/balance' && c.search === '?period=range&from=2026-10-01&to=2026-10-15',
        ),
      ).toBe(true),
    );
  });
});

describe('sales', () => {
  it('records a free sale', async () => {
    const calls = mockApi({
      'GET /api/me': { body: me },
      'GET /api/products': { body: products },
      'POST /api/sales': { status: 201, body: { id: 's9' } },
      'GET /api/balance': { body: emptyBalance },
      'GET /api/movements': { body: { period, items: [] } },
    });
    const router = renderAt('/ventas/nueva');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText('Valor'), '15.000');
    await user.type(screen.getByLabelText('Concepto (opcional)'), 'Almuerzo');
    await user.click(screen.getByRole('button', { name: 'Guardar venta' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/'));
    expect(calls.find((c) => c.method === 'POST')?.body).toEqual({
      amount: '15000',
      concept: 'Almuerzo',
      paymentMethod: 'CASH',
    });
  });

  it('records a sale with products and previews the total', async () => {
    const calls = mockApi({
      'GET /api/me': { body: me },
      'GET /api/products': { body: products },
      'POST /api/sales': { status: 201, body: { id: 's9' } },
      'GET /api/balance': { body: emptyBalance },
      'GET /api/movements': { body: { period, items: [] } },
    });
    renderAt('/ventas/nueva');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('radio', { name: 'Con productos' }));
    await user.selectOptions(await screen.findByLabelText('Agregar producto'), 'p1');
    await user.selectOptions(screen.getByLabelText('Agregar producto'), 'p2');
    const quantities = screen.getAllByLabelText('Cantidad');
    await user.clear(quantities[0]!);
    await user.type(quantities[0]!, '2');
    await user.clear(quantities[1]!);
    await user.type(quantities[1]!, '4');
    await user.selectOptions(screen.getByLabelText('Método de pago'), 'NEQUI');
    await user.type(screen.getByLabelText('Fecha'), '2026-10-20');
    expect(screen.getByText('Total: $8.000')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Guardar venta' }));

    await waitFor(() => expect(calls.some((c) => c.method === 'POST')).toBe(true));
    expect(calls.find((c) => c.method === 'POST')?.body).toEqual({
      items: [
        { productId: 'p1', quantity: '2', unitPrice: '3000.00' },
        { productId: 'p2', quantity: '4', unitPrice: '500.00' },
      ],
      paymentMethod: 'NEQUI',
      occurredAt: '2026-10-20T17:00:00.000Z',
    });
  });

  it('shows field errors from the server', async () => {
    mockApi({
      'GET /api/me': { body: me },
      'GET /api/products': { body: [] },
      'POST /api/sales': {
        status: 400,
        body: {
          code: 'validation_failed',
          errors: [{ field: 'occurredAt', code: 'future_date' }],
        },
      },
    });
    renderAt('/ventas/nueva');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText('Valor'), '10');
    await user.click(screen.getByRole('button', { name: 'Guardar venta' }));
    expect(await screen.findByText('La fecha no puede ser futura.')).toBeInTheDocument();
  });

  it('edits and deletes an existing sale', async () => {
    const calls = mockApi({
      'GET /api/me': { body: me },
      'GET /api/products': { body: products },
      'GET /api/sales/s1': {
        body: {
          id: 's1',
          type: 'SALE',
          kind: 'FREE',
          total: '1000.00',
          items: [],
          concept: null,
          paymentMethod: 'CARD',
          occurredAt: '2026-10-20T17:00:00Z',
          note: 'nota',
        },
      },
      'PUT /api/sales/s1': { body: { id: 's1' } },
      'DELETE /api/sales/s1': { status: 204 },
      'GET /api/balance': { body: emptyBalance },
      'GET /api/movements': { body: { period, items: [] } },
    });
    const router = renderAt('/ventas/s1');
    const user = userEvent.setup();
    const amount = await screen.findByLabelText('Valor');
    await waitFor(() => expect(amount).toHaveValue('1000'));
    await user.clear(amount);
    await user.type(amount, '2500');
    await user.click(screen.getByRole('button', { name: 'Guardar venta' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/'));
    expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({
      amount: '2500',
      paymentMethod: 'CARD',
      occurredAt: '2026-10-20T17:00:00Z',
      note: 'nota',
    });

    await router.navigate('/ventas/s1');
    await user.click(await screen.findByRole('button', { name: 'Eliminar' }));
    await user.click(await screen.findByRole('button', { name: 'Sí, eliminar' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'DELETE')).toBe(true));
  });
});

describe('sales with deleted products', () => {
  it('keeps lines of deleted products when editing', async () => {
    const calls = mockApi({
      'GET /api/me': { body: me },
      'GET /api/products': { body: [] },
      'GET /api/sales/s2': {
        body: {
          id: 's2',
          type: 'SALE',
          kind: 'ITEMS',
          total: '6000.00',
          items: [
            {
              productId: null,
              name: 'Gaseosa',
              unitPrice: '3000.00',
              quantity: '2',
              lineTotal: '6000.00',
            },
          ],
          concept: null,
          paymentMethod: 'CASH',
          occurredAt: '2026-10-26T15:00:00Z',
          note: null,
        },
      },
      'PUT /api/sales/s2': { body: { id: 's2' } },
      'GET /api/balance': { body: emptyBalance },
      'GET /api/movements': { body: { period, items: [] } },
    });
    renderAt('/ventas/s2');
    const user = userEvent.setup();
    expect(await screen.findByText('Gaseosa')).toBeInTheDocument();
    expect(screen.getByText('Total: $6.000')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Guardar venta' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'PUT')).toBe(true));
    expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({
      items: [{ name: 'Gaseosa', quantity: '2', unitPrice: '3000.00' }],
      paymentMethod: 'CASH',
      occurredAt: '2026-10-26T15:00:00Z',
    });
  });
});

describe('expenses', () => {
  it('records an expense with category', async () => {
    const calls = mockApi({
      'GET /api/me': { body: me },
      'POST /api/expenses': { status: 201, body: { id: 'e9' } },
      'GET /api/balance': { body: emptyBalance },
      'GET /api/movements': { body: { period, items: [] } },
    });
    const router = renderAt('/gastos/nuevo');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText('Valor'), '50.000');
    await user.selectOptions(screen.getByLabelText('Categoría'), 'RENT');
    await user.type(screen.getByLabelText('Nota (opcional)'), 'Octubre');
    await user.click(screen.getByRole('button', { name: 'Guardar gasto' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/'));
    expect(calls.find((c) => c.method === 'POST')?.body).toEqual({
      amount: '50000',
      category: 'RENT',
      paymentMethod: 'CASH',
      note: 'Octubre',
    });
  });

  it('requires a category before sending', async () => {
    const calls = mockApi({ 'GET /api/me': { body: me } });
    renderAt('/gastos/nuevo');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText('Valor'), '10');
    await user.click(screen.getByRole('button', { name: 'Guardar gasto' }));
    expect(await screen.findByText('Este campo es obligatorio.')).toBeInTheDocument();
    expect(calls.some((c) => c.method === 'POST')).toBe(false);
  });

  it('loads an expense for editing', async () => {
    const calls = mockApi({
      'GET /api/me': { body: me },
      'GET /api/expenses/e1': {
        body: {
          id: 'e1',
          type: 'EXPENSE',
          amount: '300.00',
          category: 'OTHER',
          concept: 'Bolsas',
          paymentMethod: 'CASH',
          occurredAt: '2026-10-26T15:00:00Z',
          note: null,
        },
      },
      'PUT /api/expenses/e1': { body: { id: 'e1' } },
      'GET /api/balance': { body: emptyBalance },
      'GET /api/movements': { body: { period, items: [] } },
    });
    renderAt('/gastos/e1');
    const user = userEvent.setup();
    await waitFor(() => expect(screen.getByLabelText('Concepto (opcional)')).toHaveValue('Bolsas'));
    await user.selectOptions(screen.getByLabelText('Categoría'), 'TRANSPORT');
    await user.click(screen.getByRole('button', { name: 'Guardar gasto' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'PUT')).toBe(true));
    expect(calls.find((c) => c.method === 'PUT')?.body).toMatchObject({
      amount: '300',
      category: 'TRANSPORT',
      concept: 'Bolsas',
    });
  });
});

describe('products', () => {
  it('creates, edits and deletes products', async () => {
    let list = [...products];
    const calls = mockApi({
      'GET /api/me': { body: me },
      'GET /api/products': () => ({ body: list }),
      'POST /api/products': (body) => {
        const created = { id: 'p3', ...(body as { name: string; price: string }) };
        list = [...list, { ...created, price: '1200.00' }];
        return { status: 201, body: created };
      },
      'PUT /api/products/p1': { body: { id: 'p1', name: 'Gaseosa', price: '3500.00' } },
      'DELETE /api/products/p2': () => {
        list = list.filter((p) => p.id !== 'p2');
        return { status: 204 };
      },
    });
    renderAt('/productos');
    const user = userEvent.setup();
    expect(await screen.findByText('Gaseosa')).toBeInTheDocument();

    await user.type(screen.getByLabelText('Nombre'), 'Arepa');
    await user.type(screen.getByLabelText('Precio'), '1.200');
    await user.click(screen.getByRole('button', { name: 'Agregar producto' }));
    expect(await screen.findByText('Arepa')).toBeInTheDocument();
    expect(calls.find((c) => c.method === 'POST')?.body).toEqual({ name: 'Arepa', price: '1200' });

    await user.click(screen.getByRole('button', { name: 'Editar Gaseosa' }));
    const price = screen.getByLabelText('Precio de Gaseosa');
    await user.clear(price);
    await user.type(price, '3500');
    await user.click(screen.getByRole('button', { name: 'Guardar Gaseosa' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'PUT')).toBe(true));

    await user.click(screen.getByRole('button', { name: 'Eliminar Pan' }));
    await user.click(screen.getByRole('button', { name: 'Sí, eliminar' }));
    await waitFor(() => expect(screen.queryByText('Pan')).not.toBeInTheDocument());
  });

  it('explains a duplicated product name', async () => {
    mockApi({
      'GET /api/me': { body: me },
      'GET /api/products': { body: products },
      'POST /api/products': { status: 409, body: { code: 'product_name_taken' } },
    });
    renderAt('/productos');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText('Nombre'), 'pan');
    await user.type(screen.getByLabelText('Precio'), '500');
    await user.click(screen.getByRole('button', { name: 'Agregar producto' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Ya tienes un producto con ese nombre.',
    );
  });
});
