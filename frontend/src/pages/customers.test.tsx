import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { emptyBalance, me, mockApi } from '../test/api-mock';
import { renderAt } from '../test/render';

const juan = { id: 'c1', name: 'Juan', phone: '300 123 4567', note: null };
const ana = { id: 'c2', name: 'Ana', phone: null, note: null };
const period = { from: '2026-10-26', to: '2026-10-26' };

const account = {
  customer: { id: 'c1', name: 'Juan', phone: '300 123 4567' },
  debt: '15000.00',
  entries: [
    {
      id: 'pay1',
      type: 'PAYMENT',
      amount: '5000.00',
      description: null,
      occurredAt: '2026-10-26T16:00:00Z',
    },
    {
      id: 's1',
      type: 'SALE',
      amount: '20000.00',
      description: 'Mercado',
      occurredAt: '2026-10-26T15:00:00Z',
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

describe('customers and debts', () => {
  it('lists customers with their debt and the total receivable', async () => {
    mockApi({
      'GET /api/me': { body: me },
      'GET /api/customers': { body: [ana, juan] },
      'GET /api/receivables': {
        body: {
          total: '15000.00',
          customers: [{ customerId: 'c1', name: 'Juan', phone: '300 123 4567', debt: '15000.00' }],
        },
      },
    });
    renderAt('/clientes');
    const summary = await screen.findByRole('region', { name: 'Por cobrar' });
    expect(await within(summary).findByText('$15.000')).toBeInTheDocument();
    const row = await screen.findByRole('listitem', { name: 'Juan' });
    expect(within(row).getByText('Debe $15.000')).toBeInTheDocument();
    expect(within(row).getByRole('link', { name: 'Ver cuenta de Juan' })).toHaveAttribute(
      'href',
      '/clientes/c1',
    );
    expect(
      within(screen.getByRole('listitem', { name: 'Ana' })).getByText('Al día'),
    ).toBeInTheDocument();
  });

  it('creates a customer', async () => {
    const calls = mockApi({
      'GET /api/me': { body: me },
      'GET /api/customers': { body: [] },
      'GET /api/receivables': { body: { total: '0.00', customers: [] } },
      'POST /api/customers': { status: 201, body: { id: 'c9', name: 'Pedro' } },
    });
    renderAt('/clientes');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText('Nombre'), 'Pedro');
    await user.type(screen.getByLabelText('Teléfono (opcional)'), '311 000 0000');
    await user.click(screen.getByRole('button', { name: 'Agregar cliente' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'POST')).toBe(true));
    expect(calls.find((c) => c.method === 'POST')?.body).toEqual({
      name: 'Pedro',
      phone: '311 000 0000',
    });
  });

  it('shows the account and registers a payment', async () => {
    let attempt = 0;
    const calls = mockApi({
      'GET /api/me': { body: me },
      'GET /api/customers': { body: [juan] },
      'GET /api/customers/c1/account': { body: account },
      'POST /api/customer-payments': () =>
        attempt++ === 0
          ? {
              status: 400,
              body: {
                code: 'validation_failed',
                errors: [{ field: 'amount', code: 'exceeds_debt' }],
              },
            }
          : { status: 201, body: { id: 'pay2' } },
    });
    renderAt('/clientes/c1');
    expect(await screen.findByRole('heading', { name: 'Juan' })).toBeInTheDocument();
    expect(screen.getByText('Debe $15.000')).toBeInTheDocument();
    const history = screen.getByRole('list', { name: 'Fiados y abonos' });
    expect(within(history).getByText('Abono')).toBeInTheDocument();
    expect(within(history).getByText('Fiado: Mercado')).toBeInTheDocument();

    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Valor del abono'), '16.000');
    await user.click(screen.getByRole('button', { name: 'Registrar abono' }));
    expect(await screen.findByText('El abono no puede superar la deuda.')).toBeInTheDocument();

    await user.clear(screen.getByLabelText('Valor del abono'));
    await user.type(screen.getByLabelText('Valor del abono'), '5.000');
    await user.selectOptions(screen.getByLabelText('Método de pago'), 'TRANSFER');
    await user.click(screen.getByRole('button', { name: 'Registrar abono' }));
    await waitFor(() => expect(calls.filter((c) => c.method === 'POST')).toHaveLength(2));
    expect(calls.filter((c) => c.method === 'POST').at(-1)?.body).toEqual({
      customerId: 'c1',
      amount: '5000',
      paymentMethod: 'TRANSFER',
    });
    expect(
      within(screen.getByLabelText('Método de pago')).queryByRole('option', { name: 'Fiado' }),
    ).not.toBeInTheDocument();
  });

  it('explains why a customer with debt cannot be deleted', async () => {
    mockApi({
      'GET /api/me': { body: me },
      'GET /api/customers': { body: [juan] },
      'GET /api/customers/c1/account': { body: account },
      'DELETE /api/customers/c1': { status: 409, body: { code: 'customer_has_debt' } },
    });
    renderAt('/clientes/c1');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Eliminar cliente' }));
    await user.click(screen.getByRole('button', { name: 'Sí, eliminar' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No puedes eliminar un cliente con saldo pendiente.',
    );
  });

  it('edits the customer data', async () => {
    const calls = mockApi({
      'GET /api/me': { body: me },
      'GET /api/customers': { body: [juan] },
      'GET /api/customers/c1/account': { body: account },
      'PUT /api/customers/c1': { body: { ...juan, name: 'Juan Pérez' } },
    });
    renderAt('/clientes/c1');
    const user = userEvent.setup();
    const name = await screen.findByLabelText('Nombre');
    await waitFor(() => expect(name).toHaveValue('Juan'));
    await user.clear(name);
    await user.type(name, 'Juan Pérez');
    await user.click(screen.getByRole('button', { name: 'Guardar cliente' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'PUT')).toBe(true));
    expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({
      name: 'Juan Pérez',
      phone: '300 123 4567',
    });
  });
});

describe('credit in other screens', () => {
  it('records a credit sale to a customer', async () => {
    const calls = mockApi({
      'GET /api/me': { body: me },
      'GET /api/products': { body: [] },
      'GET /api/customers': { body: [ana, juan] },
      'POST /api/sales': { status: 201, body: { id: 's9' } },
      'GET /api/balance': { body: emptyBalance },
      'GET /api/movements': { body: { period, items: [] } },
    });
    const router = renderAt('/ventas/nueva');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText('Valor'), '20.000');
    await user.selectOptions(screen.getByLabelText('Método de pago'), 'CREDIT');
    await user.click(screen.getByRole('button', { name: 'Guardar venta' }));
    expect(await screen.findByText('Este campo es obligatorio.')).toBeInTheDocument();
    expect(calls.some((c) => c.method === 'POST')).toBe(false);

    await user.selectOptions(screen.getByLabelText('Cliente'), 'c1');
    await user.click(screen.getByRole('button', { name: 'Guardar venta' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/'));
    expect(calls.find((c) => c.method === 'POST')?.body).toEqual({
      amount: '20000',
      paymentMethod: 'CREDIT',
      customerId: 'c1',
    });
  });

  it('does not offer credit for expenses', async () => {
    mockApi({ 'GET /api/me': { body: me } });
    renderAt('/gastos/nuevo');
    const method = await screen.findByLabelText('Método de pago');
    expect(within(method).queryByRole('option', { name: 'Fiado' })).not.toBeInTheDocument();
  });

  it('shows credit sales and payments in the movements and the receivable total', async () => {
    mockApi({
      'GET /api/me': { body: me },
      'GET /api/balance': {
        body: {
          period,
          sales: '0.00',
          payments: '5000.00',
          expenses: '0.00',
          balance: '5000.00',
          receivable: '15000.00',
        },
      },
      'GET /api/movements': {
        body: {
          period,
          items: [
            {
              id: 'pay1',
              type: 'PAYMENT',
              amount: '5000.00',
              description: null,
              paymentMethod: 'CASH',
              occurredAt: '2026-10-26T16:00:00Z',
              category: null,
              customerName: 'Juan',
            },
            {
              id: 's1',
              type: 'SALE',
              amount: '20000.00',
              description: null,
              paymentMethod: 'CREDIT',
              occurredAt: '2026-10-26T15:00:00Z',
              category: null,
              customerName: 'Juan',
            },
          ],
        },
      },
    });
    renderAt('/');
    expect(await screen.findByText('Abono de Juan')).toBeInTheDocument();
    expect(screen.getByText('Venta fiada a Juan')).toBeInTheDocument();
    const summary = screen.getByRole('region', { name: 'Resumen' });
    expect(
      await within(summary).findByText('Abonos: $5.000 · Por cobrar: $15.000'),
    ).toBeInTheDocument();
  });
});

describe('payment page', () => {
  it('edits and deletes a payment, then returns to the customer', async () => {
    const calls = mockApi({
      'GET /api/me': { body: me },
      'GET /api/customer-payments/pay1': {
        body: {
          id: 'pay1',
          type: 'PAYMENT',
          customerId: 'c1',
          customerName: 'Juan',
          amount: '5000.00',
          paymentMethod: 'CASH',
          occurredAt: '2026-10-26T16:00:00Z',
          note: null,
        },
      },
      'PUT /api/customer-payments/pay1': { body: { id: 'pay1' } },
      'DELETE /api/customer-payments/pay1': { status: 204 },
      'GET /api/customers': { body: [juan] },
      'GET /api/customers/c1/account': { body: account },
    });
    const router = renderAt('/abonos/pay1');
    const user = userEvent.setup();
    expect(await screen.findByRole('heading', { name: 'Abono de Juan' })).toBeInTheDocument();
    const amount = screen.getByLabelText('Valor del abono');
    await user.clear(amount);
    await user.type(amount, '6000');
    await user.click(screen.getByRole('button', { name: 'Guardar abono' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/clientes/c1'));
    expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({
      customerId: 'c1',
      amount: '6000',
      paymentMethod: 'CASH',
      occurredAt: '2026-10-26T16:00:00Z',
    });

    await router.navigate('/abonos/pay1');
    await user.click(await screen.findByRole('button', { name: 'Eliminar' }));
    await user.click(await screen.findByRole('button', { name: 'Sí, eliminar' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'DELETE')).toBe(true));
  });
});
