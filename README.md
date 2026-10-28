# MyPrueba

Gestión para micronegocios: cuentas, productos, ventas, gastos y balance. Es una réplica funcional
clean-room de Treinta, con marca y código propios. "MyPrueba" es un nombre de trabajo.

- `backend/`: Java 25, Spring Boot 4.1, Spring Modulith 2.1, SQLite + Flyway.
- `frontend/`: React 19, Vite, Tailwind CSS 4 y TypeScript strict.
- `02-DOCS/wiki/`: constitución, specs, planes y decisiones (cadena SDD de rsc-harness).

## Requisitos

- JDK 25. Maven viene incluido con `./mvnw`.
- Node.js 24 (`.nvmrc`).

## Ejecutar en local

```bash
cd backend && APP_COOKIE_SECURE=false ./mvnw spring-boot:run
```

```bash
cd frontend && npm ci && npm run dev
```

Abre http://localhost:5173. Vite redirige `/api` al backend en el puerto 8080. La base de datos se
guarda en `backend/data/myprueba.db`; puedes cambiar la ruta con `APP_DB_PATH`.

## Verificar

```bash
./scripts/verify.sh
```

Ejecuta formato, lint, tipos, pruebas con cobertura ≥ 80 %, límites de módulos, presupuesto de
bundle y auditoría de dependencias. Es el listón de la constitución
(`02-DOCS/wiki/sdd/constitution.md`).
