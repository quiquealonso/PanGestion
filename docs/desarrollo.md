# Desarrollo

Cómo poner PanGestión en marcha en tu ordenador y trabajar en él. Para saber qué hace la aplicación, empieza por el
[README](../README.md); el despliegue a producción está en [despliegue.md](despliegue.md).

| Parte | Carpeta | Tecnología |
|---|---|---|
| Frontend | raíz (`src/`) | Angular 22 · panel de gestión + web pública de pedidos (PWA) |
| Backend | [`backend/`](../backend/README.md) | Java 21 · Spring Boot 4 · Spring Data JPA / Hibernate · Spring Security (JWT) |
| Base de datos | — | H2 en fichero (desarrollo) o PostgreSQL (producción) |
| Web promocional | `site/` | HTML, CSS y JS sin dependencias |

El modelo de datos, las reglas de negocio y todos los endpoints están en [`backend/README.md`](../backend/README.md).
El contexto del proyecto y las decisiones tomadas, en [`context.md`](../context.md).

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

## Estructura

```
PanGestion/
├── src/app/                  Frontend Angular
│   ├── core/                 modelos, servicios HTTP, guards, interceptor, utilidades
│   ├── shared/               diálogo, confirmación, formulario de empresa, pipes, mesa, avisos, gráfico de barras
│   ├── layout/shell/         menú lateral según rol y tipo de negocio
│   └── features/             una carpeta por pantalla (dashboard, orders, production, catalog, customers,
│                             dining, service, kitchen, bar, dining-report…)
├── site/                     web promocional (se publica en la raíz de GitHub Pages)
├── backend/                  API Spring Boot (ver backend/README.md)
│   ├── src/main/java/com/pangestion/   config, controller, dto, events, exception, model, repository, security, service, storage
│   └── src/main/resources/             application*.properties, seed/ (cartas), seed-images/ (fotos de ejemplo)
├── proxy.conf.json           /api y /uploads → localhost:8080 en desarrollo
├── docs/                     desarrollo, despliegue, reglas de seguridad y pendientes
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

## Web promocional

Está en `site/` y no necesita compilarse. Para verla en local:

```bash
python -m http.server 8766 --directory site
```

En producción se publica en la raíz de GitHub Pages y la app en `/app/` (ver [despliegue.md](despliegue.md)).
Las capturas de `site/assets/capturas/` son de la app con los datos de ejemplo.
