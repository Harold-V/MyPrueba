import { describe, expect, it } from 'vitest';
import { fold, matchesProduct } from './search';

describe('product search', () => {
  it('ignores case and accents', () => {
    expect(fold('Jabón AZUL')).toBe('jabon azul');
    expect(matchesProduct({ name: 'Jabón' }, 'JABON')).toBe(true);
  });

  it('matches the barcode and treats an empty query as everything', () => {
    expect(matchesProduct({ name: 'Gaseosa', barcode: '7702004003508' }, '7702004')).toBe(true);
    expect(matchesProduct({ name: 'Gaseosa', barcode: null }, 'pan')).toBe(false);
    expect(matchesProduct({ name: 'Gaseosa' }, '  ')).toBe(true);
  });
});
