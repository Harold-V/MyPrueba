/** Case- and accent-insensitive key: "Jabón" and "JABON" both become "jabon". */
export function fold(text: string): string {
  return text.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
}

/** Whether a product's name or barcode contains what the user typed. */
export function matchesProduct(
  product: { name: string; barcode?: string | null },
  query: string,
): boolean {
  const q = fold(query.trim());
  if (!q) return true;
  return fold(product.name).includes(q) || fold(product.barcode ?? '').includes(q);
}
