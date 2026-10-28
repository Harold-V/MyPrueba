#!/usr/bin/env bash
# Definition of Done gate (constitution): format + lint, types, tests + coverage ≥ 80 %,
# Modulith boundaries, frontend bundle budget and a dependency audit. Exits non-zero on any failure.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"

echo "== backend: spotless, tests, ApplicationModules.verify(), JaCoCo ≥ 80 %"
(cd "$root/backend" && ./mvnw -B -q verify)

echo "== frontend: prettier, eslint, tsc, vitest + coverage ≥ 80 %, build, bundle budget, audit"
cd "$root/frontend"
npm ci --silent
npm run -s format:check
npm run -s lint
npm run -s typecheck
npm run -s test:coverage
npm run -s build
npm run -s size
npm audit --omit=dev --audit-level=high

echo "verify: OK"
