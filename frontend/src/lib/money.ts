// Amounts travel as decimal strings ("15000.00") and are computed as integer cents (BigInt),
// mirroring the backend (constitution principle 23). Never use floating point for money.

/** Turns what a Colombian user types ("15.000", "0,10", "$ 2.000") into an API decimal string. */
export function normalizeAmount(input: string): string {
  const value = input.replace(/[\s$]/g, '');
  if (value === '') return '';
  if (value.includes(',')) {
    return value.replace(/\./g, '').replace(',', '.');
  }
  // A single dot followed by one or two digits is a decimal point ("15.5"); otherwise dots group thousands.
  if (/^\d+\.\d{1,2}$/.test(value)) return value;
  return value.replace(/\./g, '');
}

export function toCents(amount: string): bigint | null {
  const match = /^(-?)(\d+)(?:\.(\d{1,2}))?$/.exec(amount.trim());
  if (!match) return null;
  const [, sign, whole = '0', fraction = ''] = match;
  const cents = BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0') || '0');
  return sign === '-' ? -cents : cents;
}

export function fromCents(cents: bigint): string {
  const negative = cents < 0n;
  const abs = negative ? -cents : cents;
  const fraction = (abs % 100n).toString().padStart(2, '0');
  return `${negative ? '-' : ''}${abs / 100n}.${fraction}`;
}

/** "15000.00" → "$15.000"; "0.30" → "$0,30"; "-30000.00" → "-$30.000". */
export function formatMoney(amount: string): string {
  const cents = toCents(amount) ?? 0n;
  const negative = cents < 0n;
  const abs = negative ? -cents : cents;
  const whole = (abs / 100n).toString().replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  const fraction = abs % 100n;
  const decimals = fraction === 0n ? '' : `,${fraction.toString().padStart(2, '0')}`;
  return `${negative ? '-' : ''}$${whole}${decimals}`;
}

/** Quantity × unit price, rounded half-up to cents, or null when either is not a valid number. */
export function lineTotal(quantity: string, unitPrice: string): string | null {
  const qty = /^(\d+)(?:\.(\d{1,3}))?$/.exec(quantity.trim());
  const price = toCents(unitPrice);
  if (!qty || price === null) return null;
  const [, whole = '0', fraction = ''] = qty;
  const milli = BigInt(whole) * 1000n + BigInt(fraction.padEnd(3, '0') || '0');
  const product = milli * price;
  const cents = product / 1000n + (product % 1000n >= 500n ? 1n : 0n);
  return fromCents(cents);
}

export function sumAmounts(amounts: string[]): string {
  return fromCents(amounts.reduce((sum, amount) => sum + (toCents(amount) ?? 0n), 0n));
}
