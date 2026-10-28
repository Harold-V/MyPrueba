---
type: constitution
title: MyPrueba — Constitution
description: The non-negotiable principles every rsc-sdd phase obeys.
tags: [sdd, constitution]
timestamp: 2026-10-28T05:10:00Z
topic: sdd
status: draft
version: v1.0.0
---

# MyPrueba — Constitution

> Version: v1.0.0 · Ratified: pendiente · Last amended: 2026-10-28
> Principios no negociables que obedece cada fase rsc-sdd. Las mecánicas del stack viven en
> `02-DOCS/wiki/stack/*`; este archivo ratifica el principio y enlaza el detalle.
> Objetivo: réplica funcional clean-room de Treinta (gestión para micronegocios), analizada con
> REA, con marca y código propios. Proyecto mixto · alcance complejo.

## 1. Stack canon

1. Backend: Java 25 LTS, Spring Boot 4.x, Spring Modulith 2.x, Maven vía wrapper (`./mvnw`).
   Versiones exactas fijadas en `backend/pom.xml`. Cambiar un framework es enmienda MAJOR.
2. Frontend: React + Vite + Tailwind CSS en TypeScript, Node 24 LTS (`.nvmrc`), npm con
   `package-lock.json` versionado. Versiones fijadas en `frontend/package.json`.
3. Base de datos: SQLite. El esquema cambia solo mediante migraciones Flyway versionadas, escritas
   en SQL portable a PostgreSQL (sin tipos ni funciones exclusivas de SQLite). Pasar a PostgreSQL es
   enmienda MINOR.
4. Monorepo: `backend/` y `frontend/` en la raíz. Se comunican solo por la API HTTP JSON bajo
   `/api`, descrita en OpenAPI generado desde el backend.

## 2. Quality bar

5. Formato y lint limpios en cada commit: Spotless (palantir-java-format) en backend; Prettier +
   ESLint con cero warnings en frontend.
6. TypeScript con `strict: true`; `tsc --noEmit` pasa sin errores antes del merge.
7. TDD (red → green → refactor): JUnit 5 + Spring Boot Test en backend; Vitest + Testing Library en
   frontend. Cobertura de líneas ≥ 80 % en el código cambiado (JaCoCo / Vitest coverage).
8. Los límites de módulos se verifican siempre: el test `ApplicationModules.of(…).verify()` pasa
   en cada build.

## 3. Conventions

9. Un módulo Spring Modulith por dominio de negocio (p. ej. ventas, gastos, inventario, clientes,
   deudas, reportes). Un módulo usa otro solo por su API pública o por eventos de dominio; nunca por
   sus paquetes internos.
10. Dinero: `BigDecimal` en Java, enteros o strings decimales en JSON y `NUMERIC`/`DECIMAL` en BD;
    nunca `double`/`float`. Moneda explícita (ISO 4217, por defecto COP).
11. Errores de API en formato RFC 9457 (Problem Details); JSON en camelCase; fechas ISO 8601 con
    zona.
12. Identificadores, código y commits en inglés; textos de UI en español, centralizados para i18n.
13. Commits con gitmoji + Conventional Commits: `✨ feat(scope): subject`. Enforced: `gitmoji-guard`.

## 4. Branching & shipping

14. Todo trabajo va en una rama y entra por PR; `main` está cerrado al trabajo directo.
    Enforced: `branch-guard`.
15. **La autoría de git es humana.** Sin `Co-Authored-By` de IA ni pie "generated with" en commits
    ni PRs. Enforced en la fase `ship`.

## 5. Security & privacy floor

16. Ningún secreto se versiona: configuración por variables de entorno; `.env` en `.gitignore`.
17. Todo endpoint salvo autenticación y salud exige usuario autenticado, y todo dato de negocio se
    filtra por el negocio dueño. Cada endpoint tiene un test que prueba que otro negocio no lo lee
    ni lo modifica.
18. **Clean-room.** No entra al repo código, marca, logos, textos, imágenes ni otros recursos de
    Treinta. Los hallazgos de REA se guardan como evidencia de *comportamiento* en
    `02-DOCS/wiki/research/`. El análisis se limita a superficies públicas o a la cuenta propia del
    usuario.

## 6. UX / accessibility floor

19. WCAG 2.2 AA: navegable por teclado, foco visible, contraste AA. Mobile-first: todas las
    pantallas funcionan desde 360 px de ancho.

## 7. Performance budgets

20. p95 ≤ 300 ms en endpoints CRUD medido en los tests de integración; JS inicial del frontend
    ≤ 250 KB gzip.

## 8. Knowledge & decisions

21. Toda decisión significativa se anota en `02-DOCS/wiki/sdd/decisions.md` (fecha, opciones, por
    qué). La constitución es el registro de decisiones de mayor rango.
22. Cada spec de un módulo replicado cita la evidencia REA de cada comportamiento, o lo marca como
    `sin verificar` hasta tenerla.

## Definition of Done (the merge bar `verify` runs against)

Un cambio entra solo si se cumple TODO:

- [ ] Formato y lint limpios; TypeScript strict pasa (principios 5-6).
- [ ] Tests en verde, cobertura ≥ 80 % en código cambiado, `ApplicationModules.verify()` pasa (7-8).
- [ ] Convenciones de módulos, dinero, API, idioma y commits cumplidas (9-13).
- [ ] En rama, entra por PR, autoría humana (14-15).
- [ ] Sin secretos; autenticación y aislamiento por negocio probados (16-17).
- [ ] Nada de Treinta copiado; evidencia REA citada o marcada `sin verificar` (18, 22).
- [ ] Accesibilidad y presupuestos de rendimiento cumplidos donde aplican (19-20).
- [ ] Decisiones significativas registradas (21).

## Amendment log (append-only)

| Date | Version | Change | Why |
|------|---------|--------|-----|
| 2026-10-28 | draft | Borrador generado por `rsc onboard`. | El SDD necesita una constitución. |
| 2026-10-28 | v1.0.0 | Propuesta inicial: stack Java 25/Boot 4/Modulith + React/Vite/Tailwind, SQLite portable a PostgreSQL, clean-room. | Arranque del proyecto. Pendiente de ratificar. |
