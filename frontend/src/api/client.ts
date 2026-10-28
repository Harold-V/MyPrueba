export interface FieldError {
  field: string;
  code: string;
}

/** A problem response from the API (RFC 9457) with its stable `code`. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly errors: FieldError[];
  /** Extra problem fields the API adds for some codes (e.g. productName for insufficient_stock). */
  readonly details: Record<string, unknown>;

  constructor(
    status: number,
    code: string,
    errors: FieldError[] = [],
    details: Record<string, unknown> = {},
  ) {
    super(code);
    this.status = status;
    this.code = code;
    this.errors = errors;
    this.details = details;
  }

  /** Field errors as `{ field: code }`. */
  fieldErrors(): Record<string, string> {
    return Object.fromEntries(this.errors.map((e) => [e.field, e.code]));
  }
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, {
      method,
      credentials: 'same-origin',
      headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, 'network_error');
  }
  if (response.status === 204) {
    return undefined as T;
  }
  const data: unknown = await response.json().catch(() => ({}));
  if (!response.ok) {
    const { code, errors, ...details } = data as {
      code?: string;
      errors?: FieldError[];
    } & Record<string, unknown>;
    throw new ApiError(response.status, code ?? 'unknown_error', errors ?? [], details);
  }
  return data as T;
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
  put: <T>(path: string, body: unknown) => request<T>('PUT', path, body),
  delete: (path: string) => request<void>('DELETE', path),
};

/** Drops empty optional fields so the API receives only what the user filled in. */
export function compact<T extends Record<string, unknown>>(body: T): Partial<T> {
  return Object.fromEntries(
    Object.entries(body).filter(
      ([, value]) => value !== undefined && value !== null && value !== '',
    ),
  ) as Partial<T>;
}
