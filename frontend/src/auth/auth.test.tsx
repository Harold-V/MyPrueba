import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { emptyBalance, me, mockApi } from '../test/api-mock';
import { renderAt } from '../test/render';

const home = {
  'GET /api/balance': { body: emptyBalance },
  'GET /api/movements': { body: { period: emptyBalance.period, items: [] } },
};

const anonymous = { 'GET /api/me': { status: 401, body: { code: 'unauthenticated' } } };

describe('authentication', () => {
  it('sends visitors without a session to the login page', async () => {
    mockApi(anonymous);
    const router = renderAt('/');
    expect(await screen.findByRole('heading', { name: 'Entrar' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/entrar');
  });

  it('registers an owner and opens their business', async () => {
    const calls = mockApi({
      ...anonymous,
      'POST /api/auth/register': { status: 201, body: me },
      ...home,
    });
    renderAt('/registro');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText('Email'), 'rosa@example.com');
    await user.type(screen.getByLabelText('Contraseña'), 'secreto123');
    await user.type(screen.getByLabelText('Nombre del negocio'), 'Tienda Doña Rosa');
    await user.selectOptions(screen.getByLabelText('Tipo de negocio (opcional)'), 'GROCERY');
    await user.click(screen.getByRole('button', { name: 'Crear cuenta' }));

    expect(await screen.findByText('Tienda Doña Rosa')).toBeInTheDocument();
    expect(calls.find((c) => c.path === '/api/auth/register')?.body).toEqual({
      email: 'rosa@example.com',
      password: 'secreto123',
      businessName: 'Tienda Doña Rosa',
      businessCategory: 'GROCERY',
    });
  });

  it('shows each invalid field and keeps what was typed', async () => {
    mockApi({
      ...anonymous,
      'POST /api/auth/register': {
        status: 400,
        body: {
          code: 'validation_failed',
          errors: [
            { field: 'password', code: 'too_short' },
            { field: 'businessName', code: 'required' },
          ],
        },
      },
    });
    renderAt('/registro');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText('Email'), 'rosa@example.com');
    await user.type(screen.getByLabelText('Contraseña'), 'corta');
    await user.click(screen.getByRole('button', { name: 'Crear cuenta' }));

    expect(await screen.findByText('Debe tener al menos 8 caracteres.')).toBeInTheDocument();
    expect(screen.getByText('Este campo es obligatorio.')).toBeInTheDocument();
    expect(screen.getByLabelText('Email')).toHaveValue('rosa@example.com');
    expect(screen.getByLabelText('Contraseña')).toHaveAttribute('aria-invalid', 'true');
  });

  it('reports an email already in use', async () => {
    mockApi({
      ...anonymous,
      'POST /api/auth/register': { status: 409, body: { code: 'email_taken' } },
    });
    renderAt('/registro');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText('Email'), 'rosa@example.com');
    await user.type(screen.getByLabelText('Contraseña'), 'secreto123');
    await user.type(screen.getByLabelText('Nombre del negocio'), 'X');
    await user.click(screen.getByRole('button', { name: 'Crear cuenta' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Ya existe una cuenta con este email.',
    );
  });

  it('logs in and shows a single message for wrong credentials', async () => {
    let attempts = 0;
    mockApi({
      ...anonymous,
      'POST /api/auth/login': () =>
        attempts++ === 0
          ? { status: 401, body: { code: 'invalid_credentials' } }
          : { status: 200, body: me },
      ...home,
    });
    renderAt('/entrar');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText('Email'), 'rosa@example.com');
    await user.type(screen.getByLabelText('Contraseña'), 'mala');
    await user.click(screen.getByRole('button', { name: 'Entrar' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Email o contraseña incorrectos.');

    await user.clear(screen.getByLabelText('Contraseña'));
    await user.type(screen.getByLabelText('Contraseña'), 'secreto123');
    await user.click(screen.getByRole('button', { name: 'Entrar' }));
    expect(await screen.findByText('Tienda Doña Rosa')).toBeInTheDocument();
  });

  it('edits the business and logs out', async () => {
    const calls = mockApi({
      'GET /api/me': { body: me },
      'PUT /api/business': (body) => ({ body: { ...me.business, ...(body as object) } }),
      'POST /api/auth/logout': { status: 204 },
    });
    const router = renderAt('/negocio');
    const user = userEvent.setup();
    const name = await screen.findByLabelText('Nombre del negocio');
    await user.clear(name);
    await user.type(name, 'Rosa Express');
    await user.click(screen.getByRole('button', { name: 'Guardar' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Cambios guardados.');
    expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({
      name: 'Rosa Express',
      category: 'GROCERY',
    });

    await user.click(screen.getByRole('button', { name: 'Cerrar sesión' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/entrar'));
  });
});
