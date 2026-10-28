import { describe, expect, it } from 'vitest';
import { normalizeQuantity, signed, toMilli } from './quantity';

describe('quantity', () => {
  it('normalizes the decimal comma', () => {
    expect(normalizeQuantity(' 2,5 ')).toBe('2.5');
  });

  it('converts to thousandths', () => {
    expect(toMilli('2.5')).toBe(2500n);
    expect(toMilli('-2')).toBe(-2000n);
    expect(toMilli('x')).toBeNull();
  });

  it('formats signed changes', () => {
    expect(signed('-2')).toBe('−2');
    expect(signed('5')).toBe('+5');
  });
});
