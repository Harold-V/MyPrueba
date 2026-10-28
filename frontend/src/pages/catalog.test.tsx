import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { me, mockApi } from '../test/api-mock';
import { renderAt } from '../test/render';

const bebidas = { id: 'c1', name: 'Bebidas' };
const aseo = { id: 'c2', name: 'Aseo' };
const gaseosa = {
  id: 'p1',
  name: 'Gaseosa',
  price: '3000.00',
  cost: '2000.00',
  categoryId: 'c1',
  categoryName: 'Bebidas',
  barcode: '7702004003508',
  groupId: null,
  variant: null,
};
const jabon = {
  id: 'p2',
  name: 'Jabón',
  price: '2500.00',
  cost: null,
  categoryId: 'c2',
  categoryName: 'Aseo',
  barcode: null,
  groupId: null,
  variant: null,
};
const camisetaM = {
  id: 'p3',
  name: 'Camiseta — M',
  price: '25000.00',
  cost: null,
  categoryId: null,
  categoryName: null,
  barcode: null,
  groupId: 'g1',
  variant: 'M',
};
const camisetaL = { ...camisetaM, id: 'p4', name: 'Camiseta — L', variant: 'L' };
const catalog = {
  'GET /api/me': { body: me },
  'GET /api/products': { body: [camisetaL, camisetaM, gaseosa, jabon] },
  'GET /api/product-categories': { body: [aseo, bebidas] },
};

