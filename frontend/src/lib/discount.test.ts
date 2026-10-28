import { describe, expect, it } from 'vitest';
import { discountCents, parseDiscount } from './discount';

describe('discounts', () => {
  it('reads percentages and amounts as typed in Colombia', () => {
    expect(parseDiscount('10%')).toEqual({ discountPercent: '10' });
    expect(parseDiscount(' 2,5 % ')).toEqual({ discountPercent: '2.5' });
    expect(parseDiscount('1.500')).toEqual({ discount: '1500' });
    expect(parseDiscount('')).toEqual({});
  });

  it('rejects what the API would reject', () => {
    expect(parseDiscount('101%')).toBeNull();
    expect(parseDiscount('0%')).toBeNull();
    expect(parseDiscount('0')).toBeNull();
    expect(parseDiscount('mucho')).toBeNull();
  });

  it('computes like the API, rounding half-up', () => {
    expect(discountCents(600000n, { discountPercent: '10' })).toBe(60000n);
    expect(discountCents(333n, { discountPercent: '50' })).toBe(167n);
    expect(discountCents(600000n, { discount: '500' })).toBe(50000n);
    expect(discountCents(600000n, {})).toBe(0n);
  });
});
