# Contexto del proyecto · PanGestión

> Leer este archivo antes de tocar el proyecto. Resume qué es, cómo está montado y qué decisiones se han tomado.
> El detalle técnico del backend (modelo de datos, reglas y endpoints) está en `backend/README.md`.

## Qué es

Aplicación web de **pedidos y gestión para negocios de hostelería** (panaderías, bares, restaurantes).
Nació de la propuesta `PanGestion.pdf` (panadería familiar) y se ha generalizado a **multiempresa**:

- **Panel de gestión (backoffice):** cada negocio gestiona su catálogo, clientes, pedidos, producción y usuarios.
- **Web pública (`/pedido`):** el cliente elige el negocio, el día y la franja de recogida, añade productos y confirma.

Referencia funcional: la aplicación de Oberta (`oberta.es/pedidos`), de la que se copiaron el catálogo real de la
panadería, el flujo de "Iniciar pedido" y la pantalla de "Producción del día".

## Stack

| Parte | Tecnología |
|---|---|
| Frontend | Angular 22 (componentes standalone, signals, control flow `@if/@for`, rutas con `loadComponent`), SCSS, RxJS |
| Backend | Java 21, Spring Boot 4.1, Spring Data JPA / Hibernate 7, Spring Security con JWT (resource server, HS256), Bean Validation, Lombok |
| Base de datos | H2 en fichero (perfil `h2`, por defecto) o PostgreSQL (perfil `postgres`). Hibernate crea/actualiza las tablas (`ddl-auto=update`) |
| Imágenes | Disco local (`backend/uploads/`) detrás de la interfaz `ImageStorage` |

## Entorno de trabajo

- Copia de trabajo en Windows: `C:\Users\quiqu\Proyectos\PanGestion` (NTFS). Repositorio: `github.com/quiquealonso/PanGestion` (privado), rama `main`.
- Cada sistema operativo clona e instala por separado: `node_modules`, `.angular/`, `dist/`, `backend/target/`, `backend/data/` y `backend/uploads/` son locales y no van a Git.
- Node **≥ 24.15** (Angular CLI 22). En Windows está instalado Node LTS 24.20 con winget. Java 21+; Maven se descarga solo con `mvnw`.
- En discos **exFAT** (SSD portátil) hay que instalar con `npm ci --no-bin-links` y `mvnw` pierde el permiso de ejecución (`sh mvnw ...`). Ver README.
- En este PC el puerto 4200 lo usa otro proyecto (finanzas): el front se arranca en el **4201** (`npm start -- --port 4201`).

## Comandos

| Qué | Dónde | Comando |
|---|---|---|
| Backend (8080) | `backend/` | `./mvnw spring-boot:run` (Windows: `mvnw.cmd spring-boot:run`) |
| Tests backend | `backend/` | `./mvnw test` |
| Frontend (4200/4201) | raíz | `npm ci` y `npm start` (proxy `/api` y `/uploads` → 8080) |
| Build frontend | raíz | `npm run build` |
| BD desde cero | `backend/` | parar el backend, borrar `data/` (y `uploads/`) y arrancar: se cargan los datos de ejemplo |

## Usuarios de prueba (solo desarrollo)

Los crea `backend/.../config/DataInitializer.java` y se muestran en el login (desde `src/environments/environment.ts`,
nunca en el build de producción). **Quitar antes de publicar.**

| Usuario | Contraseña | Rol / empresa |
|---|---|---|
| `superadmin@admin.es` | `admin1234` | SUPERADMIN (sin empresa) |
| `admin@admin.es` | `admin1234` | ADMIN de la empresa 1 (panadería) |
| `admin2@admin.es` | `admin1234` | ADMIN de la empresa 2 (bar El Mosset) |
| `gestor@pangestion.local` | `gestor123` | GESTOR de la empresa 1 |
| `visor@pangestion.local` | `visor123` | VISOR de la empresa 1 |

