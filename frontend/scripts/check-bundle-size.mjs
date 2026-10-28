// Constitution principle 20: initial JavaScript ≤ 250 KB gzip.
import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { gzipSync } from 'node:zlib';

const LIMIT_BYTES = 250 * 1024;
const dir = join(import.meta.dirname, '..', 'dist', 'assets');
const total = readdirSync(dir)
  .filter((file) => file.endsWith('.js'))
  .reduce((sum, file) => sum + gzipSync(readFileSync(join(dir, file))).length, 0);

console.log(`JS gzip: ${(total / 1024).toFixed(1)} KB (límite ${LIMIT_BYTES / 1024} KB)`);
if (total > LIMIT_BYTES) {
  process.exitCode = 1;
}
