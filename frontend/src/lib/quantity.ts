/** "2,5" → "2.5": the API expects a dot as decimal separator. */
export function normalizeQuantity(value: string): string {
  return value.trim().replace(',', '.');
}

/** Quantity as integer thousandths, or null when it is not a number. */
export function toMilli(quantity: string): bigint | null {
  const match = /^(-?)(\d+)(?:\.(\d{1,3}))?$/.exec(normalizeQuantity(quantity));
  if (!match) return null;
  const [, sign, whole = '0', fraction = ''] = match;
  const milli = BigInt(whole) * 1000n + BigInt(fraction.padEnd(3, '0') || '0');
  return sign === '-' ? -milli : milli;
}

/** "-2" → "−2", "5" → "+5" (typographic minus for display). */
export function signed(quantity: string): string {
  return quantity.startsWith('-') ? `−${quantity.slice(1)}` : `+${quantity}`;
}
