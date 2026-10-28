---
type: review
title: Review — núcleo
tags: [sdd, review]
topic: sdd
---

# Review — núcleo (2026-10-28)

Revisión adversarial del diff contra la spec, el plan y la constitución v1.1.0. La hizo el mismo
agente que implementó, sin revisores con contexto limpio: queda pendiente una revisión humana en el PR.

| Lente | Resultado |
|---|---|
| Corrección | 2 defectos encontrados y corregidos (ver la verificación). Dinero siempre en enteros. |
| Seguridad | SQL siempre parametrizado; cada consulta filtra por `business_id`; recurso ajeno = 404 idéntico; token de sesión aleatorio de 256 bits, solo hash en BD; cookie HttpOnly + SameSite=Strict + Secure; login sin enumeración (BCrypt ficticio). Pendientes aceptados: rate limiting. |
| Tests | Cada criterio se prueba contra SQLite real; las pruebas de aislamiento cubren productos, ventas, gastos, movimientos, balance y negocio. |
| Constitución | Principios 1-9 y 11-23 cumplidos; principio 15 (autoría humana) aplicado en los commits de código. |

Veredicto: **aprobado para PR**, con los riesgos aceptados listados en la verificación.
