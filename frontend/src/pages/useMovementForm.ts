import { useState } from 'react';
import { useNavigate } from 'react-router';
import { api, ApiError } from '../api/client';
import { formMessage } from '../i18n/es';
import { localDateOf, localDateToInstant } from '../lib/dates';

/** Shared state and behaviour of the sale and expense forms. */
export function useMovementForm(resource: '/api/sales' | '/api/expenses', id: string | undefined) {
  const navigate = useNavigate();
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [alert, setAlert] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  function fail(error: unknown) {
    const apiError = error instanceof ApiError ? error : new ApiError(0, 'unknown_error');
    setErrors(apiError.fieldErrors());
    setAlert(formMessage(apiError.code));
  }

  async function save(body: Record<string, unknown>, clientErrors: Record<string, string>) {
    if (Object.keys(clientErrors).length > 0) {
      setErrors(clientErrors);
      setAlert(formMessage('validation_failed'));
      return;
    }
    setBusy(true);
    try {
      if (id) await api.put(`${resource}/${id}`, body);
      else await api.post(resource, body);
      navigate('/');
    } catch (error) {
      fail(error);
    } finally {
      setBusy(false);
    }
  }

  async function remove() {
    try {
      await api.delete(`${resource}/${id}`);
      navigate('/');
    } catch (error) {
      fail(error);
    }
  }

  return { errors, alert, busy, save, remove, fail };
}

/**
 * The date field shows a local date. An untouched date keeps the original instant (or "now" for
 * a new record, by omitting it); a changed date becomes noon of that day in the business zone.
 */
export function occurredAtFor(
  date: string,
  original: string | null,
  timeZone: string,
): string | undefined {
  if (!date) return original ?? undefined;
  if (original && localDateOf(original, timeZone) === date) return original;
  return localDateToInstant(date, timeZone);
}
