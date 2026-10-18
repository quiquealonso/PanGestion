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
- **Clientes**, **Mi empresa** (datos y horario de recogida) y **Usuarios** con roles.
- **Superadministrador:** alta de empresas (panadería, bar o restaurante) y de sus usuarios.

**Web pública** (`/pedido`, sin login): elegir negocio, día y franja disponible, añadir productos y confirmar.

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

## Estructura

```
PanGestion/
├── src/app/                  Frontend Angular
│   ├── core/                 modelos, servicios HTTP, guards, interceptor, utilidades
│   ├── shared/               diálogo, confirmación, formulario de empresa, pipes
│   ├── layout/shell/         menú lateral según rol
│   └── features/             una carpeta por pantalla (dashboard, orders, production, catalog, customers…)
├── backend/                  API Spring Boot (ver backend/README.md)
│   ├── src/main/java/com/pangestion/   config, controller, dto, exception, model, repository, security, service, storage
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
- **Datos e imágenes:** la base de datos H2 y las fotos subidas no se sincronizan solas. Para llevarlas a otra máquina hay una copia en `backups/` (ver [Empezar en otro ordenador](#empezar-en-otro-ordenador-por-ejemplo-en-clase)); si no se restaura, se generan los datos de ejemplo básicos.
