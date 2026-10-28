import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { emptyBalance, me, mockApi } from '../test/api-mock';
import { renderAt } from '../test/render';

const gaseosa = {
  productId: 'p1',
  name: 'Gaseosa',
  price: '3000.00',
  cost: '2500.00',
  tracked: true,
  stock: '3',
  minStock: '5',
  low: true,
};
const pan = {
  productId: 'p2',
  name: 'Pan',
  price: '500.00',
  cost: null,
  tracked: false,
  stock: null,
  minStock: null,
  low: false,
};
const inventory = {
  summary: { tracked: 1, lowStock: 1, value: '7500.00' },
  items: [gaseosa, pan],
};

describe('inventory page', () => {
  it('shows the summary, stock levels and low-stock filter', async () => {
    const calls = mockApi({
      'GET /api/me': { body: me },
      'GET /api/inventory': (_, url) => ({
        body:
          url.searchParams.get('lowOnly') === 'true'
            ? { ...inventory, items: [gaseosa] }
            : inventory,
      }),
    });
    renderAt('/inventario');
    const summary = await screen.findByRole('region', { name: 'Resumen de inventario' });
    expect(await within(summary).findByText('$7.500')).toBeInTheDocument();
    expect(within(summary).getByText('Stock bajo').nextSibling).toHaveTextContent('1');
    const row = screen.getByRole('listitem', { name: 'Gaseosa' });
    expect(within(row).getByText('3 en stock')).toBeInTheDocument();
    expect(within(row).getByText('Stock bajo')).toBeInTheDocument();
    expect(
      within(screen.getByRole('listitem', { name: 'Pan' })).getByText('Sin control de inventario'),
    ).toBeInTheDocument();

    const user = userEvent.setup();
    await user.click(screen.getByRole('checkbox', { name: 'Solo stock bajo' }));
    await waitFor(() =>
      expect(screen.queryByRole('listitem', { name: 'Pan' })).not.toBeInTheDocument(),
    );
    expect(calls.some((c) => c.search === '?lowOnly=true')).toBe(true);
  });

  it('activates stock control with the current stock', async () => {
    const calls = mockApi({
      'GET /api/me': { body: me },
      'GET /api/inventory': { body: inventory },
      'PUT /api/inventory/p2': () => ({
        body: { ...pan, tracked: true, stock: '20', minStock: '5' },
      }),
    });
    renderAt('/inventario');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Activar inventario de Pan' }));
    await user.type(screen.getByLabelText('Stock actual'), '20');
    await user.type(screen.getByLabelText('Stock mínimo (opcional)'), '5');
    await user.click(screen.getByRole('button', { name: 'Activar' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'PUT')).toBe(true));
    expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({
      tracked: true,
      initialStock: '20',
      minStock: '5',
    });
  });

  it('records an adjustment and shows field errors', async () => {
    let attempt = 0;
    const calls = mockApi({
      'GET /api/me': { body: me },
      'GET /api/inventory': { body: inventory },
      'POST /api/inventory/p1/adjustments': () =>
        attempt++ === 0
          ? {
              status: 400,
              body: {
                code: 'validation_failed',
                errors: [{ field: 'quantity', code: 'must_be_positive' }],
              },
            }
          : { status: 201, body: { ...gaseosa, stock: '15', low: false } },
    });
    renderAt('/inventario');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Ajustar Gaseosa' }));
    await user.selectOptions(screen.getByLabelText('Tipo de ajuste'), 'PURCHASE');
    await user.type(screen.getByLabelText('Cantidad'), '0');
    await user.click(screen.getByRole('button', { name: 'Registrar ajuste' }));
    expect(await screen.findByText('Debe ser mayor que cero.')).toBeInTheDocument();

    await user.clear(screen.getByLabelText('Cantidad'));
    await user.type(screen.getByLabelText('Cantidad'), '12');
    await user.type(screen.getByLabelText('Nota (opcional)'), 'Pedido del lunes');
    await user.click(screen.getByRole('button', { name: 'Registrar ajuste' }));
    expect(await screen.findByText('15 en stock')).toBeInTheDocument();
    expect(calls.filter((c) => c.method === 'POST').at(-1)?.body).toEqual({
      type: 'PURCHASE',
      quantity: '12',
      note: 'Pedido del lunes',
    });
  });

  it('shows the stock history of a product', async () => {
    mockApi({
      'GET /api/me': { body: me },
      'GET /api/inventory': { body: inventory },
      'GET /api/inventory/p1/movements': {
        body: [
          {
            type: 'SALE',
            change: '-2',
            stockAfter: '3',
            note: null,
            occurredAt: '2026-10-26T15:00:00Z',
          },
          {
            type: 'INITIAL',
            change: '5',
            stockAfter: '5',
            note: null,
            occurredAt: '2026-10-26T14:00:00Z',
          },
        ],
      },
    });
    renderAt('/inventario');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Historial de Gaseosa' }));
    const history = await screen.findByRole('list', { name: 'Historial de Gaseosa' });
    expect(within(history).getByText('Venta')).toBeInTheDocument();
    expect(within(history).getByText('−2 → 3')).toBeInTheDocument();
    expect(within(history).getByText('Stock inicial')).toBeInTheDocument();
  });
});

describe('inventory in other screens', () => {
  it('warns on the home page when products are low', async () => {
    mockApi({
      'GET /api/me': { body: me },
      'GET /api/balance': { body: emptyBalance },
      'GET /api/movements': { body: { period: emptyBalance.period, items: [] } },
      'GET /api/inventory': { body: inventory },
    });
    renderAt('/');
    const banner = await screen.findByRole('link', { name: /1 producto con stock bajo/ });
    expect(banner).toHaveAttribute('href', '/inventario');
  });

  it('shows available stock when selling and warns when it is not enough', async () => {
    mockApi({
      'GET /api/me': { body: me },
      'GET /api/products': {
        body: [
          { id: 'p1', name: 'Gaseosa', price: '3000.00', cost: '2500.00' },
          { id: 'p2', name: 'Pan', price: '500.00', cost: null },
        ],
      },
      'GET /api/inventory': { body: inventory },
    });
    renderAt('/ventas/nueva');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('radio', { name: 'Con productos' }));
    const select = await screen.findByLabelText('Agregar producto');
    await waitFor(() =>
      expect(
        within(select).getByRole('option', { name: 'Gaseosa — $3.000 · 3 disponibles' }),
      ).toBeInTheDocument(),
    );
    await user.selectOptions(select, 'p1');
    const quantity = screen.getByLabelText('Cantidad');
    await user.clear(quantity);
    await user.type(quantity, '4');
    expect(
      screen.getByText('Solo hay 3 en inventario. La venta se registrará igual.'),
    ).toBeInTheDocument();
  });

  it('saves an optional product cost', async () => {
    const calls = mockApi({
      'GET /api/me': { body: me },
      'GET /api/products': { body: [] },
      'POST /api/products': { status: 201, body: { id: 'p9' } },
    });
    renderAt('/productos');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText('Nombre'), 'Arroz');
    await user.type(screen.getByLabelText('Precio'), '4.200');
    await user.type(screen.getByLabelText('Costo (opcional)'), '3.000');
    await user.click(screen.getByRole('button', { name: 'Agregar producto' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'POST')).toBe(true));
    expect(calls.find((c) => c.method === 'POST')?.body).toEqual({
      name: 'Arroz',
      price: '4200',
      cost: '3000',
    });
  });
});
