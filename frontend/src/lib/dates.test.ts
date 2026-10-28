import { describe, expect, it } from 'vitest';
import { localDateOf, localDateToInstant, todayIn } from './dates';

const BOGOTA = 'America/Bogota';

describe('dates', () => {
  it('converts a local date to noon in the business time zone', () => {
    expect(localDateToInstant('2026-10-20', BOGOTA)).toBe('2026-10-20T17:00:00.000Z');
  });

  it('reads the local date of an instant', () => {
    expect(localDateOf('2026-10-26T04:30:00Z', BOGOTA)).toBe('2026-10-25');
  });

  it('knows today in the business time zone', () => {
    expect(todayIn(BOGOTA, new Date('2026-10-26T03:00:00Z'))).toBe('2026-10-25');
  });
});
