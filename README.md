# PanGestión

Plataforma de **pedidos y gestión para panaderías, bares y restaurantes**. Cada negocio tiene su propio panel
(catálogo, clientes, pedidos, producción y usuarios) y sus clientes piden desde una web pública eligiendo
día y hora de recogida.

| Parte | Carpeta | Tecnología |
|---|---|---|
| Frontend | raíz (`src/`) | Angular 22 · panel de gestión + web pública de pedidos |
| Backend | [`backend/`](backend/README.md) | Java 21 · Spring Boot 4 · Spring Data JPA / Hibernate · Spring Security (JWT) |
| Base de datos | — | H2 en fichero (desarrollo) o PostgreSQL |

El modelo de datos, las reglas de negocio y todos los endpoints están documentados en [`backend/README.md`](backend/README.md).
El contexto del proyecto y las decisiones tomadas, en [`context.md`](context.md).

## Funcionalidades

**Panel de gestión** (requiere login):

- **Inicio:** pedidos pendientes, en proceso y listos, recogidas de hoy, facturación del mes, clientes, gráfica de
  unidades vendidas por día comparando semanas y ranking de productos más vendidos.
- **Pedidos:** vista por día (hoy por defecto), detalle, cambio de estado, edición y borrado. Botones para
  **descargar** lo que hay que preparar el día (`producto: cantidad`) y **pasar a "en proceso"** todos los pendientes del día.
- **Producción del día:** tabla producto × franja horaria que se actualiza sola, pensada para la pantalla del obrador o la cocina.
- **Nuevo pedido:** productos por categorías con carrito flotante → resumen (quitar ingredientes: "pizza sin cebolla") → cliente → día y franja.
- **Catálogo:** productos (foto, precio, descripción, abreviatura, color, ingredientes, orden), categorías e ingredientes con alérgenos.
- **Clientes:** estadísticas (nuevos por semana, activos, gasto medio…), filtros por pedidos y gasto, tarjetas con pedidos y total gastado.
- **Mi empresa** (datos y horario de recogida) y **Usuarios** con roles.
- **Superadministrador:** alta de empresas (panadería, bar o restaurante) y de sus usuarios.
- **Modo claro y oscuro** del panel (botón en el menú; por defecto, el del sistema).

**Sala** (solo bares y restaurantes):

- **Sala y mesas:** editor visual del plano por zonas (terraza, interior…): crear, mover, redimensionar y borrar mesas.
- **Servicio de mesas** (camarero, en el móvil): plano en vivo, abrir mesa juntando otras, comanda por rondas.
- **Cocina y barra:** pantalla táctil con el estado de cada producto (pendiente → preparando → listo); mesas y pedidos para llevar diferenciados.
- **Avisos en tiempo real:** cocina recibe las rondas al instante y el camarero un aviso con sonido cuando algo está listo.
- **Barra y cobros:** cobrar todo, entre comensales o a partes iguales (efectivo con cambio o tarjeta), precuenta de 80 mm y caja del día.
- **Pedidos a domicilio y reparto:** el cliente elige recoger o a domicilio; el repartidor, desde el móvil, se lleva los pedidos listos (Transportando) y los marca entregados; recibe un aviso cuando cocina termina uno.
- **Informes de sala:** facturación, ticket medio, horas punta, más vendidos, camareros, mesas y tiempos de cocina.

**Web pública** (`/pedido`, sin login, pensada para el móvil y para cualquier edad): asistente de 5 pasos —
negocio → productos → revisar (quitar ingredientes) → datos → día y hora + confirmar— con botones grandes,
instrucciones en cada paso y número de pedido al terminar.

## Requisitos

- **Java 21** o superior. Maven no hace falta: se descarga solo con `mvnw`.
- **Node.js 24.15** o superior (lo exige Angular CLI 22).
- PostgreSQL solo si se quiere usar en lugar de H2 (hay un `backend/docker-compose.yml`).

## Empezar en otro ordenador (por ejemplo, en clase)