describe('product catalog', () => {
  it('searches by name or code and filters by category', async () => {
    mockApi(catalog);
    renderAt('/productos');
    const user = userEvent.setup();
    const list = await screen.findByRole('list', { name: 'Productos' });
    expect(await within(list).findByText('Gaseosa')).toBeInTheDocument();
    expect(within(list).getByText(/Bebidas · 7702004003508/)).toBeInTheDocument();

    await user.type(screen.getByLabelText('Buscar por nombre o código'), '770200');
    expect(within(list).queryByText('Jabón')).not.toBeInTheDocument();
    expect(within(list).getByText('Gaseosa')).toBeInTheDocument();

    await user.clear(screen.getByLabelText('Buscar por nombre o código'));
    await user.type(screen.getByLabelText('Buscar por nombre o código'), 'jabon');
    expect(within(list).getByText('Jabón')).toBeInTheDocument();
    expect(within(list).queryByText('Gaseosa')).not.toBeInTheDocument();

    await user.clear(screen.getByLabelText('Buscar por nombre o código'));
    await user.selectOptions(screen.getByLabelText('Filtrar por categoría'), 'c1');
    expect(within(list).getByText('Gaseosa')).toBeInTheDocument();
    expect(within(list).queryByText('Jabón')).not.toBeInTheDocument();
  });

  it('creates a product with category and barcode', async () => {
    const calls = mockApi({ ...catalog, 'POST /api/products': { status: 201, body: gaseosa } });
    renderAt('/productos');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText('Nombre'), 'Agua');
    await user.type(screen.getByLabelText('Precio'), '1.500');
    await user.selectOptions(screen.getByLabelText('Categoría'), 'c1');
    await user.type(screen.getByLabelText('Código de barras (opcional)'), '7702001');
    await user.click(screen.getByRole('button', { name: 'Agregar producto' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'POST')).toBe(true));
    expect(calls.find((c) => c.method === 'POST')?.body).toEqual({
      name: 'Agua',
      price: '1500',
      categoryId: 'c1',
      barcode: '7702001',
    });
    // The next product starts clean: nothing carries over unseen.
    await waitFor(() => expect(screen.getByLabelText('Categoría')).toHaveValue(''));
    expect(screen.getByLabelText('Código de barras (opcional)')).toHaveValue('');
  });

  it('creates a product with variants', async () => {
    const calls = mockApi({
      ...catalog,
      'POST /api/product-groups': { status: 201, body: { id: 'g2', name: 'Jean', variants: [] } },
    });
    renderAt('/productos');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('radio', { name: 'Con variantes' }));
    await user.type(screen.getByLabelText('Nombre'), 'Jean');
    await user.type(screen.getByLabelText('Precio'), '80.000');
    await user.type(screen.getByLabelText('Variante 1'), '30');
    await user.type(screen.getByLabelText('Stock de la variante 1'), '4');
    await user.click(screen.getByRole('button', { name: 'Agregar variante' }));
    await user.type(screen.getByLabelText('Variante 2'), '32');
    await user.type(screen.getByLabelText('Precio de la variante 2 (opcional)'), '85.000');
    await user.click(screen.getByRole('button', { name: 'Agregar producto' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'POST')).toBe(true));
    expect(calls.find((c) => c.method === 'POST')?.body).toEqual({
      name: 'Jean',
      price: '80000',
      variants: [
        { name: '30', stock: '4' },
        { name: '32', price: '85000' },
      ],
    });
  });

  it('shows variants together and manages them as one product', async () => {
    const calls = mockApi({
      ...catalog,
      'PUT /api/product-groups/g1': { body: { id: 'g1', name: 'Polo', variants: [] } },
      'POST /api/product-groups/g1/variants': { status: 201, body: camisetaM },
      'DELETE /api/product-groups/g1': { status: 204 },
    });
    renderAt('/productos');
    const user = userEvent.setup();
    const group = await screen.findByRole('group', { name: 'Camiseta' });
    expect(within(group).getByText('M')).toBeInTheDocument();
    expect(within(group).getByText('L')).toBeInTheDocument();

    await user.click(within(group).getByRole('button', { name: 'Agregar variante a Camiseta' }));
    await user.type(within(group).getByLabelText('Nueva variante'), 'XL');
    // The price starts as the product's; this variant costs more.
    await user.clear(within(group).getByLabelText('Precio de XL'));
    await user.type(within(group).getByLabelText('Precio de XL'), '29.000');
    await user.click(within(group).getByRole('button', { name: 'Crear variante' }));
    await waitFor(() =>
      expect(calls.find((c) => c.path === '/api/product-groups/g1/variants')?.body).toEqual({
        name: 'XL',
        price: '29000',
      }),
    );

    await user.click(within(group).getByRole('button', { name: 'Renombrar Camiseta' }));
    const name = within(group).getByLabelText('Nombre de Camiseta');
    await user.clear(name);
    await user.type(name, 'Polo');
    await user.click(within(group).getByRole('button', { name: 'Guardar nombre' }));
    await waitFor(() =>
      expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({ name: 'Polo' }),
    );

    await user.click(
      within(group).getByRole('button', { name: 'Eliminar Camiseta y sus variantes' }),
    );
    await user.click(within(group).getByRole('button', { name: 'Sí, eliminar' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'DELETE')).toBe(true));
  });

  it('manages categories', async () => {
    const calls = mockApi({
      ...catalog,
      'POST /api/product-categories': { status: 201, body: { id: 'c3', name: 'Lácteos' } },
      'PUT /api/product-categories/c2': { body: { id: 'c2', name: 'Aseo personal' } },
      'DELETE /api/product-categories/c1': { status: 204 },
    });
    renderAt('/productos');
    const user = userEvent.setup();
    const panel = await screen.findByRole('region', { name: 'Categorías' });
    await user.type(within(panel).getByLabelText('Nueva categoría'), 'Lácteos');
    await user.click(within(panel).getByRole('button', { name: 'Crear categoría' }));
    await waitFor(() =>
      expect(calls.find((c) => c.method === 'POST')?.body).toEqual({ name: 'Lácteos' }),
    );

    await user.click(within(panel).getByRole('button', { name: 'Renombrar Aseo' }));
    const input = within(panel).getByLabelText('Nombre de Aseo');
    await user.clear(input);
    await user.type(input, 'Aseo personal');
    await user.click(within(panel).getByRole('button', { name: 'Guardar Aseo' }));
    await waitFor(() =>
      expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({ name: 'Aseo personal' }),
    );

    await user.click(within(panel).getByRole('button', { name: 'Eliminar Bebidas' }));
    await user.click(within(panel).getByRole('button', { name: 'Sí, eliminar' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'DELETE')).toBe(true));
  });

  it('uploads products from a file and reports row errors', async () => {
    let attempt = 0;
    const calls = mockApi({
      ...catalog,
      'POST /api/products/import': () =>
        attempt++ === 0
          ? {
              status: 400,
              body: {
                code: 'validation_failed',
                errors: [
                  { field: 'rows[3].price', code: 'invalid_format' },
                  { field: 'rows[5].barcode', code: 'barcode_taken' },
                ],
              },
            }
          : { body: { created: 3 } },
    });
    renderAt('/productos');
    const user = userEvent.setup();
    const panel = await screen.findByRole('region', { name: 'Cargar desde Excel' });
    expect(within(panel).getByRole('link', { name: 'Descargar plantilla' })).toHaveAttribute(
      'download',
      'plantilla-productos.csv',
    );
    const file = new File(['Nombre;Precio\nPan;500'], 'productos.csv', { type: 'text/csv' });
    await user.upload(within(panel).getByLabelText('Archivo .xlsx o .csv'), file);
    await user.click(within(panel).getByRole('button', { name: 'Cargar productos' }));
    const errors = await within(panel).findByRole('list', { name: 'Errores del archivo' });
    expect(
      within(errors).getByText('Fila 3 · Precio: El formato no es válido.'),
    ).toBeInTheDocument();
    expect(
      within(errors).getByText('Fila 5 · Código de barras: Ya tienes un producto con ese código.'),
    ).toBeInTheDocument();
    const upload = calls.find((c) => c.path === '/api/products/import');
    expect(upload?.body).toBe(file);

    await user.click(within(panel).getByRole('button', { name: 'Cargar productos' }));
    expect(await within(panel).findByRole('status')).toHaveTextContent('3 productos cargados.');
  });
});

