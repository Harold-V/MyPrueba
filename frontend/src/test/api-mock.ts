import { vi } from 'vitest';

export type MockResponse = { status?: number; body?: unknown };
type Handler = MockResponse | ((body: unknown, url: URL) => MockResponse);

export type Call = { method: string; path: string; search: string; body: unknown };

/** Replaces fetch with handlers keyed by "METHOD /path" (query string ignored for matching). */
export function mockApi(handlers: Record<string, Handler>) {
  const calls: Call[] = [];
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input), 'http://localhost');
    const method = (init?.method ?? 'GET').toUpperCase();
    const body = init?.body ? JSON.parse(String(init.body)) : undefined;
    calls.push({ method, path: url.pathname, search: url.search, body });
    const handler = handlers[`${method} ${url.pathname}`];
    if (!handler) {
      return new Response(JSON.stringify({ code: 'not_mocked' }), { status: 500 });
    }
    const { status = 200, body: responseBody } =
      typeof handler === 'function' ? handler(body, url) : handler;
    return new Response(status === 204 ? null : JSON.stringify(responseBody ?? {}), {
      status,
      headers: { 'Content-Type': 'application/json' },
    });
  });
  vi.stubGlobal('fetch', fetchMock);
  return calls;
}

export const business = {
  id: 'b1',
  name: 'Tienda Doña Rosa',
  category: 'GROCERY',
  currency: 'COP',
  timeZone: 'America/Bogota',
};

export const me = { user: { id: 'u1', email: 'rosa@example.com' }, business };

export const emptyBalance = {
  period: { from: '2026-10-26', to: '2026-10-26' },
  sales: '0.00',
  expenses: '0.00',
  balance: '0.00',
};
