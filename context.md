# Contexto del proyecto · PanGestión

> Leer este archivo antes de tocar el proyecto. Resume qué es, cómo está montado y qué decisiones se han tomado.
> El detalle técnico del backend (modelo de datos, reglas y endpoints) está en `backend/README.md`.

## Qué es

Aplicación web de **pedidos y gestión para negocios de hostelería** (panaderías, bares, restaurantes).
Nació de la propuesta `PanGestion.pdf` (panadería familiar) y se ha generalizado a **multiempresa**:

- **Panel de gestión (backoffice):** cada negocio gestiona su catálogo, clientes, pedidos, producción y usuarios.
- **Web pública (`/pedido`):** asistente para el móvil: el cliente elige negocio y productos (pudiendo quitar ingredientes), pone sus datos, elige día y hora de recogida, revisa y confirma.

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

### Pedidos de demostración y copias de la BD

- La panadería (empresa 1) tiene ~310 pedidos generados del 12 al 24 de octubre de 2026 (12–17 entregados, 19–24 pendientes) con `config/DemoOrdersGenerator` (ver `backend/README.md`).
- Copias de la BD (H2 + `uploads/`) en el SSD (no se suben a Git porque el repo es público; para usarlas se copian a `backups/`, que está en `.gitignore`; ver README "Empezar en otro ordenador"): `D:\2DAW\DesarrolloServidor\PanGestion-backups\`. Para restaurar: parar el backend y copiar `data/` y `uploads/` del zip dentro de `backend/`.

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

Sin login. Pensada para **cualquier persona (de 15 a más de 60 años)** y **primero para el móvil**: un paso cada vez,
instrucciones cortas en cada paso y botones grandes. Archivos: `src/app/features/public-order/public-order-page.component.{ts,html,scss}`.

**Flujo** (5 pasos + pantalla final):

| Paso | Qué ve el cliente |
|---|---|
| 1. ¿Dónde quieres pedir? | Tarjetas de negocio (tipo, dirección, horario). Si solo hay uno, se salta y se empieza en el 2 |
| 2. Elige tus productos | Pestañas de categoría (fijas al hacer scroll) y tarjetas compactas: foto, nombre y precio (2 por fila en el móvil, 3 en tablet, 4 en escritorio; sin foto, franja de color baja). Globo con la cantidad que ya lleva. Al tocar una tarjeta se abre el **panel del producto**: foto, descripción, alérgenos, ingredientes para desmarcar, cantidad − + y «Añadir · precio». Al añadir, la barra inferior confirma «✓ Añadido: 2 × Vegetal sin cebolla» |
| 3. Revisa tu pedido | Una tarjeta por línea con cantidad − +, **lápiz** (quitar ingredientes) y **papelera** (eliminar) en la misma fila; «+ Añadir más productos», notas opcionales y total |
| 4. Tus datos | Nombre, teléfono (opcional) y email, con ejemplos en el placeholder. Los errores salen en palabras sencillas solo después de pulsar Continuar |
| 5. ¿Cuándo lo recoges? | Los 7 próximos días en dos filas (Hoy / Mañana / nombre del día). Al tocar un día **baja solo** hasta las horas, agrupadas en Por la mañana / tarde / noche (completas tachadas con «Completo», pasadas ocultas). El botón «Revisar y confirmar» abre el panel final |
| Revisa y confirma | Panel con recogida (día, fecha, hora, negocio y dirección), tus datos, productos con sus "sin …", notas y total («se paga al recoger»). Cada bloque tiene «Cambiar», que lleva a su paso. Abajo, «✓ Confirmar pedido» |
| Pedido hecho | Número de pedido grande («díselo al recogerlo»), cuándo, dónde, total, líneas y teléfono del negocio. «Hacer otro pedido» vuelve al paso 2 conservando los datos |

**Diseño y usabilidad:**

- Tema **claro de alto contraste solo en esta página** (variables `--p-*` en `:host`; el panel de gestión sigue en tema oscuro). Texto base de 18px, botones y zonas táctiles de 44–48px o más, campos con letra ≥ 16px (el móvil no hace zoom al escribir).
- **Minimalista en el móvil:** cabecera fija de una línea (icono · negocio · calle, cortada con "…") y solo las bolitas de progreso (✓ en los pasos hechos; se puede tocar un paso hecho para volver). Títulos e instrucciones de paso pequeños para que el protagonismo sea de productos y categorías.
- **Barra inferior fija** con Atrás / Continuar. Si no se puede seguir, explica por qué ("Añade al menos un producto para continuar."). En el paso 2 muestra el carrito (🛒 N productos · total).
- **Paneles que suben desde abajo** (`.sheet`) para el producto, quitar ingredientes y el resumen final; en escritorio salen centrados. Se cierran con ✕ o tocando fuera.
- Iconos de lápiz y papelera en **SVG** (no emojis: 🗑 no se ve en todos los móviles).
- Al cambiar de paso: scroll arriba y foco en el título (lectores de pantalla). `aria-label` en los botones de icono y en las cantidades.
- El progreso (negocio, paso, carrito, datos y notas) se guarda en `sessionStorage` (`pangestion.public-order`) por si se recarga la página; el día y la hora no, porque pueden haber pasado. Si el envío falla (normalmente porque la hora se ha llenado), se muestra el error y se recargan las horas.

**Piezas del componente** (para tocarlo rápido):

- Estado con signals: `step`, `cart` (`CartLine {key, product, quantity, removed[]}`), `detail` (panel del producto), `customizing` (panel de quitar ingredientes del paso 3), `reviewing` (panel final), `customer`, `date`, `time`, `slots`.
- `blockedReason` (computed) decide si se puede continuar y qué texto mostrar; `customerErrors` valida los datos; `slotGroups` agrupa las horas.
- Paso 2: `openDetail` → `toggleDetailIngredient` / `changeDetailQuantity` → `addFromDetail` (suma a una línea igual o crea otra).
- Paso 3: `changeLine`, `removeLine`, `openCustomize` → `applyCustomize` (se aplica a N unidades; si son menos que la línea, las separa en otra).
- Paso 5: `selectDate` → `loadSlots(true)` (baja a las horas cuando llegan), `openReview` → `confirm`, `editFromReview(paso)`.
- Navegación: `next`, `back`, `goTo`. Persistencia: `readProgress`, `applyRestore`, `clearProgress`.

**API pública** (`PublicController`, `/api/public`, sin token): `GET /companies`, `GET /companies/{id}/slots?date=`,
`GET /companies/{id}/categories`, `GET /companies/{id}/products` (incluye `ingredients` con su id y `allergens`),
`POST /orders` (cliente por email dentro del negocio; líneas con `removedIngredientIds`).

### Quitar ingredientes ("sin cebolla") de punta a punta

1. **Catálogo:** `Product` ↔ `Ingredient` (ManyToMany). Los ingredientes describen de qué está hecho el producto y sus alérgenos; no controlan stock.
2. **Pedido (web pública y panel):** cada línea lleva `removedIngredientIds`. En el carrito, cada combinación distinta es **una línea aparte** con su cantidad: "2 × Vegetal" y "1 × Vegetal sin cebolla". Las líneas iguales se juntan.
3. **Base de datos:** tabla `order_line_removed_ingredients` (`order_line_id`, `ingredient_name`). Se guarda el **nombre** a propósito: si luego se renombra o borra el ingrediente, el pedido antiguo sigue diciendo lo que se pidió. Todos los negocios usan las mismas tablas, separados por `company_id`.
4. **Cocina / obrador:** Producción del día agrupa por producto + ingredientes quitados (`productionLabel` en `core/models/order.model.ts`), así que salen filas distintas: "Vegetal 5" y "Vegetal (sin cebolla) 2". También se ve en el detalle del pedido.
5. **Pendiente:** extras con suplemento ("+ bacon 1 €"), que necesitarían las tablas `product_extras` y `order_line_extras` (ver Pendiente).

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

- Código en inglés; textos de la interfaz en español. Panel de gestión en tema oscuro y web pública en tema claro (Space Grotesk / IBM Plex Sans).
- Web pública: textos cortos y sin tecnicismos ("Elige", "Revisa", "Tus datos"), un paso cada vez y nunca un botón solo con icono sin `aria-label`.
- Backend por capas: Controller → Service → Repository. Reglas de negocio en el servicio.
- Pantallas de gestión: **tarjetas en rejilla + diálogo** para crear/editar; borrar siempre con `ConfirmService` (nunca `confirm()` del navegador).
- Las credenciales reales van en `backend/.env` (no se sube). En producción `JWT_SECRET` es obligatorio.
- Gráficos: SVG propio siguiendo la guía de visualización (colores validados para daltonismo, un solo eje, tooltip y vista de tabla).

## Guía rápida para próximos cambios

| Quiero cambiar… | Dónde |
|---|---|
| Pasos, textos o validaciones de la web pública | `features/public-order/public-order-page.component.ts` (`steps`, `blockedReason`, `customerErrors`) y su `.html` |
| Aspecto de la web pública | `public-order-page.component.scss` (colores en `:host`, `--p-*`; móvil primero, `@media` al final) |
| Lo que devuelve la API pública | `backend/.../controller/PublicController.java`, `dto/ProductDto.PublicResponse`, y alinear `core/models/product.model.ts` (`PublicProduct`) |
| Crear pedidos / reglas de franjas | `backend/.../service/OrderService.java` (aforo por franja, horario de la empresa) |
| Pantalla de producción | `features/production/` (agrupa producto + "sin …") y `OrderService.production` |
| Estilos del panel de gestión | `src/styles.scss` (compartidos) y el `.scss` de cada pantalla |
| Datos de ejemplo | `config/DataInitializer.java`, `resources/seed/el-mosset.json`, `config/DemoOrdersGenerator.java` |

- **Presupuesto de estilos:** `angular.json` limita el SCSS de cada componente a 14 kB (aviso) / 16 kB (error). La web pública es la más grande (~13 kB ya compilado; el `.scss` fuente ocupa ~19 kB).
- **Probar la web pública:** abrirla a tamaño móvil (375 × 812), hacer un pedido completo y después borrar el pedido y el cliente de prueba (con `admin@admin.es` o `admin2@admin.es`: `DELETE /api/orders/{id}` y `DELETE /api/customers/{id}`) para no ensuciar los datos de demo.
- El servidor de Angular a veces se queda colgado tras un error de compilación: pararlo y volver a arrancarlo.

## Historial de cambios

| Fecha | Commit | Cambio |
|---|---|---|
| 2026-10-14 | `629ce3e` | Backend Spring Boot (Hibernate crea la BD) y panel Angular con las primeras pantallas |
| 2026-10-18 | `03b0988` | Multiempresa con SUPERADMIN, tipo de negocio, carta de El Mosset, catálogo de Oberta con fotos, nuevo flujo de pedido, quitar ingredientes en el panel, gráfica semanal, producción del día, descargar pedidos y pasar a "en proceso" |
| 2026-10-18 | `1b6487e` | Generador de pedidos de demo y copia de la BD en `backups/` |
| 2026-10-21 | `b8c16d2` | Web pública rediseñada: asistente de 5 pasos, móvil primero, tema claro accesible, quitar ingredientes desde la web, progreso en `sessionStorage` |
| 2026-10-22 | `e546367` | Web pública: tarjetas compactas + panel del producto, variantes "sin …" como líneas aparte, móvil minimalista (cabecera de una línea, iconos SVG, días en dos filas con scroll a las horas) y panel final «Revisa y confirma» |

## Pendiente / ideas

- Horario por día de la semana y con tramos partidos (El Mosset: mañanas y noches, distinto lunes y domingo).
- Extras con suplemento ("ingrediente extra +1 €") y precios por día (pizzas a 6 € los miércoles).
- Reparto a domicilio con zonas y pedido mínimo.
- Nombres repetidos entre categorías ("Barbacoa" pizza/bocadillo/hamburguesa): mostrar la categoría en el detalle del pedido.
- Redimensionar imágenes al subirlas; almacenamiento S3/R2 al desplegar.
- Paginación en listados grandes, tests de integración de la API y despliegue (VPS con Docker + PostgreSQL).
- Quitar los usuarios de prueba del login antes de publicar.
