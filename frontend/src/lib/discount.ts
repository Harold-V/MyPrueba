import { normalizeAmount } from './money';

/** A discount as the API takes it: an amount or a percentage, never both. */
export type Discount = { discount?: string; discountPercent?: string };

/**
 * What the seller typed: "10%" is a percentage, "1.500" an amount, "" no discount.
 * Returns null when it is not a valid discount.
 */
export function parseDiscount(raw: string): Discount | null {
  const value = raw.trim();
  if (!value) return {};
  if (value.endsWith('%')) {
    const percent = value.slice(0, -1).trim().replace(',', '.');
    if (!/^\d{1,3}(\.\d{1,2})?$/.test(percent)) return null;
    const hundredths = percentHundredths(percent);
    return hundredths > 0n && hundredths <= 10000n ? { discountPercent: percent } : null;
  }
  const amount = normalizeAmount(value);
  if (!/^\d+(\.\d{1,2})?$/.test(amount) || /^0+(\.0+)?$/.test(amount)) return null;
  return { discount: amount };
}

function percentHundredths(percent: string): bigint {
  const [whole = '0', fraction = ''] = percent.split('.');
  return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0') || '0');
}

/** Cents taken off `base`, as the API computes it (a percentage rounds half-up). */
export function discountCents(base: bigint, discount: Discount): bigint {
  if (discount.discount) {
    const [whole = '0', fraction = ''] = discount.discount.split('.');
    return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0') || '0');
  }
  if (discount.discountPercent) {
    return (base * percentHundredths(discount.discountPercent) + 5000n) / 10000n;
  }
  return 0n;
}
