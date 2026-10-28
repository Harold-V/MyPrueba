// The business works in its own time zone; the API speaks in instants.

/** YYYY-MM-DD of `instant` as seen in `timeZone`. */
export function localDateOf(instant: string | Date, timeZone: string): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(typeof instant === 'string' ? new Date(instant) : instant);
}

export function todayIn(timeZone: string, now: Date = new Date()): string {
  return localDateOf(now, timeZone);
}

/** Noon of a local date in `timeZone`, as an ISO instant (noon avoids any date-line ambiguity). */
export function localDateToInstant(date: string, timeZone: string): string {
  const guess = new Date(`${date}T12:00:00Z`);
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone,
    hourCycle: 'h23',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).formatToParts(guess);
  const get = (type: string) => Number(parts.find((p) => p.type === type)?.value);
  const asLocal = Date.UTC(get('year'), get('month') - 1, get('day'), get('hour'), get('minute'));
  const offset = asLocal - guess.getTime();
  return new Date(guess.getTime() - offset).toISOString();
}

/** "20 de oct de 2026" in the business time zone. */
export function formatDate(instant: string, timeZone: string): string {
  return new Intl.DateTimeFormat('es-CO', {
    timeZone,
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(new Date(instant));
}

export function formatDateTime(instant: string, timeZone: string): string {
  return new Intl.DateTimeFormat('es-CO', {
    timeZone,
    day: 'numeric',
    month: 'short',
    hour: 'numeric',
    minute: '2-digit',
  }).format(new Date(instant));
}