describe('finding products when selling', () => {
  it('adds a product by typing its barcode', async () => {
    mockApi({
      ...catalog,
      'GET /api/customers': { body: [] },
      'GET /api/inventory': {
        body: { summary: { references: 0, lowStock: 0, value: '0' }, items: [] },
      },
    });
    renderAt('/ventas/nueva');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('radio', { name: 'Con productos' }));
    const search = await screen.findByLabelText('Buscar producto (nombre o código)');
    await user.type(search, 'jab');
    const select = screen.getByLabelText('Agregar producto');
    expect(
      within(select)
        .getAllByRole('option')
        .map((o) => o.textContent),
    ).toEqual(['Elige un producto', 'Jabón — $2.500']);
    await user.clear(search);
    await user.type(search, '7702004003508{Enter}');
    expect(await screen.findByLabelText('Cantidad')).toHaveValue('1');
    expect(screen.getByText('Total: $3.000')).toBeInTheDocument();
    expect(search).toHaveValue('');
  });
});

describe('inventory filters', () => {
  it('filters the inventory by category and text', async () => {
    const calls = mockApi({
      ...catalog,
      'GET /api/inventory': {
        body: { summary: { references: 4, lowStock: 0, value: '0.00' }, items: [] },
      },
    });
    renderAt('/inventario');
    const user = userEvent.setup();
    await screen.findByRole('option', { name: 'Bebidas' });
    await user.selectOptions(screen.getByLabelText('Filtrar por categoría'), 'c1');
    await waitFor(() => expect(calls.some((c) => c.search === '?categoryId=c1')).toBe(true));
    await user.type(screen.getByLabelText('Buscar por nombre o código'), 'gas');
    await waitFor(() => expect(calls.some((c) => c.search === '?categoryId=c1&q=gas')).toBe(true));
  });
});
