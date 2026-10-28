import { describe, expect, it } from 'vitest';
import { formatMoney, lineTotal, normalizeAmount, sumAmounts } from './money';

describe('normalizeAmount', () => {
  it.each([
    ['15000', '15000'],
    ['15.000', '15000'],
    ['1.234.567', '1234567'],
    ['0,10', '0.10'],
    ['1.234,5', '1234.5'],
    ['15.5', '15.5'],
    ['15.50', '15.50'],
    [' $ 2.000 ', '2000'],
    ['', ''],
  ])('%s → %s', (input, expected) => {
    expect(normalizeAmount(input)).toBe(expected);
  });
});

describe('formatMoney', () => {
  it('formats Colombian pesos without decimals when whole', () => {
    expect(formatMoney('15000.00')).toBe('$15.000');
  });

  it('keeps cents when present', () => {
    expect(formatMoney('0.30')).toBe('$0,30');
  });

  it('formats negative balances', () => {
    expect(formatMoney('-30000.00')).toBe('-$30.000');
  });
});

describe('lineTotal', () => {
  it('multiplies quantity by unit price rounding half-up to cents', () => {
    expect(lineTotal('2', '3000')).toBe('6000.00');
    expect(lineTotal('0.333', '0.05')).toBe('0.02');
    expect(lineTotal('0.25', '18000')).toBe('4500.00');
  });

  it('returns null for invalid input', () => {
    expect(lineTotal('x', '3000')).toBeNull();
    expect(lineTotal('2', '')).toBeNull();
  });
});

describe('sumAmounts', () => {
  it('adds exactly', () => {
    expect(sumAmounts(['0.10', '0.10', '0.10'])).toBe('0.30');
  });
});
