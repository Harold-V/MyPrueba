---
type: stack
title: Despliegue
updated: 2026-10-28
---

# Despliegue: Oracle Cloud Always Free + Docker + Caddy

## Decisión

La app es para un solo usuario por ahora y el presupuesto es cero. Hace falta un disco que guarde
SQLite y que la app no se duerma. Esto se comparó el 2026-10-28 (fuentes de terceros):

| Opción | Costo | Disco para SQLite | ¿Se duerme? |
|--------|-------|-------------------|-------------|
| **Oracle Cloud Always Free (VM ARM)** | 0 (pide tarjeta para verificar) | Sí, el disco de la VM | No |
| Render free | 0 | No (disco solo en planes de pago) | Sí, a los 15 min |
| Koyeb free | 0 | No | Sí, a la hora |
| Fly.io | ~2–4 USD/mes, sin plan gratis nuevo | Volumen 0,15 USD/GB | No |
| Railway | 5 USD/mes tras la prueba | Sí | No |
| Cloud Run + Neon Postgres | 0 | No; exigiría migrar a Postgres | Sí |

Elegida: **Oracle Always Free**. Es la única opción gratis que conserva SQLite y no se duerme.
El mismo `Dockerfile` corre en cualquier VM o host de contenedores si después se cambia.

Fuentes: [Render](https://www.srvrlss.io/provider/render/) ·
[Fly.io 2026](https://temps.sh/blog/fly-io-pricing-2026) ·
[Railway](https://www.saaspricepulse.com/tools/railway) ·
[Docker gratis 2026](https://snapdeploy.dev/blog/free-docker-hosting-2026-platforms-compared) ·
[Neon](https://neon.com/faqs/free-plan-limits-and-quotas) ·
[Cloud Run](https://agentdeals.dev/vendor/google-cloud-run).

## Cómo funciona

```text
PR ──▶ verify (scripts/verify.sh)
merge a main ──▶ verify ──▶ imagen amd64+arm64 en ghcr.io (sha-xxxxxxx y main) ──▶ deploy por SSH
                                                                                  │
VM: ~/myprueba ── compose.yaml ── app (Spring Boot: API + frontend, mismo origen) ─┤
                               └─ caddy (HTTPS con Let's Encrypt, puertos 80/443) ─┘
                  data/myprueba.db (SQLite en el disco de la VM) · backups/ (14 copias)
```

- **Imagen** (`Dockerfile`): Node compila el frontend, Maven lo mete en `classpath:/static` y
  la imagen final es solo el JRE 25, usuario 1000, sin herramientas de compilación.
- **Mismo origen**: Spring sirve `/api/**` (con sesión) y el frontend (público). Una ruta sin
  extensión que no es archivo devuelve `index.html` (`shared/SpaConfig.java`). Por eso la cookie
  `SameSite=Strict` sigue igual y no hace falta CORS.
- **Contenedor endurecido** (`deploy/compose.yaml`): sistema de archivos de solo lectura,
  `cap_drop: ALL`, `no-new-privileges`. `/tmp` es tmpfs con `exec` porque sqlite-jdbc carga su
  librería nativa desde ahí. El healthcheck usa `/actuator/health`.
- **Secretos**: el token de ghcr.io es el `GITHUB_TOKEN` de cada ejecución. Viaja por stdin y se
  cierra la sesión al final. La llave SSH y la huella del servidor son secretos del repositorio.
- **Sin configurar, no despliega**: el job `deploy` solo corre si existe la variable
  `DEPLOY_HOST`. Hasta entonces `main` queda verde.

## Puesta en marcha (una sola vez)

### 1. Llave de despliegue (en tu computador)

```bash
ssh-keygen -t ed25519 -f myprueba_deploy -N "" -C myprueba-deploy
```

Crea `myprueba_deploy` (privada, para GitHub) y `myprueba_deploy.pub` (pública, para la VM).

### 2. Cuenta y VM en Oracle Cloud

1. Crea la cuenta en <https://www.oracle.com/cloud/free/>. Pide tarjeta solo para verificar.
   Elige la región de casa con cuidado: no se cambia después (por ejemplo Bogotá o São Paulo).
2. **Compute → Instances → Create instance**:
   - Imagen: **Ubuntu 24.04**.
   - Shape: **Ampere VM.Standard.A1.Flex**, 1 OCPU y 6 GB alcanzan (lo gratis llega a 4 y 24).
     Si dice «out of capacity», reintenta más tarde o usa **VM.Standard.E2.1.Micro** (AMD, 1 GB):
     la imagen también es amd64.
   - SSH keys: pega el contenido de `myprueba_deploy.pub`.
3. Abre los puertos 80 y 443 en **dos** sitios:
   - En la VCN: **Networking → Virtual cloud networks → (tu VCN) → Security Lists → Default →
     Add Ingress Rules**: origen `0.0.0.0/0`, TCP, puertos `80,443`.
   - Dentro de la VM (las imágenes de Oracle traen iptables cerrado):

```bash
ssh -i myprueba_deploy ubuntu@IP_DE_LA_VM
```

```bash
sudo iptables -I INPUT 6 -m state --state NEW -p tcp -m multiport --dports 80,443 -j ACCEPT && sudo netfilter-persistent save
```

### 3. Docker y sqlite3 en la VM

```bash
curl -fsSL https://get.docker.com | sudo sh && sudo usermod -aG docker ubuntu && sudo apt-get install -y sqlite3
```

Sal y vuelve a entrar por SSH para que el grupo `docker` aplique.

### 4. Un dominio gratis con HTTPS

La cookie de sesión es `Secure`, así que hace falta HTTPS. Caddy pide el certificado solo.

- Sin registro: `IP-CON-GUIONES.sslip.io` (por ejemplo `129-146-10-20.sslip.io`).
- Con nombre propio: un subdominio gratis de <https://www.duckdns.org> apuntando a la IP.

### 5. Variables y secretos en GitHub

En el repositorio: **Settings → Secrets and variables → Actions**.

| Tipo | Nombre | Valor |
|------|--------|-------|
| Variable | `DEPLOY_HOST` | IP pública de la VM |
| Variable | `DEPLOY_USER` | `ubuntu` |
| Variable | `DEPLOY_DOMAIN` | el dominio del paso 4 |
| Secreto | `DEPLOY_SSH_KEY` | contenido completo de `myprueba_deploy` |
| Secreto | `DEPLOY_KNOWN_HOSTS` | la salida del comando de abajo |

```bash
ssh-keyscan -t ed25519 IP_DE_LA_VM
```

### 6. Primer despliegue

Haz merge a `main`, o en **Actions → ci-deploy → Run workflow** sobre `main`. Al final el job
imprime `live: https://tu-dominio`.

### 7. Copia diaria de la base (recomendado)

En la VM, `crontab -e` y agrega:

```text
0 3 * * * /home/ubuntu/myprueba/backup.sh >/dev/null 2>&1
```

Las copias quedan en `~/myprueba/backups/` (las 14 más nuevas) en el mismo disco. Bájalas de vez
en cuando a otro lugar:

```bash
scp -i myprueba_deploy -r ubuntu@IP_DE_LA_VM:myprueba/backups ./backups-myprueba
```

## Operación

- **Volver a una versión anterior**: **Actions → ci-deploy → Run workflow**, rama `main`, campo
  `tag` = `sha-xxxxxxx` (lo ves en ghcr.io o en el log del deploy). No compila; solo cambia la
  imagen. Las migraciones de Flyway no se deshacen: una migración nueva debe ser compatible
  hacia atrás.
- **Logs**: `cd ~/myprueba && docker compose logs -f app`.
- **Riesgo conocido**: Oracle avisa que puede reclamar instancias Always Free inactivas
  (uso muy bajo durante 7 días). Pasar la cuenta a «Pay As You Go» sin salir de los límites
  gratis lo evita; revisa las condiciones vigentes en la consola antes de hacerlo.
