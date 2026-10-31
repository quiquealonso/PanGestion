# Despliegue y producción

| Qué | Dónde |
|---|---|
| Web promocional | https://quiquealonso.github.io/PanGestion/ (GitHub Pages, carpeta `site/`) |
| App (gestión y web de pedidos) | https://quiquealonso.github.io/PanGestion/app/ (GitHub Pages) |
| API | https://34-175-204-112.sslip.io (VM de Google Cloud en Madrid, `europe-southwest1`) |
| Base de datos | PostgreSQL 17 en la misma VM, sin puertos abiertos a Internet |

En la VM, la API, PostgreSQL y Caddy (HTTPS con Let's Encrypt) son contenedores de Docker: `backend/deploy/docker-compose.yml`.
La configuración y los secretos están en `~/pangestion/app/backend/deploy/.env`, que no está en Git (ver `.env.example`).

Los enlaces de cuando la app estaba en la raíz (`/PanGestion/pedido`, `/PanGestion/auth/login`...) se redirigen solos a `/app/...`.

## Integración y despliegue continuos

| Cuándo | Qué se ejecuta | Workflow |
|---|---|---|
| Push a cualquier rama (menos `main`) | Tests rápidos del backend (unidad y reglas de negocio) y tests y build del frontend | `ci.yml` |
| Pull request | Todos los tests del backend, frontend y seguridad (gitleaks, `npm audit`, OSV) | `ci.yml`, `security.yml` |
| Fusionar en `main` | Otra vez todos los tests y la seguridad; después se despliega la API en la VM y el frontend en Pages, y se pasan pruebas de humo contra producción | `deploy.yml` |

- **Rama `main` protegida:** solo se llega por PR, con los checks en verde.
- **Despliegue de la API:** Actions entra por SSH con una clave que solo puede ejecutar `~/pangestion/deploy.sh` en la VM (copia de `backend/deploy/deploy.sh`). El script hace lo siguiente:
  1. Comprueba que el commit está en `main`.
  2. Hace una copia de la BBDD en `~/pangestion/backups`, donde guarda las últimas 14.
  3. Construye la imagen y arranca los contenedores.
  4. Espera a que la API responda; si no responde, vuelve al commit anterior.
- **Secretos del repositorio:** `VM_SSH_KEY` (clave privada de despliegue) y `VM_KNOWN_HOSTS` (huella de la VM).
- **Desplegar a mano, en la VM:** `~/pangestion/deploy.sh <sha de main>`.

## App instalable (PWA)

La web se puede instalar en el móvil o en el ordenador desde el navegador: en Chrome, con «Instalar app»; en el iPhone, con Compartir → «Añadir a pantalla de inicio». Son dos apps distintas:

- **PanGestión** (`/PanGestion/app/`): la de gestión.
- **Pedir** (`/PanGestion/app/pedido`): la de pedidos de clientes.

- **Instalar:** en el móvil sale un aviso «Instala la app». En Android se instala con el botón; en iPhone, Apple solo permite hacerlo a mano, así que el aviso explica cómo.
- **Versiones:** cada despliegue tiene una versión (`1.0.<número de despliegue>`), que aparece en la pantalla de acceso. Para subir la versión mayor o menor, cambia `version` en `package.json`.
- **Actualizar:** las apps abiertas o instaladas detectan la versión nueva (al abrirlas, al volver a ellas y cada 5 minutos) y muestran el botón «Actualizar», que la activa y recarga.
- **Datos:** siempre se piden a la API; nada se guarda sin conexión.

## Cuentas de cliente y emails

> **Ahora mismo están desactivadas** (`CUSTOMER_ACCOUNTS_ENABLED=false`): se pide sin cuenta, como antes. Lo que falta para activarlas está en [docs/pendientes.md](pendientes.md).

Con las cuentas activas, para pedir en la web pública hay que tener una cuenta: es lo primero que se pide, antes de elegir el establecimiento. Al crearla se envía un email con un enlace para confirmarla (caduca a las 24 horas). Los emails salen del backend por SMTP: GitHub no ofrece SMTP.

Configúralo en el `.env` de la VM (`~/pangestion/app/backend/deploy/.env`), junto con `CUSTOMER_ACCOUNTS_ENABLED=true`. Si las cuentas están activas y no hay SMTP, se activan sin confirmar el email.

1. **Gmail** (gratis, unos 500 emails al día):
   - Activa la verificación en 2 pasos en tu cuenta de Google.
   - Crea una contraseña de aplicación en https://myaccount.google.com/apppasswords.
   - Pon esto en el `.env`, con tus datos:
     ```
     SMTP_HOST=smtp.gmail.com
     SMTP_PORT=587
     SMTP_USERNAME=tu correo de Gmail
     SMTP_PASSWORD=la contraseña de aplicación (16 letras, sin espacios)
     MAIL_FROM="PanGestión <tu correo de Gmail>"
     ```
2. Reinicia la API: en la VM, `cd ~/pangestion/app/backend/deploy && docker compose up -d api`.

Otros proveedores con plan gratis, como Brevo (300 al día) o Mailjet, funcionan igual: cambia `SMTP_HOST` y las credenciales.

## Logs

La API deja una línea por petición (método, ruta, estado, tiempo, usuario e IP). Los errores inesperados guardan la traza completa. Cada línea lleva el id de la petición: es el «código» que ve el usuario en un error 500 y la cabecera `X-Request-Id`. Docker guarda como mucho 5 ficheros de 10 MB por contenedor.

En la VM (`cd ~/pangestion/app/backend/deploy`):

```bash
docker compose logs api --since 1h                         # la última hora
docker compose logs api --since 24h | grep -E "ERROR|WARN"  # errores y avisos del último día
docker compose logs api | grep abc123                      # todo lo de una petición (su código)
docker compose logs -f api                                 # en directo
```

Si un despliegue falla, GitHub envía un email y el log del job «Desplegar API (VM)» muestra la salida de `deploy.sh`.

## Lo pendiente

Lo que se ha dejado para más adelante (correo de la app, guardar la clave de cifrado...) está en [pendientes.md](pendientes.md).