Requisitos: Git, Java 21+ y Node 24.15+ (ver [Requisitos](#requisitos)).

**1. Clonar e instalar**

```bash
git clone https://github.com/quiquealonso/PanGestion.git
cd PanGestion
npm ci
```

**2. Restaurar la base de datos de pruebas** (opcional pero recomendado: trae los ~310 pedidos de ejemplo, la
panadería con fotos y el bar El Mosset). Sin este paso, el backend crea los datos de ejemplo básicos al arrancar.
La copia no está en el repositorio (es público): copia el zip desde el SSD
(`D:\2DAW\DesarrolloServidor\PanGestion-backups\`) a una carpeta `backups/` en la raíz del proyecto, que Git ignora.

```bash
# Linux / macOS
unzip backups/pangestion-backup-2026-10-18.zip -x LEEME.txt -d backend
```

```powershell
# Windows (PowerShell)
Expand-Archive backups\pangestion-backup-2026-10-18.zip -DestinationPath backend\restore-tmp
Move-Item backend\restore-tmp\data, backend\restore-tmp\uploads backend\
Remove-Item -Recurse backend\restore-tmp
```

Debe quedar `backend/data/pangestion.mv.db` y `backend/uploads/1/products/...`.

**3. Arrancar** (dos terminales):

```bash
cd backend && ./mvnw spring-boot:run      # Windows: mvnw.cmd spring-boot:run   (puerto 8080)
npm start                                 # en la raíz (puerto 4200)
```

Abre http://localhost:4200 y entra con un usuario de prueba (botón **Usar** en el login).

## Arrancar en local

1. **Backend** (puerto 8080). La primera vez crea la base de datos H2 en `backend/data/` con datos de ejemplo:

   ```bash
   cd backend
   ./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
   ```

2. **Frontend** (puerto 4200). `npm start` usa `proxy.conf.json`, que manda `/api` y `/uploads` al backend:

   ```bash
   npm ci
   npm start                     # otro puerto: npm start -- --port 4201
   ```

3. Abre http://localhost:4200. En el login aparecen los usuarios de prueba (solo en desarrollo) con un botón
   **Usar** que rellena el formulario. La web pública está en http://localhost:4200/pedido.

### Usuarios de prueba

| Usuario | Contraseña | Qué ve |
|---|---|---|
| `superadmin@admin.es` | `admin1234` | Empresas y usuarios de todas |
| `admin@admin.es` | `admin1234` | Panadería (catálogo real con fotos) |
| `admin2@admin.es` | `admin1234` | Bar El Mosset (carta completa) |
| `gestor@pangestion.local` | `gestor123` | Panadería, sin usuarios ni datos de empresa |
| `visor@pangestion.local` | `visor123` | Panadería, solo lectura |
| `camarero@pangestion.local` | `camarero123` | Bar El Mosset: servicio de mesas y cobros |
| `cocina@pangestion.local` | `cocina123` | Bar El Mosset: pantalla de cocina y barra |

Se crean en `backend/src/main/java/com/pangestion/config/DataInitializer.java` y se listan en el login desde
`src/environments/environment.ts`. El build de producción no los incluye. **Hay que quitarlos antes de publicar.**

### Empezar con la base de datos limpia

Para el backend, borra `backend/data/` (base de datos) y `backend/uploads/` (imágenes subidas) y vuelve a arrancarlo:
se cargan de nuevo los datos de ejemplo, incluidas las fotos de `backend/src/main/resources/seed-images/` y la carta
de `backend/src/main/resources/seed/el-mosset.json`.

### Con PostgreSQL

```bash
cd backend
cp .env.example .env          # y rellena DB_USER, DB_PASSWORD y JWT_SECRET
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
```

Las credenciales van en `backend/.env`, que **no se sube a Git**. Con PostgreSQL no se crean usuarios de prueba
salvo que se ponga `SEED_DATA=true`.

## Compilar y probar

```bash
npm run build                    # frontend (dist/gestion-pedidos)
cd backend && ./mvnw test        # test del backend (BD en memoria)
cd backend && ./mvnw package     # backend (jar en backend/target)
```

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

- **PanGestión** (`/PanGestion/`): la de gestión.
- **Pedir** (`/PanGestion/pedido`): la de pedidos de clientes.

El service worker guarda la app para que abra rápido. Cada versión nueva se descarga sola y se aplica al cambiar de pantalla. Los datos siempre se piden a la API: nada se guarda sin conexión.

## Seguridad

Las reglas de seguridad, con su estado y su comprobación, están en [docs/reglas-seguridad.md](docs/reglas-seguridad.md). Añade allí las nuevas.

- **Autenticación en el servidor:** JWT HS256 en la cabecera `Authorization` (sin cookies, porque el frontend y la API están en dominios distintos). Las reglas de acceso por rol están en `SecurityConfig`, y cada servicio filtra por el `companyId` del token. Pedir datos de otra empresa devuelve 404.
- **Contraseñas:** se guardan con BCrypt y deben tener 8 caracteres como mínimo. El login tarda lo mismo exista o no el email, así que el tiempo de respuesta no revela qué cuentas existen.
- **Límite de intentos:** el login admite 20 intentos cada 5 minutos por IP y 10 cada 15 minutos por email. La web pública admite 10 pedidos por hora y por IP. Al superar el límite, la API responde con 429 y `Retry-After`.
- **Bots:** el formulario público incluye un campo trampa oculto (`website`); si llega relleno, la API rechaza el pedido.
- **Datos personales cifrados (AES-256-GCM):** el teléfono y el CIF de los clientes y la dirección de entrega se cifran con la clave `ENCRYPTION_KEY`. Guarda una copia de esa clave: si se pierde, esos datos no se pueden leer.
- **Entradas y respuestas:** los DTO tienen Bean Validation y la API rechaza los campos que no existen. Las consultas usan JPQL con parámetros. La web pública solo recibe los datos que necesita.
- **Subida de imágenes:** solo JPEG, PNG o WebP, comprobados por su firma, de 2 MB como máximo y con un nombre aleatorio.
- **HTTPS y cabeceras:** Caddy redirige a HTTPS y añade HSTS, CSP, `nosniff`, `X-Frame-Options`, `Referrer-Policy` y `Permissions-Policy` (`backend/deploy/Caddyfile`). El frontend de producción lleva su propia CSP (`src/index.prod.html`); si cambia la URL de la API, hay que cambiarla también allí.
- **Secretos y dependencias:** el workflow "Seguridad" pasa gitleaks sobre el historial, `npm audit` y OSV-Scanner en cada PR y cada lunes. Dependabot abre PR semanales contra `dev`.

## Estructura

```
PanGestion/
├── src/app/                  Frontend Angular
│   ├── core/                 modelos, servicios HTTP, guards, interceptor, utilidades
│   ├── shared/               diálogo, confirmación, formulario de empresa, pipes, mesa, avisos, gráfico de barras
│   ├── layout/shell/         menú lateral según rol y tipo de negocio
│   └── features/             una carpeta por pantalla (dashboard, orders, production, catalog, customers,
│                             dining, service, kitchen, bar, dining-report…)
├── backend/                  API Spring Boot (ver backend/README.md)
│   ├── src/main/java/com/pangestion/   config, controller, dto, events, exception, model, repository, security, service, storage
│   └── src/main/resources/             application*.properties, seed/ (cartas), seed-images/ (fotos de ejemplo)
├── proxy.conf.json           /api y /uploads → localhost:8080 en desarrollo
└── context.md                contexto y decisiones del proyecto
```

## Cambiar de sistema operativo (Windows ⇄ Linux)

`node_modules`, `.angular/`, `dist/`, `backend/target/`, `backend/data/` y `backend/uploads/` son **locales de cada
máquina**: no se copian ni se suben a Git. Lo normal es clonar el repositorio en cada sistema e instalar allí:

```bash
git clone https://github.com/quiquealonso/PanGestion.git
cd PanGestion
npm ci                      # en un disco exFAT/FAT: npm ci --no-bin-links
```

- **Binarios nativos:** esbuild y otros paquetes instalan un ejecutable distinto en Windows y en Linux; un `node_modules` instalado en un sistema no sirve en el otro.
- **Discos exFAT** (SSD portátil): no admiten enlaces simbólicos ni permisos de ejecución. Se instala con `--no-bin-links` (los scripts de `package.json` llaman al CLI de Angular con `node` directamente) y `mvnw` se lanza con `sh mvnw ...`. Git además pedirá `git config --global --add safe.directory <ruta>`.
- **`mvnw` en Linux:** está marcado como ejecutable en Git; si aun así falla, `chmod +x backend/mvnw`.
- **Finales de línea:** `.gitattributes` y `.editorconfig` fuerzan LF (CRLF solo para `.cmd`).
- **Datos e imágenes:** la base de datos H2 y las fotos subidas no se sincronizan solas. Para llevarlas a otra máquina hay una copia en el SSD que se coloca en `backups/` (no se sube a Git) (ver [Empezar en otro ordenador](#empezar-en-otro-ordenador-por-ejemplo-en-clase)); si no se restaura, se generan los datos de ejemplo básicos.