## Multiempresa (decisión clave)

- Cada **empresa** (`Company`) es un negocio independiente y su único punto de recogida (dirección, horario, franjas). No hay "tiendas".
- Categorías, ingredientes, productos, clientes, pedidos y usuarios pertenecen a una empresa.
- El JWT lleva `companyId`; los servicios filtran siempre por él con `security/CurrentUser`. Pedir un id de otra empresa da 404.
- **Tipo de negocio** (`BusinessType`: PANADERIA, BAR, RESTAURANTE) para futuras funciones por tipo. Solo lo cambia el SUPERADMIN.
- Roles:
  - `SUPERADMIN`: sin empresa; da de alta empresas y usuarios de cualquiera. No trabaja con catálogo ni pedidos.
  - `ADMIN`: todo en su empresa, incluidos sus usuarios y los datos/horario de la empresa.
  - `GESTOR`: pedidos, producción, catálogo y clientes.
  - `VISOR`: solo lectura (pensado para la pantalla de producción del obrador).
- Web pública: los clientes se identifican por email **dentro de cada negocio** (el mismo email en dos negocios = dos clientes).

## Datos de ejemplo

- **Empresa 1 (panadería):** catálogo real copiado de Oberta (10 categorías, 37 productos con abreviatura, color y orden; 9 fotos en `backend/src/main/resources/seed-images/`). En la BD local se renombró a "Forn Campanero".
- **Empresa 2 (bar El Mosset, Alberic):** carta completa (7 categorías, 85 productos, 74 ingredientes con alérgenos) en `backend/src/main/resources/seed/el-mosset.json`, que `DataInitializer` lee al crear la BD.
- Los alérgenos solo se pusieron donde eran evidentes: hay que revisarlos con cada negocio.

## Funcionalidades

### Panel de gestión

| Pantalla | Ruta | Qué hace |
|---|---|---|
| Login | `/auth/login` | JWT; lista de usuarios de prueba con "Usar" y copiar (solo desarrollo) |
| Inicio | `/dashboard` | 6 indicadores (pendientes, en proceso, listos, recogidas hoy, facturado del mes, clientes), gráfica de unidades vendidas por día (semana vs semana anterior, navegable) y ranking de más vendidos |
| Pedidos | `/orders` | Día seleccionado (hoy por defecto) o todos; tarjetas agrupadas por día y hora; detalle en diálogo (cambiar estado, editar, eliminar); **Descargar pedidos** (`.txt` con `producto: cantidad`); **Pasar a en proceso** (pendientes del día) |
| Producción del día | `/production` | Tabla producto × franja horaria (1–4 h), totales, franja actual marcada, "Por entregar"/"Todo el día", oculta franjas vacías, se actualiza cada 30 s, pantalla completa |
| Nuevo / editar pedido | `/orders/new`, `/orders/:id/edit` | Flujo tipo caja: 1) productos por pestañas con carrito flotante, 2) resumen editable con **personalización** (quitar ingredientes, variantes), 3) cliente, 4) día y franja + confirmación |
| Clientes | `/customers` | Tarjetas + diálogo; razón social/CIF si es negocio; historial de pedidos |
| Productos | `/catalog/products` | Bloques por categoría, tarjetas con foto, descripción y precio; diálogo con abreviatura, color propio, foto, ingredientes (chips); activar/desactivar; ordenar con ◀ ▶ |
| Categorías | `/catalog/categories` | Tarjetas; al pulsar, lista de sus productos y "Editar"; ordenar con ◀ ▶ |
| Ingredientes | `/catalog/ingredients` | De qué está hecho cada producto y su alérgeno (no controla stock); "Lo llevan" |
| Mi empresa | `/company` | Datos, dirección y horario de recogida (solo ADMIN edita) |
| Usuarios | `/users` | SUPERADMIN: todos agrupados por empresa; ADMIN: los de su empresa |
| Empresas | `/companies` | Solo SUPERADMIN: alta y edición de negocios (tipo, CIF, horario, franjas) |

### Web pública (`/pedido`)

Elegir negocio → día (7 próximos) y franja (las completas o pasadas salen tachadas) → productos por categoría
(con foto, ingredientes y alérgenos) → datos del cliente → confirmar. La API ya admite personalizar ingredientes
desde aquí, pero la pantalla aún no tiene el botón.

## Backend (`backend/src/main/java/com/pangestion`)

- `model/`: `Company`, `BusinessType`, `User`, `Role`, `Category`, `Product`, `Ingredient`, `Customer`, `Order` (tabla `orders`), `OrderLine` (con `removedIngredients`), `OrderStatus`.
- `repository/`: Spring Data; todas las consultas de negocio filtran por `companyId`.
- `service/`: lógica y transacciones. `controller/`: REST, siempre devuelven DTOs (nunca entidades).
- `dto/`: records `XxxDto.Request` / `XxxDto.Response` con validaciones; `SortRequest`, `ErrorResponse`.
- `security/`: `TokenService` (JWT) y `CurrentUser` (empresa/rol del token). `config/`: `SecurityConfig`, `WebConfig` (sirve `/uploads/**`), `DataInitializer`.
- `storage/`: `ImageStorage` (interfaz), `LocalImageStorage`, `ImageType` (valida la firma del fichero).
- `exception/`: `NotFoundException` (404), `BusinessException` (409), `GlobalExceptionHandler`.

## Frontend (`src/app`)

- `core/models`: interfaces alineadas con los DTOs. `core/services`: `CrudService` base + uno por recurso, `AuthService`, `ConfirmService` (diálogo de confirmación propio), `NotificationService` (toasts), `MyCompanyService`, `PublicApiService`.
- `core/interceptors/auth.interceptor.ts` (Bearer + logout en 401), `core/guards/auth.guard.ts` (`authGuard`, `companyGuard`, `superAdminGuard`, `usersGuard`).
- `shared/`: `ModalComponent` (diálogo), `ConfirmDialogComponent`, `CompanyFormComponent`, `OrderStatusPipe`.
- `features/`: una carpeta por pantalla (ver tabla). `layout/shell`: menú lateral fijo según rol.
- Estilos compartidos en `src/styles.scss`: paneles, tarjetas (`.card-grid`, `.card`), grupos (`.group`), botones, etiquetas de estado, formularios, asistente por pasos y franjas.

## Convenciones

- Código en inglés; textos de la interfaz en español. Tema oscuro (Space Grotesk / IBM Plex Sans).
- Backend por capas: Controller → Service → Repository. Reglas de negocio en el servicio.
- Pantallas de gestión: **tarjetas en rejilla + diálogo** para crear/editar; borrar siempre con `ConfirmService` (nunca `confirm()` del navegador).
- Las credenciales reales van en `backend/.env` (no se sube). En producción `JWT_SECRET` es obligatorio.
- Gráficos: SVG propio siguiendo la guía de visualización (colores validados para daltonismo, un solo eje, tooltip y vista de tabla).

## Pendiente / ideas

- Horario por día de la semana y con tramos partidos (El Mosset: mañanas y noches, distinto lunes y domingo).
- Extras con suplemento ("ingrediente extra +1 €") y precios por día (pizzas a 6 € los miércoles).
- Reparto a domicilio con zonas y pedido mínimo.
- Personalizar ingredientes desde la web pública.
- Nombres repetidos entre categorías ("Barbacoa" pizza/bocadillo/hamburguesa): mostrar la categoría en el detalle del pedido.
- Redimensionar imágenes al subirlas; almacenamiento S3/R2 al desplegar.
- Paginación en listados grandes, tests de integración de la API y despliegue (VPS con Docker + PostgreSQL).
- Quitar los usuarios de prueba del login antes de publicar.
