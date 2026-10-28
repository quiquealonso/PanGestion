# Contexto del proyecto · PanGestión

> Leer este archivo antes de tocar el proyecto. Resume qué es, cómo está montado y qué decisiones se han tomado.
> El detalle técnico del backend (modelo de datos, reglas y endpoints) está en `backend/README.md`.

## Qué es

Aplicación web de **pedidos y gestión para negocios de hostelería** (panaderías, bares, restaurantes).
Nació de la propuesta `PanGestion.pdf` (panadería familiar) y se ha generalizado a **multiempresa**:

- **Panel de gestión (backoffice):** cada negocio gestiona su catálogo, clientes, pedidos, producción y usuarios.
- **Web pública (`/pedido`):** asistente para el móvil: el cliente elige negocio y productos (pudiendo quitar ingredientes), pone sus datos, elige día y hora de recogida, revisa y confirma.
- **Sala (solo bares y restaurantes):** plano de mesas editable, comandas por rondas desde el móvil del camarero,
  pantalla táctil de cocina/barra con estado por producto, avisos en tiempo real, cobro en barra con precuenta e informes.
  Ver «Bares y restaurantes: sala, comandas, cocina y cobro».

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

- Copia de trabajo en Windows: `C:\Users\quiqu\Proyectos\PanGestion` (NTFS). Repositorio: `github.com/quiquealonso/PanGestion` (público).
- **Ramas:** se trabaja en **`dev`**. `main` está protegida con el ruleset «main protegida»: solo entra por pull request
  (desde `dev`), con el check **«Compilar y probar»** en verde y sin borrado ni force-push.
- **CI** (`.github/workflows/ci.yml`): en cada PR y en cada push a `main`, Java 21 y `./mvnw -B verify -Dgroups=reglas-negocio`
  en `backend/`: compila todo pero **solo ejecuta los tests de la sesión 17** (`@Tag("reglas-negocio")`). El resto se pasa en local. El frontend **no** se comprueba en CI: ejecutar `npm run build` antes del PR.
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
| `camarero@pangestion.local` | `camarero123` | CAMARERO del bar El Mosset (empieza en «Servicio de mesas») |
| `cocina@pangestion.local` | `cocina123` | COCINA del bar El Mosset (empieza en «Cocina y barra») |
| `repartidor@pangestion.local` | `repartidor123` | REPARTIDOR de El Mosset (empieza en «Reparto») |

> Camarero y cocina los crea `DataInitializer` solo en una BD nueva. En la BD local de este PC se crearon a mano
> (`POST /api/users` con `admin2@admin.es`) con las mismas credenciales.

## Multiempresa (decisión clave)

- Cada **empresa** (`Company`) es un negocio independiente y su único punto de recogida (dirección, horario, franjas). No hay "tiendas".
- Categorías, ingredientes, productos, clientes, pedidos y usuarios pertenecen a una empresa.
- El JWT lleva `companyId`; los servicios filtran siempre por él con `security/CurrentUser`. Pedir un id de otra empresa da 404.
- **Tipo de negocio** (`BusinessType`: PANADERIA, BAR, RESTAURANTE). Solo lo cambia el SUPERADMIN. BAR y RESTAURANTE activan la
  parte de **sala** (mesas, comandas, cocina, cobro e informes); el usuario lleva `companyType` en el login
  (`UserDto.Response`) y el menú lo usa (`AuthService.hasDining`). Si se cambia el tipo, hay que volver a entrar.
- Roles:
  - `SUPERADMIN`: sin empresa; da de alta empresas y usuarios de cualquiera. No trabaja con catálogo ni pedidos.
  - `ADMIN`: todo en su empresa, incluidos sus usuarios y los datos/horario de la empresa.
  - `GESTOR`: pedidos, producción, catálogo y clientes.
  - `VISOR`: solo lectura (pensado para la pantalla de producción del obrador). En bares y restaurantes también ve los informes de sala.
  - `CAMARERO` (solo bares y restaurantes): abre mesas, toma comandas, sirve lo que está listo y cobra. Fuera de la sala, solo lectura; no ve informes.
  - `COCINA` (solo bares y restaurantes): solo la pantalla de cocina y barra.
  - `REPARTIDOR` (cualquier negocio que haga envíos): solo la pantalla de reparto (pedidos a domicilio).
  - Un ADMIN de panadería no puede crear camareros ni cocina (409).
- Web pública: los clientes se identifican por email **dentro de cada negocio** (el mismo email en dos negocios = dos clientes).

## Datos de ejemplo

- **Empresa 1 (panadería):** catálogo real copiado de Oberta (10 categorías, 37 productos con abreviatura, color y orden; 9 fotos en `backend/src/main/resources/seed-images/`). En la BD local se renombró a "Forn Campanero".
- **Empresa 2 (bar El Mosset, Alberic):** carta completa (7 categorías, 85 productos, 74 ingredientes con alérgenos) en `backend/src/main/resources/seed/el-mosset.json`, que `DataInitializer` lee al crear la BD.
  Además, `DataInitializer` le añade una categoría **Bebidas** (Caña, Refresco, Agua, Café) que se prepara en **barra**,
  el **plano de la sala** (Interior con 10 mesas y 3 taburetes de barra, Terraza con 6 mesas) y los usuarios camarero y cocina.
  Todo esto solo aparece al crear la BD desde cero: la BD local de este PC no lo tiene (el plano y los usuarios se hicieron a mano).
- Los alérgenos solo se pusieron donde eran evidentes: hay que revisarlos con cada negocio.

### Pedidos de demostración y copias de la BD

- La panadería (empresa 1) tiene ~310 pedidos generados del 12 al 24 de octubre de 2026 (12–17 entregados, 19–24 pendientes) con `config/DemoOrdersGenerator` (ver `backend/README.md`).
- Copias de la BD (H2 + `uploads/`) en el SSD (no se suben a Git porque el repo es público; para usarlas se copian a `backups/`, que está en `.gitignore`; ver README "Empezar en otro ordenador"): `D:\2DAW\DesarrolloServidor\PanGestion-backups\`. Para restaurar: parar el backend y copiar `data/` y `uploads/` del zip dentro de `backend/`.

## Funcionalidades

### Panel de gestión

| Pantalla | Ruta | Qué hace |
|---|---|---|
| Login | `/auth/login` | JWT; lista de usuarios de prueba con "Usar" y copiar (solo desarrollo) |
| Inicio | `/dashboard` | **Panadería:** 6 indicadores (pendientes, en proceso, listos, recogidas hoy, facturado del mes, clientes), gráfica de unidades vendidas por día (semana vs semana anterior, navegable) y ranking de más vendidos. **Bar o restaurante:** inicio propio (`dashboard/dining-home.component`): la sala ahora mismo (mesas ocupadas y % de la sala, comensales, listo para servir, cola de cocina y de barra, para llevar de hoy), caja de hoy (cobrado, efectivo/tarjeta, mesas cerradas, ticket medio), accesos según el rol y, si ve la facturación, lo facturado y lo más vendido en los últimos 7 días. Se actualiza en directo |
| Pedidos | `/orders` | Día seleccionado (hoy por defecto) o todos; tarjetas agrupadas por día y hora; detalle en diálogo (cambiar estado, editar, eliminar); **Descargar pedidos** (`.txt` con `producto: cantidad`); **Pasar a en proceso** (pendientes del día) |
| Producción del día | `/production` | Tabla producto × franja horaria (1–4 h), totales, franja actual marcada, "Por entregar"/"Todo el día", oculta franjas vacías, se actualiza cada 30 s, pantalla completa |
| Nuevo / editar pedido | `/orders/new`, `/orders/:id/edit` | Flujo tipo caja: 1) productos por pestañas con carrito flotante, 2) resumen editable con **personalización** (quitar ingredientes, variantes), 3) cliente, 4) día y franja + confirmación |
| Clientes | `/customers` | 6 estadísticas (clientes, nuevos por semana, activos 30 días, pedidos por cliente, gasto medio y ticket, mejor cliente); filtros por tipo, nº de pedidos y gasto, y orden; tarjetas con nº de pedidos, total gastado y último pedido (sin correo); diálogo con selector Particular/Negocio (razón social y CIF solo si es negocio) e historial de pedidos |
| Productos | `/catalog/products` | Bloques por categoría, tarjetas con foto, descripción y precio; diálogo con abreviatura, color propio, foto, ingredientes (chips), y en bares y restaurantes **«Se prepara en»** (cocina, barra o no se prepara); activar/desactivar; ordenar con ◀ ▶ |
| Categorías | `/catalog/categories` | Tarjetas; al pulsar, lista de sus productos y "Editar"; ordenar con ◀ ▶. En bares y restaurantes, **«Se prepara en»** de toda la categoría (Bebidas → barra) |
| Ingredientes | `/catalog/ingredients` | De qué está hecho cada producto y su alérgeno (no controla stock); "Lo llevan"; **precio del extra** (vacío = no se ofrece como extra) |
| Orden en cocina | `/catalog/kitchen-order` | Solo bares y restaurantes: orden de las categorías dentro de cada ticket de cocina (▲ ▼). No cambia el orden de la carta |
| Mi empresa | `/company` | Datos, dirección y horario de recogida (solo ADMIN edita) |
| Usuarios | `/users` | SUPERADMIN: todos agrupados por empresa; ADMIN: los de su empresa |
| Empresas | `/companies` | Solo SUPERADMIN: alta y edición de negocios (tipo, CIF, horario, franjas) |
| Sala y mesas | `/dining` | Bares y restaurantes. Editor del plano: zonas en pestañas y mesas que se arrastran, se redimensionan desde la esquina y se editan (nombre, plazas, forma, girar, duplicar, borrar; flechas y Supr con el teclado). Ver la sección de sala |
| Servicio de mesas | `/service` | Plano en vivo (o lista) con las mesas ocupadas, comensales, minutos y lo que está listo; abrir mesa juntando otras |
| Comanda | `/service/:id` | Móvil del camarero: carta, «por enviar» y «pedido» (lista única, sin separar por comensal) con el estado de cada producto; enviar ronda, servir, anular, comensales y notas, juntar/cambiar mesa |
| Barra y cobros | `/bar` | Plano en vivo, cuenta de la mesa, cobro (todo, entre comensales o a partes iguales; efectivo con cambio o tarjeta), precuenta de 80 mm y caja del día |
| Reparto | `/delivery` | Pedidos a domicilio de hoy para el móvil del repartidor: «Para llevar», «Míos», «En cocina», «Entregados»; al tocar uno, «Me lo llevo» (pasa a Transportando a su nombre), «Entregado», «No me lo llevo», mapa y llamar |
| Cocina y barra | `/kitchen` | Pantalla táctil a todo el alto: Pendiente y Preparando se reparten el ancho según sus tickets (hasta 3 por fila), Listo es una columna estrecha. Pestañas Cocina / Barra, tickets de mesa y para llevar. Tocar un producto pasa **una unidad**; cada fila lleva el color y la inicial de su categoría |
| Informes de sala | `/dining-report` | Cobrado, mesas, comensales, ticket medio, duración, ventas por día y hora, más vendidos, camareros, uso de mesas y tiempos de cocina |

**El menú depende del tipo de negocio.** Panadería: Operaciones (Inicio, Pedidos, Producción del día, Nuevo pedido, Clientes).
Bar o restaurante: **Sala** (Inicio, Servicio de mesas, Barra y cobros, Cocina y barra, Informes de sala, según el rol) y
**Para llevar** (Pedidos, Nuevo pedido, Clientes); «Producción del día» no sale porque la sustituye la pantalla de cocina, y
«Sala y mesas» está en Empresa. El camarero y la cocina solo ven su parte. Los textos que decían «panadería» se adaptan al negocio.

Además, el panel tiene **modo claro y oscuro**: en el menú del usuario (sus iniciales, arriba del menú lateral, junto a «Cerrar sesión»). Por defecto sigue
el del sistema y se recuerda en el navegador (`localStorage`, `pangestion.theme`). Ver «Convenciones».

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

- Tema **claro de alto contraste solo en esta página** (variables `--p-*` en `:host`, independiente del modo claro/oscuro del panel de gestión). Texto base de 18px, botones y zonas táctiles de 44–48px o más, campos con letra ≥ 16px (el móvil no hace zoom al escribir).
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

## Bares y restaurantes: sala, comandas, cocina y cobro

Se hizo en 6 fases a partir de esta petición: «el camarero toma pedidos en una mesa exacta identificando comensales;
en barra está el mapa de mesas, donde se cobra; en cocina una pantalla táctil para pasar los productos hacia delante;
los camareros reciben un aviso cuando cocina tiene algo listo». Todo es **solo para empresas BAR y RESTAURANTE**
(el backend devuelve 409 a una panadería y el menú no lo muestra). Después de las 6 fases se ajustó (28-10-2026): ver
«Ajustes del 28-10-2026» más abajo. El más importante: **los productos ya no se apuntan por comensal**.

### Decisiones tomadas

| Decisión | Qué se eligió | Por qué |
|---|---|---|
| Juntar mesas | Solo durante el servicio: el servicio ocupa varias mesas; al liberarlo vuelven a estar separadas. El plano no cambia | En el día a día se juntan y separan mesas constantemente; tocar el plano sería un lío |
| Comensal | **Solo un número de la mesa**, para dividir la cuenta al cobrar. Los productos son de toda la mesa: no se apunta de quién es cada uno. (Al principio era opcional por producto; se quitó el 28-10-2026) | Separar lo de cada comensal complicaba la comanda y la cocina y no se usaba: solo hace falta para repartir el ticket final |
| Estaciones | Cada producto se prepara en `COCINA`, `BARRA` o `NINGUNA` (no se prepara: sale ya listo) | Las bebidas no deben llenar la pantalla de cocina |
| Modelo de mesa | `TableSession` + `ServiceLine` propios, **sin tocar** `Order` / `OrderLine` | El pedido para llevar exige cliente, fecha y franja; mezclarlo rompía aforos, listados y estadísticas |
| Estado | Por **producto** (`ItemStatus`), no por pedido; el pedido para llevar sigue a sus productos | Un plato puede estar listo y otro no |
| Ticket | **Precuenta no fiscal** («Documento no válido como factura») | Un ticket legal exige cumplir Veri\*factu (obligatorio desde 2027); queda fuera |
| Tiempo real | Server-Sent Events (`GET /api/events`) leídos con `fetch` | Más simple que WebSocket, va por el mismo HTTP y el token va en la cabecera (no en la URL) |
| ¿Dos apps? | **No.** Una sola app; la parte de sala se activa por tipo de negocio | Comparten login, usuarios, catálogo, clientes, pedidos para llevar, web pública y CI. Separarlas duplicaría el mantenimiento. Ver «Pendiente» (separar por módulos dentro de la misma app) |

### Fase 1 · Plano de la sala (`/dining`)

- **Modelo:** `DiningArea` (zona: nombre, ancho × fondo en casillas, orden) y `DiningTable` (nombre único en la empresa, plazas,
  forma `ROUND`/`RECT`, x, y, ancho, alto en casillas). Una casilla es más o menos medio metro.
- **Reglas** (en el modelo, probadas en `DiningModelTest`): la mesa cabe en su zona (`fits`) y no pisa a otra (`overlapping`);
  no se encoge una zona dejando mesas fuera; no se borra una mesa ni una zona con mesas ocupadas.
- **Editor:** pestañas de zonas, botones «Redonda / Cuadrada / Alargada / Taburete» que colocan la mesa en el primer hueco
  libre dejando una casilla de pasillo, arrastrar (encaja en la cuadrícula), esquina para redimensionar, panel lateral,
  teclado (flechas, Supr, Esc). Si no cabe o pisa otra mesa, vuelve atrás y avisa.
- **Guardado optimista con cola por mesa:** el cambio se ve al momento y al servidor va una petición por mesa cada vez
  (si llegan varias seguidas, al acabar se envía la última). Así varios clics rápidos en «+» suman bien. Si el servidor
  lo rechaza, se recarga el plano.
- **Dibujo de la mesa:** componente compartido `shared/dining-table.component.ts` (tablero, nombre, sillas alrededor según
  plazas y forma). Las medidas escalan con el plano por `--cell` (CSS container queries, `100cqw / --cols`).

### Fase 2 · Servicio de mesas y comanda (`/service`, `/service/:id`)

- **Modelo:** `TableSession` (servicio: mesas que ocupa, `tableLabel` «4 + 5», comensales, notas, camarero, estado `OPEN`/`CLOSED`,
  rondas, abierta/cerrada a las) y `ServiceLine` (producto, cantidad, precio copiado, notas, ingredientes quitados, ronda;
  **sin comensal**: es de toda la mesa).
- **Reglas:** una mesa solo puede estar en un servicio abierto; un servicio cerrado no admite cambios; el número de comensales
  se cambia cuando haga falta, **salvo si algún comensal ya ha pagado su parte** (409: las partes dejarían de cuadrar). Al cerrar,
  las mesas se liberan y queda el nombre (`tableLabel`) en el historial.
- **Plano en vivo:** mesas libres / ocupadas (ámbar, con comensales y minutos) / con algo listo (verde que late). Vista de lista para el móvil.
  Abrir mesa: comensales (por defecto, las plazas), «Juntar con» otras mesas libres de la zona y notas.
- **Comanda (móvil):** carta con categorías y buscador, toque = +1 (globo con lo apuntado), editar línea (quitar ingredientes,
  notas), «Enviar a cocina» por rondas, barra fija para enviar sin cambiar de pestaña. «Por enviar» y «Pedido» son listas únicas
  (lo pedido lleva su ronda R1, R2…). El número de comensales se cambia en «Comensales y notas». Lo apuntado y no enviado se
  guarda en `sessionStorage` (`pangestion.draft.<id>`).
- **Rol CAMARERO** y redirección: el camarero entra directamente en `/service` y solo ve la sala.

### Fase 3 · Cocina y barra (`/kitchen`)

- `Product.station` (cocina, barra, ninguna). Cada línea (de mesa y de pedido para llevar) **copia la estación** al pedirse y tiene
  `status` (`PENDIENTE → PREPARANDO → LISTO → SERVIDO`) y `statusChangedAt`. Lo que no se prepara nace `LISTO`.
- **Reglas:** solo se avanza o retrocede un paso (`ItemStatus.canMoveTo`); un pedido para llevar no se «sirve» (se entrega desde
  Pedidos) y su estado sigue al de sus productos (`Order.syncStatusFromLines`); el camarero solo anula lo que cocina no ha empezado.
- **Pantalla:** 3 columnas; cada ticket es una ronda de mesa o un pedido para llevar de hoy (borde azul / ámbar). Tocar un producto
  pasa **una unidad** a la columna siguiente (3× Calzone → quedan 2× y 1× pasa), «Todo →» (en la cabecera, entre la mesa y los
  minutos) pasa el ticket entero y ↩ devuelve una unidad. Minutos desde que se pidió (aviso a los 10, tarde a los 20); en los de
  llevar, la hora de recogida (aviso a 15 min, tarde si ya pasó). Pantalla completa. **Rol COCINA**.
- **Una unidad cada vez:** `PATCH /api/kitchen/items` acepta `units` por producto. Si no son todas, la línea se separa
  (`ServiceLine.split` / `OrderLine.split`: copia precio, ronda, notas, ingredientes, estado y hora de pedido) y, si en el estado de
  destino ya está lo mismo de la misma ronda (`sameDishAs`), se juntan: al acabar vuelve a quedar una sola línea (3× listas,
  no 1× + 1× + 1×). El total de la mesa o del pedido no cambia.
- **Tickets compactos:** cabecera de una línea (Mesa 4 o #577 + cliente, «Todo →» y minutos). Cada fila lleva el color de su
  categoría y su inicial en un recuadro (P de Pizzas): así se distinguen dos productos con el mismo nombre en categorías distintas.
- **El usuario de cocina solo usa su pantalla:** en el panel, `kitchenOnlyGuard` le devuelve a `/kitchen` desde cualquier otra
  ruta; en la API solo tiene `/api/kitchen/**`, `/api/events` y `/api/auth/me` (el resto, ni leer: 403). No recibe los avisos de
  «listo para servir» (son para quien sirve).
- En la comanda, cada producto enviado muestra su estado y los listos tienen «Servir» (y «Servir todo lo listo»).

### Fase 4 · Avisos en tiempo real

- **Backend:** `events/LiveEventsHub` guarda las conexiones SSE por empresa; `events/LiveEvent` con dos tipos:
  `change` (`scopes`: `kitchen`, `floor`, `orders`, `session:{id}`) para que las pantallas se refresquen y `ready`
  (mesa, camarero y productos) para el camarero. Se envían **al confirmarse la transacción** (`@TransactionalEventListener`),
  con latido cada 25 s (`SchedulingConfig`). Las peticiones `ASYNC` se permiten en `SecurityConfig` (ya se autorizó al abrirlas).
- **Frontend:** `core/services/live-events.service.ts` lee el flujo con `fetch` (token en la cabecera), reconecta solo
  (1, 2, 5, 10, 15 s) y expone `on(scope)` y `ready$`. El shell lo arranca en bares y restaurantes.
- `shared/ready-alerts.component.ts`: aviso «🔔 Mesa 4 · 2× Burger · Listo para servir» con pitido (`shared/sound.ts`) y
  vibración; tocarlo lleva a la comanda. Con la pestaña oculta y permiso concedido («🔔 Activar avisos»), notificación del sistema.
- Cocina pita al llegar un ticket nuevo. Las pantallas muestran «En directo / Reconectando…» y dejan un refresco lento de respaldo.

### Fase 5 · Barra y cobros (`/bar`)

- **Modelo:** `Payment` (importe, `EFECTIVO`/`TARJETA`, comensal, texto «Total / Comensal 2 / Parte de 3», efectivo entregado, cajero, hora).
  `TableSession.getPaid()`, `getPending()` y `guestShares()`.
- **Reparto entre comensales:** el total de la mesa a partes iguales entre sus comensales (`guestShares`); los céntimos sobrantes
  los paga el comensal 1, así la suma es siempre el total (25 € entre 3: 8,34 + 8,33 + 8,33). Cada comensal que paga queda marcado.
- **Reglas:** no se cobra más de lo pendiente; un comensal paga exactamente su parte y una sola vez; en efectivo lo entregado cubre
  el importe; **no se libera una mesa con algo pendiente**; con algo cobrado, no se anula un producto si el total quedaría por debajo.
  Un cobro hecho por error se puede anular con la mesa abierta.
- **Pantalla:** chips de mesas abiertas, plano en vivo, cuenta (productos con estado, total, cobrado, pendiente, cobros),
  diálogo de cobro (Todo / Entre comensales / A partes iguales; efectivo con «Exacto» y billetes redondeados hacia arriba y cambio; tarjeta;
  «Liberar la mesa si queda pagada») y **caja del día** (efectivo, tarjeta, mesas cerradas; elegir día).
- **Precuenta:** bloque `.receipt` que solo se ve al imprimir (`@media print` en `styles.scss`, 80 mm): negocio, CIF, mesa, fecha,
  camarero, productos agrupados, total, cobros, pendiente, «IVA incluido · Documento no válido como factura». Funciona con
  cualquier impresora instalada en el sistema, también las térmicas de 80 mm.

### Fase 6 · Informes de sala (`/dining-report`)

- `GET /api/reports/dining?from&to` (ADMIN, GESTOR y VISOR; camarero y cocina no ven la facturación). Por defecto, últimos 7 días; máximo un año.
- Cobrado (de los pagos, por su fecha) y, de las mesas cerradas en el periodo: mesas, comensales, ticket medio por mesa y por
  comensal, duración media, facturado por día (todos los días, aunque estén vacíos) y por hora de llegada, 10 más vendidos,
  camareros, uso de cada mesa (si estuvo junta, lo facturado se reparte) y tiempos de cocina/barra (de pedido a listo).
- Para los tiempos, `ServiceLine.readyAt` guarda cuándo quedó listo (se borra si se devuelve a preparación).
- Gráficos con `shared/simple-bar-chart.component.ts`: una serie, color primario, tooltip y «Ver como tabla».

### Ajustes del 28-10-2026 (después de las 6 fases)

Cambios pedidos al usar la app. Cada apartado dice qué hace, cómo está hecho, sus reglas y dónde tocar para cambiarlo.

#### 1. Menú lateral plegable y menú del usuario (todo el panel)

- **Qué:** una flecha `‹ / ›` arriba a la derecha del menú lateral lo pliega a una franja de 4,6 rem con solo las iniciales,
  y lo vuelve a desplegar. Pensado para pantallas fijas (la de cocina): más sitio para el contenido.
- **Se recuerda** en el navegador: `localStorage` `pangestion.sidebar-collapsed` (`true`/`false`). Sin almacenamiento, vale para esa visita.
- **Menú del usuario:** el cuadrado de arriba (antes una «P») muestra las **iniciales del usuario** (dos como mucho: «Pablo
  Admin» → PA, «Kike Cocina» → KC). Al pulsarlo se abre un menú con rol, nombre, email, **modo claro/oscuro** y **Cerrar sesión**.
  Se cierra con un clic fuera o con Escape. El cuadro que había abajo del menú lateral (nombre, tema y salir) ya no existe.
- **Detalle técnico:** el menú se pinta **fuera** del `<aside>` con `position: fixed` (arriba a la izquierda), porque el lateral
  tiene `overflow-y: auto` y `backdrop-filter`, que recortan lo que sobresale (con el lateral plegado no se veía).
- **En el móvil** (menos de 960 px) el lateral va arriba; plegado oculta los enlaces y deja solo la fila de las iniciales.
- **Ficheros:** `layout/shell/shell.component.{html,ts,scss}` (`collapsed`, `menuOpen`, `initials`, `toggleCollapsed`,
  `toggleMenu`, `closeMenu` con `@HostListener` de `document:click` y `document:keydown.escape`).

#### 2. Pantalla de cocina más densa (`/kitchen`)

- **A todo el alto:** `.kds` ocupa `100vh − 3rem`; cada columna hace **scroll por dentro** y su título (con el contador) se queda
  fijo arriba (`position: sticky`). En menos de 960 px vuelve al alto natural y las columnas van una debajo de otra.
- **Tickets compactos:** cabecera de **una línea**: `Mesa 4` (o `#577 Luis` en para llevar), el botón **«Todo →»** en medio
  (entre el nombre y los minutos; solo si hay más de una unidad) y los minutos (o la hora de recogida) a la derecha con su color
  de urgencia. Ya no salen la ronda, los comensales, el camarero ni la etiqueta «Mesa / Para llevar» (para llevar se distingue
  por el borde ámbar). Las notas de la mesa sí salen. Filas y botones más bajos, pero aún cómodos con el dedo.
- **Ancho de las columnas:** lo calcula el componente (`boardColumns`):
  `minmax(0, Nfr) minmax(0, Mfr) var(--ready-width)`, donde N y M son los tickets de Pendiente y Preparando (de 1 a 3).
  Así la columna con más trabajo se ensancha y la otra se estrecha. Dentro de cada columna los tickets se colocan en rejilla
  con **3 por fila como mucho** (`minmax(max(200px, (100% − 1rem) / 3), 1fr)`).
- **Listo para servir:** columna estrecha y fija (`--ready-width: 240px`) con los tickets **uno debajo de otro**.
- **Una unidad por toque:** tocar «3× Calzone» pasa **una** a la columna siguiente y la fila queda en 2×. ↩ devuelve también de
  una en una. «Todo →» pasa todas las unidades del ticket en esa columna.
  - API: `PATCH /api/kitchen/items` con `{"items": [{"source": "MESA", "id": 7, "units": 1}], "status": "LISTO"}`.
    `units` es opcional (sin él, la línea entera). Más unidades de las que quedan: 409 «Solo quedan 2 de Hamburguesa».
  - Backend (`KitchenService.move`): si no son todas, la línea se **separa** (`ServiceLine.split` / `OrderLine.split`, que copian
    precio, ronda, notas, ingredientes, estación, estado, hora de pedido y hora de listo) y la parte nueva cambia de estado.
    Si en el estado de destino ya hay **lo mismo** (`sameDishAs`: mismo producto, precio, ronda, notas, ingredientes y
    estación), se **juntan** en esa línea y la otra se borra. Al terminar vuelve a quedar una sola línea (3× listas, no
    1× + 1× + 1×). El total de la mesa o del pedido no cambia y el pedido para llevar sigue a sus productos.
  - `ServiceLine.createdAt` se conserva al separar (la parte nueva no es «más reciente»), para que los tiempos de cocina sigan bien.
  - El aviso al camarero dice las unidades que se han puesto listas («1× Hamburguesa»).
  - Frontend: el cambio se ve al momento (mismo reparto y juntado que el servidor; la parte separada lleva un id provisional
    negativo y no se puede tocar hasta la recarga, que llega enseguida por el aviso en tiempo real). Si el servidor lo rechaza, se recarga.
  - Cuidado: los ingredientes quitados son una colección de Hibernate (`PersistentBag`) cuyo `equals` compara la instancia;
    `sameDishAs` compara copias (`List.copyOf`). Sin eso no se juntaban nunca.
- **Mismo nombre, distinta categoría:** cada fila lleva el **color de su categoría** (borde izquierdo y fondo suave) y, al final,
  la **inicial de la categoría** en un recuadro de ese color (P de Pizzas, E de Entrantes). El ticket trae `categoryName` y
  `categoryColor` en cada producto (si la categoría no tiene color, se usa el primario).
- **Ficheros:** `features/kitchen/kitchen-page.component.{html,ts,scss}`, `core/models/kitchen.model.ts`,
  `core/services/kitchen.service.ts`, `dto/KitchenDto.java`, `service/KitchenService.java`, `model/ServiceLine.java`,
  `model/OrderLine.java`.
- **Tests:** `KitchenApiTest` (`oneUnitAtATime`, `readyUnits`, `takeawayUnits`, `category`), `KitchenModelTest.split`.

#### 3. Usuario de cocina: solo su pantalla

- El rol **COCINA** ya existía (fase 3). Ahora se le cierra todo lo demás:
  - **Panel:** `kitchenOnlyGuard` (`canActivateChild` del grupo de rutas de empresa) le devuelve a `/kitchen` desde cualquier otra
    ruta; las que no son de empresa (`/users`, `/companies`) ya le mandaban al inicio, que también le lleva a cocina. Su menú
    lateral solo tiene «Cocina y barra».
  - **API** (`SecurityConfig`): solo `/api/kitchen/**`, `/api/events` y `/api/auth/**`. El resto da **403 aunque sea para leer**
    (antes podía leer productos, pedidos, clientes…). Para eso, la regla de `GET /api/**` es «autenticado y no COCINA»
    (`AuthorizationManagers.allOf(authenticated, not(hasRole("COCINA")))`) y las de cocina y avisos van antes.
  - **No recibe los avisos de «listo para servir»** (son para quien sirve: el shell solo los pinta si `canServe`).
- Usuario de prueba: `cocina@pangestion.local` / `cocina123` (sale en la lista del login).
- **Test:** `KitchenApiTest.kitchenOnly`.

#### 4. Sin comensales por producto

- **Qué:** los productos de la mesa ya **no se apuntan a un comensal**. Los comensales son solo el número de personas de la mesa
  y sirven **únicamente para dividir el ticket final**.
- **Comanda:** fuera la fila de chips (Mesa, C1, C2… «+»), el «Se apunta a…» y el «Para» del diálogo de editar línea. «Por
  enviar» y «Pedido» son listas únicas. El nº de comensales se cambia en «Comensales y notas».
- **Cocina y barra:** fuera la marca C1, C2… de cada producto; el aviso al camarero ya no lleva «(C1)».
- **Cobro:** el modo «Por comensal» pasa a llamarse **«Entre comensales»**: el total a partes iguales (`TableSession.guestShares`),
  el céntimo sobrante para el comensal 1, y cada comensal queda marcado al pagar su parte (`Payment.guest`). «A partes iguales»
  sigue para repartir lo pendiente en un número libre de partes (sin marcar a nadie).
- **Regla nueva:** si algún comensal ya ha pagado su parte, no se puede cambiar el número de comensales (409 «Ya ha pagado algun
  comensal su parte: no se puede cambiar el numero de comensales»). Desaparece la regla «no bajar de un comensal con productos».
- **API:** `ServiceDto.LineRequest` y `LineResponse` ya no tienen `guest`; `KitchenDto.Item` tampoco. Un cliente antiguo que lo
  envíe recibe 400 «Campo no permitido: guest» (la API rechaza campos desconocidos). `Payment.guest` y `BillResponse.guests` se quedan.
- **Base de datos:** la columna `service_lines.guest` queda en las BD ya creadas (nula y sin usar; `ddl-auto=update` no borra
  columnas). En una BD nueva no se crea.
- **Ficheros:** `model/ServiceLine.java`, `model/TableSession.java` (`guestShares`, `anyGuestHasPaid`; fuera
  `highestGuestWithLines`), `service/TableSessionService.java` (`update`, `addRound`), `dto/ServiceDto.java`, `dto/KitchenDto.java`,
  `service/KitchenService.java`; `features/service/table-session-page.*`, `features/bar/bar-page.component.html`,
  `features/kitchen/*`, `core/models/table-service.model.ts`, `core/models/kitchen.model.ts`.
- **Tests:** `TableSessionModelTest.guestShares`, `PaymentApiTest` (`bill`, `payByGuest`, `guestsFixedOncePaid`),
  `TableServiceApiTest.changeGuests`.
- **Si algún día se quiere volver a separar por comensal:** habría que recuperar `ServiceLine.guest` (la columna sigue en las BD
  antiguas), el reparto «lo suyo + parte de lo compartido» en `guestShares` y los chips de la comanda (ver el historial de git).

#### 5. Categoría Bebidas en barra («Se prepara en» por categoría)

- **Qué:** una categoría puede decir dónde se preparan **todos** sus productos (`Category.station`: `COCINA`, `BARRA`,
  `NINGUNA` o nulo = lo decide cada producto). «Bebidas» va a **barra**: sus productos salen solo en la pantalla de Barra
  y la cocina no se entera.
- **Reglas:**
  - Al guardar una categoría con estación, **todos sus productos pasan a esa estación** (`CategoryService.apply`).
  - Un producto nuevo o editado en esa categoría toma la estación de la categoría **aunque pida otra**
    (`ProductService.apply`): manda la categoría. En el formulario del producto el desplegable sale bloqueado con
    «Barra (lo marca la categoría)».
  - Si la categoría vuelve a «Lo decide cada producto», sus productos se quedan donde estaban (se pueden cambiar uno a uno).
  - Lo ya pedido no cambia: cada línea copió su estación al pedirse (una caña pedida antes sigue donde estaba).
- **API:** `CategoryDto.Request.station` y `Response.station` (más `kitchenOrder`, ver el punto 6).
- **Datos:** el seed de El Mosset crea «Bebidas» (Caña, Refresco, Agua, Café) con estación BARRA. En la BD local de este PC
  se creó a mano el 28-10-2026 con Caña, Tercio, Refresco, Agua, Copa de vino y Café.
- **Ficheros:** `model/Category.java`, `dto/CategoryDto.java`, `service/CategoryService.java`, `service/ProductService.java`,
  `repository/ProductRepository.findAllByCategoryId`; `features/catalog/categories/*`, `features/catalog/products/*`.
- **Test:** `CategoryApiTest.categoryStation`.

#### 6. Orden de las categorías en cocina (`/catalog/kitchen-order`, «Orden en cocina»)

- **Qué:** dentro de cada ticket de cocina, los productos salen **por categoría en un orden propio de la cocina**:
  primero lo que va antes a la mesa (Entrantes, Ensaladas) y luego los principales (Pizzas, Hamburguesas…). Es **otro orden**,
  aparte del de la carta (`sortOrder`): cambiarlo no mueve nada en Productos, Categorías ni la web de pedidos.
- **Pantalla:** menú Catálogo → «Orden en cocina» (solo bares y restaurantes, `diningGuard`). Lista con ▲ ▼; cada cambio se guarda
  al momento (si falla, vuelve atrás). Las categorías que van a barra o no se preparan llevan «(no sale en cocina)».
- **Modelo:** `Category.kitchenOrder` (entero, 0 = arriba; columna `kitchen_order` con `default 0` para BD ya creadas). Una
  categoría nueva toma como orden de cocina su posición en la carta.
- **API:** `PUT /api/categories/kitchen-order` con `{"ids": [...]}` de arriba abajo (cada una toma su posición); devuelve las
  categorías. Una categoría de otra empresa: 404. Mismos permisos que el resto de `/api/categories` (ADMIN y GESTOR).
- **Cocina:** `KitchenDto.Item.categoryOrder` = `kitchenOrder × 1000 + sortOrder` (a igualdad de orden de cocina, el de la
  carta). `KitchenService` ordena los productos de cada ticket por ese número y, dentro, como se pidieron; el panel lo vuelve a
  ordenar al repartirlos por columnas (también tras un cambio optimista).
- **Datos:** El Mosset queda Entrantes, Ensaladas, Pizzas, Hamburguesas, Hamburguesas premium, Bocadillos, Platos combinados y
  Bebidas (seed y BD local).
- **Ficheros:** `model/Category.java`, `service/CategoryService.reorderKitchen`, `controller/CategoryController`,
  `dto/KitchenDto.java`, `service/KitchenService.java`; `features/catalog/kitchen-order/kitchen-order-page.component.ts`,
  `core/services/categories.service.ts` (`reorderKitchen`), ruta en `app.routes.ts`, enlace en el shell.
- **Tests:** `CategoryApiTest.kitchenOrder`, `KitchenApiTest.kitchenOrderAndExtras`.

#### 7. Extras con precio («con huevo», +1,00 €) en todos los pedidos

- **Qué:** además de quitar ingredientes, a un producto se le pueden **añadir extras** que suman su precio. Vale para la
  comanda de la mesa, los pedidos para llevar del panel y la web pública. Ejemplo: Barbacoa (8,50 €) sin cebolla y con huevo
  (+1,00 €) = 9,50 € la unidad.
- **Qué es un extra:** un ingrediente con **precio de extra** (`Ingredient.extraPrice`, en Ingredientes: «Precio del extra»).
  Vacío = no se ofrece como extra; 0 = extra gratis. Se ofrecen todos los extras de la empresa (no hace falta que el producto
  lleve ese ingrediente: a una pizza se le puede añadir huevo), pero **solo a productos que tienen ingredientes** (una bebida no
  admite extras). Cada extra va una vez por línea (no hay «doble huevo»).
- **Cómo se guarda:** cada línea (`OrderLine` y `ServiceLine`) tiene `extras`, una lista de `LineExtra` (nombre y precio **del
  momento**, tablas `order_line_extras` y `service_line_extras`), igual que el precio del producto: si mañana cambia el precio
  del huevo o se borra el ingrediente, lo pedido no cambia. El **precio unitario de la línea ya incluye los extras**
  (`unitPrice = precio del producto + suma de extras`), así que totales, cobros, caja e informes no cambian de lógica.
- **Juntar líneas:** «pizza», «pizza sin cebolla», «pizza con huevo» y «pizza con huevo sin cebolla» son líneas distintas.
  Al pedir (`OrderService.setLines`) y en cocina (`sameDishAs`, al separar y juntar unidades) se comparan también los extras.
  La hoja de producción de la panadería también los separa.
- **API:**
  - Petición de línea (pedidos, web pública y rondas de mesa): `addedIngredientIds` junto a `removedIngredientIds`.
    `{"productId": 7, "quantity": 2, "removedIngredientIds": [3], "addedIngredientIds": [12]}`.
  - Errores: ingrediente sin precio de extra → 409 «Queso no se puede anadir como extra»; producto sin ingredientes →
    409 «El producto Bravas no admite extras»; ingrediente de otra empresa → 404.
  - Respuestas: las líneas de pedido y de mesa traen `extras: [{"name": "Huevo", "price": 1.00}]`; en cocina,
    `extras: ["Huevo"]` (solo nombres). La hoja de producción trae `extras` por fila.
  - `GET /api/ingredients` trae `extraPrice`; la web pública pide los extras con `GET /api/public/companies/{id}/extras`
    (`[{id, name, allergen, price}]`, sin login).
- **Validación:** `ProductService.extras(product, ids)` (la usan `OrderService` y `TableSessionService`): mismo negocio que el
  producto, con precio de extra, producto con ingredientes; los ordena por nombre.
- **Pantallas:**
  - Componente compartido `shared/extras-picker.component.ts`: chips «+ Huevo +1,00 €» que se marcan (verde, ✓). Funciones
    `extrasPrice`, `customizationText` («con huevo, sin cebolla») y `toggleId`.
  - **Comanda:** en el diálogo de cada línea apuntada, debajo de «Ingredientes (toca para quitar)», los extras. El precio de la
    línea y el total por enviar ya los suman. Lo pedido muestra «con huevo · sin Cebolla».
  - **Nuevo pedido (panel):** en «Personalizar» / «Otra variante», los extras; precio por unidad y total con extras. Al editar
    un pedido, los extras guardados se recuperan por nombre.
  - **Web pública:** en la ficha del producto, «¿Algo más?» con los extras; también al cambiar una línea del carrito
    («Desmarca lo que no quieres y marca lo que quieras añadir»). El carrito guardado en `sessionStorage` lleva los extras.
  - **Cocina:** cada producto muestra lo que **se añade** en verde («+ Huevo») y lo que **se quita** en rojo («sin Cebolla»),
    en chips debajo del nombre, para que se vea de un vistazo. Las notas siguen en ámbar.
  - **Barra:** la cuenta muestra «con huevo, sin cebolla» bajo el producto; la precuenta agrupa «Barbacoa + huevo» aparte de
    «Barbacoa».
  - **Detalle del pedido:** «con huevo (+1,00 €)» en verde y «sin cebolla» en naranja.
- **Datos:** extras de El Mosset (seed y BD local): Huevo 1,00 €, Bacon 1,50 €, Cheddar 1,00 €, Mozzarella 1,00 €,
  Champiñón 0,80 €, Jamón serrano 1,50 € y Cebolla 0,50 €.
- **Ficheros:** `model/LineExtra.java` (nuevo), `model/Ingredient.java`, `model/OrderLine.java`, `model/ServiceLine.java`,
  `model/Order.java` (`addLine` con extras), `dto/IngredientDto.java` (`Extra`, `LineExtraResponse`), `dto/OrderDto.java`,
  `dto/ServiceDto.java`, `dto/KitchenDto.java`, `service/ProductService.extras`, `service/OrderService.java`,
  `service/TableSessionService.java`, `service/IngredientService.findExtras`, `controller/PublicController`;
  `shared/extras-picker.component.ts`, `features/service/table-session-page.*`, `features/orders/create-order/*`,
  `features/public-order/*`, `features/kitchen/*`, `features/bar/*`, `features/orders/orders-page.component.html`,
  `features/production/*`, `features/catalog/ingredients/*`.
- **Tests:** `OrderApiTest.extras` y `extrasRules`, `IngredientApiTest.extraPrice`, `PublicApiTest.extras`,
  `KitchenApiTest.kitchenOrderAndExtras` (precio en la mesa y cómo sale en cocina), `KitchenModelTest.extras`.
- **Ideas para más adelante:** limitar qué extras admite cada producto o categoría (hoy, todos los de la empresa a cualquier
  producto con ingredientes); extras con cantidad («doble queso»); precio del extra distinto según el producto.

#### 8. La barra no tiene «Preparando» y «pax» pasa a «pers»

- **Qué:** en la pantalla de **Barra** solo hay dos columnas, **Pendiente** y **Listo para servir**: la bebida no se prepara, se
  pone y se lleva. Tocar un producto pasa una unidad de pendiente a listo (y avisa al camarero, como siempre); ↩ la devuelve a
  pendiente. «Todo →» pasa el ticket entero a listo. La cocina sigue con sus tres columnas.
- **Regla (backend):** `ItemStatus.canMoveTo(target, station)`: en `BARRA` se permite `PENDIENTE ↔ LISTO` además de los pasos de
  uno en uno. Lo usan `ServiceLine.moveTo`, `OrderLine.moveTo` y `KitchenService.check`. En cocina, saltarse «preparando»
  sigue dando 409.
- **Lo que ya estaba «preparándose» en barra** (de antes del cambio) sale en la columna Pendiente y desde ahí pasa a listo.
- **Pantalla:** `kitchen-page.component.ts`: `BAR_COLUMNS` y `columns` según la estación; en barra el ancho es
  `minmax(0, 1fr) var(--ready-width)`.
- **Tests:** `KitchenModelTest.barSkipsPreparing`, `KitchenApiTest.barGoesStraightToReady`.
- **«pax» → «pers»:** las personas de una mesa se escriben «3 pers» (plano, servicio de mesas, barra, caja, precuenta y el
  detalle del ticket de cocina). «Pax» es jerga de hostelería que no todo el mundo entiende.

#### 9. Pedidos a domicilio, rol REPARTIDOR y móvil con menú inferior

**Pedidos a domicilio (todos los negocios)**

- **Tipo de pedido** (`Order.type`, enum `OrderType`): `RECOGER` (por defecto, como hasta ahora) o `DOMICILIO`. A domicilio
  lleva `deliveryAddress` (dirección de entrega). `pickupTime` pasa a ser «hora de recogida o de entrega».
- **El negocio decide si hace envíos:** `Company.delivery` (Mi empresa / Empresas: «Hace envíos a domicilio»). Si no, pedir a
  domicilio da 409 «X no hace envios a domicilio». Sin dirección: 409 «Falta la direccion de entrega».
- **Web pública:** en «Tus datos», si el establecimiento hace envíos, dos botones grandes «🏪 Lo recojo / 🏍️ A domicilio»; a
  domicilio pide la dirección y el teléfono (obligatorio: el repartidor llama al llegar). La confirmación y el «¡Pedido hecho!»
  dicen «Entrega / se paga al recibirlo» y la dirección.
- **Panel (nuevo pedido):** en el paso «¿Cuándo?», «Lo recoge / A domicilio» y la dirección; al editar se recuperan.
- **Pedidos:** la tarjeta dice «🏍️ Entrega a las…»; el detalle muestra la dirección y el repartidor.
- **Cocina:** el ticket de un pedido a domicilio dice «Luis · domicilio 21:30» en lugar de «recoge».
- **API:** `type` y `deliveryAddress` en `OrderDto.Request`, `CustomerOrderRequest` y `PublicRequest`; la respuesta añade
  `type`, `deliveryAddress` y `driverName`. `CompanyDto` añade `delivery` (en la petición, nulo = no cambiarlo).

**Estado TRANSPORTANDO y rol REPARTIDOR**

- Estados del pedido: `PENDIENTE → EN_PROCESO → LISTO → TRANSPORTANDO → ENTREGADO` (o `CANCELADO`). `TRANSPORTANDO` solo para
  pedidos a domicilio (409 «Solo los pedidos a domicilio se transportan»). Desde el panel se puede poner a mano; al volver a un
  estado anterior se quita el repartidor. `Order.syncStatusFromLines` no toca un pedido que ya está transportándose.
- `Order.driver` (repartidor que lo lleva) y `Order.dispatchedAt` (cuándo salió).
- **Rol `REPARTIDOR`** (en cualquier negocio): entra directamente en `/delivery` y solo ve eso. En el panel,
  `kitchenOnlyGuard` le devuelve a `/delivery` desde cualquier otra ruta; en la API solo tiene `/api/delivery/**`, `/api/events`
  y `/api/auth/**` (el resto, 403 aunque sea para leer: `SecurityConfig` niega los GET a COCINA y REPARTIDOR).
- **API de reparto** (`DeliveryController`, `DeliveryService`; ADMIN, GESTOR y REPARTIDOR):
  - `GET /api/delivery`: pedidos a domicilio de hoy (sin cancelados) por hora de entrega, con cliente, teléfono, dirección,
    productos («2× Barbacoa (con huevo, sin cebolla)»), total, notas, repartidor y `mine`.
  - `POST /api/delivery/{id}/take` «Me lo llevo»: `LISTO → TRANSPORTANDO`, queda a su nombre. Si no está listo: 409; si ya lo lleva
    otro: 409 «El pedido #5 ya lo lleva Rafa».
  - `POST /api/delivery/{id}/deliver`: `TRANSPORTANDO → ENTREGADO`. Solo quien lo lleva (un ADMIN o GESTOR también puede).
  - `POST /api/delivery/{id}/release` «No me lo llevo»: vuelve a `LISTO` sin repartidor, para que lo coja otro.
  - Un pedido para recoger no se reparte (409).
- **Aviso a los repartidores** cuando un pedido a domicilio queda listo: evento SSE `delivery` (`LiveEvent.deliveryReady`) con
  número, cliente, dirección y hora. Salta tanto cuando cocina deja listo el último producto (`KitchenService`) como cuando se
  pone «Listo» a mano (`OrderService.changeStatus`). En el móvil del repartidor sale el aviso «🏍️ #642 · 21:45 · Luis ·
  Av. del Mar 4 · Listo para llevar» con pitido y vibración (el mismo componente que los avisos del camarero,
  `shared/ready-alerts.component.ts`), y la lista se refresca sola (ámbito `delivery`).
- Los pedidos (crear, editar, cambiar estado) avisan ahora en tiempo real a pedidos, cocina y reparto (antes la cocina solo veía
  un pedido para llevar nuevo con su refresco lento).

**Pantalla del repartidor** (`features/delivery/`, pensada para el móvil, una mano)

- Pestañas con el número grande: **Para llevar** (listos sin repartidor), **Míos** (los que llevo), **En cocina** (aún
  preparándose), **Entregados** (solo los entregados hoy; los que lleva otro repartidor no se listan). Al entrar, si llevo alguno, empieza en «Míos».
- Tarjetas grandes: hora de entrega, número, total, cliente, dirección, productos y notas.
- Las pestañas no tienen fondo propio en su franja (antes un rectángulo de `var(--bg)` cortaba el degradado de la página): cada
  botón es opaco (`var(--panel-strong)`) para leerse cuando la lista pasa por detrás al hacer scroll (la franja es `sticky`).
- Icono del reparto en toda la app: moto 🏍️ (menú, avisos, pedidos, nuevo pedido y web pública).
- **Estado: cerrado.** La pantalla del repartidor queda así como resultado final (28-10-2026): cabecera con iniciales y nombre,
  sin menús, cuatro pestañas (Para llevar / Míos / En cocina / Entregados) y hoja de acciones. Cambios futuros, a partir de aquí.
- Al tocar una, **hoja desde abajo** con botones grandes: «🏍️ Me lo llevo» / «✓ Entregado» / «No me lo llevo», «🗺 Mapa» (abre la
  dirección en el mapa del móvil) y «📞 Llamar» (`tel:`).

**Móvil: menú inferior (todas las personas)**

- En pantallas de 960 px o menos **no hay menú lateral**: el menú va **abajo, fijo** (al alcance del pulgar), con iconos:
  - Camarero: 🍽️ Mesas, 💶 Cobrar y su cuenta («Yo»).
  - Cocina: 🍳 Cocina y «Yo».
  - Admin y gestor de bar: Inicio, Mesas, Pedidos y «☰ Menú». Panadería: Inicio, Pedidos, Producción y «☰ Menú».
    Superadministrador: Empresas, Usuarios y «☰ Menú».
- «Yo» / «Menú» abre una hoja desde abajo con el usuario, el resto del menú (si lo hay), modo claro/oscuro y cerrar sesión.
  Se cierra al tocar fuera o al cambiar de pantalla. Los enlaces del menú son los mismos que el lateral (`ng-template #navLinks`).
- Lo que estaba fijo abajo sube para no quedar tapado: la barra «Enviar» de la comanda, el carrito del nuevo pedido y los avisos.
- **Repartidor (móvil y ordenador): sin menú lateral ni menú inferior**, porque solo usa su pantalla. Arriba hay una cabecera
  (`.driver-bar` en el shell) con sus iniciales y su nombre; al tocar las iniciales sale su menú (modo claro/oscuro y cerrar
  sesión). En la pantalla de reparto el título «Reparto» solo lo ven gestores y administradores; el repartidor ve solo «En directo».
- **Camarero en el móvil:** el servicio de mesas abre en **Lista** (el plano queda diminuto en un teléfono; se puede cambiar y se
  recuerda) y los botones de la mesa en la comanda van en una sola fila deslizable.
- **Ficheros:** `layout/shell/shell.component.{html,ts,scss}` (`tabs`, `hasMore`, `sheetOpen`),
  `features/service/service-floor-page.component.ts` (`readView`), `features/service/table-session-page.component.scss`.

**Base de datos:** columnas nuevas `orders.type` (con valor por defecto), `orders.delivery_address`, `orders.driver_id`,
`orders.dispatched_at` y `companies.delivery` (con valor por defecto). `SchemaUpgrade` amplía `users.role` (REPARTIDOR) y
`orders.status` (TRANSPORTANDO, `varchar(15)`) en las BD ya creadas: quita el CHECK antiguo (PostgreSQL) o pasa el ENUM a texto (H2).

**Datos:** el seed hace que El Mosset haga envíos, crea a Rafa Repartidor y dos pedidos a domicilio de hoy (uno listo). En la BD
local de este PC se hizo a mano el 28-10-2026 (pedidos #641 y #642).

**Tests:** `DeliveryApiTest` (tipo de pedido, me lo llevo y entregado, no me lo llevo, para recoger no se reparte, aviso cuando
cocina lo deja listo, permisos); `SeedDataTest` entra con el repartidor.

**Pendiente / ideas:** coste de envío y pedido mínimo a domicilio; zona de reparto; varios pedidos por viaje con ruta; Web Push
para avisar con el móvil bloqueado (hoy el aviso llega con la app abierta, aunque esté en segundo plano).

### Qué no se ha hecho (y por qué)

- **Notificaciones push con el móvil bloqueado (Web Push):** necesita claves VAPID, service worker, guardar suscripciones, una librería
  nueva en el backend y HTTPS. Hoy los avisos llegan con la app abierta (aunque esté en segundo plano). Ver «Pendiente».
- **Impresión directa ESC/POS** (cortar papel, abrir cajón): exige un programa local o WebUSB (solo Chrome). La precuenta ya sale
  bien por el driver de la impresora térmica.
- **Ticket fiscal / Veri\*factu:** fuera de alcance (ver Decisiones).

### Base de datos: columnas nuevas en una BD ya creada

`ddl-auto=update` crea tablas y columnas nuevas, pero **no cambia restricciones**:

- Las columnas nuevas en tablas con filas (`products.station`, `order_lines.station/status`) llevan `columnDefinition` con valor por defecto.
- Hibernate limita `users.role` a los roles que existían (H2: tipo `ENUM`; PostgreSQL: un `CHECK`), así que CAMARERO y COCINA se
  rechazaban. `config/SchemaUpgrade` lo arregla al arrancar: borra el `CHECK` antiguo o pasa la columna a `varchar`.
- Las líneas de pedidos antiguos quedan con estación COCINA aunque el producto pase luego a barra (la estación se copia al pedir, a propósito).

## Backend (`backend/src/main/java/com/pangestion`)

- `model/`: `Company`, `BusinessType`, `User`, `Role`, `Category`, `Product` (con `station`), `Ingredient`, `Customer`, `Order` (tabla `orders`), `OrderLine` (con `removedIngredients`, `station` y `status`), `OrderStatus`.
  Sala: `DiningArea`, `DiningTable`, `TableShape`, `TableSession`, `SessionStatus`, `ServiceLine`, `ItemStatus`, `Station`, `Payment`, `PaymentMethod`.
- `events/`: `LiveEvent` y `LiveEventsHub` (avisos en tiempo real por SSE).
- `repository/`: Spring Data; todas las consultas de negocio filtran por `companyId`.
- `service/`: lógica y transacciones. `controller/`: REST, siempre devuelven DTOs (nunca entidades).
- `dto/`: records `XxxDto.Request` / `XxxDto.Response` con validaciones; `SortRequest`, `ErrorResponse`.
- `security/`: `TokenService` (JWT) y `CurrentUser` (empresa/rol del token). `config/`: `SecurityConfig`, `WebConfig` (sirve `/uploads/**`), `DataInitializer`,
  `SchemaUpgrade` (ajustes que `ddl-auto` no hace), `SchedulingConfig` (latido de los avisos).
- `storage/`: `ImageStorage` (interfaz), `LocalImageStorage`, `ImageType` (valida la firma del fichero).
- `exception/`: `NotFoundException` (404), `BusinessException` (409), `GlobalExceptionHandler`.

## Frontend (`src/app`)

- `core/models`: interfaces alineadas con los DTOs. `core/services`: `CrudService` base + uno por recurso, `AuthService`, `ConfirmService` (diálogo de confirmación propio), `NotificationService` (toasts), `MyCompanyService`, `PublicApiService`, `ThemeService` (claro/oscuro).
  Sala: `DiningService` (plano), `TableServiceService` (servicio, rondas, cobros, caja), `KitchenService`, `LiveEventsService` (SSE).
- `core/interceptors/auth.interceptor.ts` (Bearer + logout en 401), `core/guards/auth.guard.ts` (`authGuard`, `companyGuard`, `superAdminGuard`, `usersGuard`,
  `diningGuard`, `serviceGuard`, `kitchenGuard`, `reportGuard`). La ruta vacía lleva al camarero a `/service` y a cocina a `/kitchen`.
- `shared/`: `ModalComponent` (diálogo), `ConfirmDialogComponent`, `CompanyFormComponent`, `OrderStatusPipe`, `DiningTableComponent` (dibujo de una mesa),
  `ReadyAlertsComponent` (avisos de «listo»), `SimpleBarChartComponent`, `sound.ts` (pitido y vibración).
- `features/`: una carpeta por pantalla (ver tabla). `layout/shell`: menú lateral fijo según rol.
- Estilos compartidos en `src/styles.scss`: paneles, tarjetas (`.card-grid`, `.card`), grupos (`.group`), botones, etiquetas de estado, formularios, asistente por pasos y franjas.
  Sala: plano (`.floor-stage`, `.floor-canvas`, `.floor-table`), zonas, `.stepper`, `.segmented`, `.chips`, `.live` y la precuenta para imprimir (`.receipt`).

## Convenciones

- Código en inglés; textos de la interfaz en español. Panel de gestión en tema oscuro **o claro** y web pública en tema claro (Space Grotesk / IBM Plex Sans).
- **Temas del panel:** los colores son variables en `:root` de `styles.scss` (`--bg`, `--panel`, `--text`, `--primary`… y sus canales
  `--tint-rgb`, `--primary-rgb`…, usados como `rgba(var(--x-rgb), 0.1)`); el tema claro las redefine en `:root[data-theme='light']`.
  `ThemeService` pone `data-theme` en `<html>` (y `index.html` lo hace antes de arrancar Angular para que no parpadee).
  **Nunca colores fijos** en las pantallas del panel: usar las variables (los degradados de los botones principales son la excepción).
- Web pública: textos cortos y sin tecnicismos ("Elige", "Revisa", "Tus datos"), un paso cada vez y nunca un botón solo con icono sin `aria-label`.
- Backend por capas: Controller → Service → Repository. Reglas de negocio en el servicio.
- Pantallas de gestión: **tarjetas en rejilla + diálogo** para crear/editar; borrar siempre con `ConfirmService` (nunca `confirm()` del navegador).
- Las credenciales reales van en `backend/.env` (no se sube). En producción `JWT_SECRET` es obligatorio.
- Gráficos: SVG propio siguiendo la guía de visualización (colores validados para daltonismo, un solo eje, tooltip y vista de tabla).

## Tests del backend (`backend/src/test/java/com/pangestion`)

`./mvnw verify` en `backend/`: **276 tests**, cada funcionalidad probada una a una. El CI solo ejecuta los 58 de la S17
(`./mvnw verify -Dgroups=reglas-negocio`). Siguen los tipos de prueba del temario (S17 unidad con Mockito, S22 repositorios
con `@DataJpaTest`, S31 HTTP con MockMvc). Correspondencia sesión ↔ código en `backend/docs/sesiones.md` y lo comprobado
(regresiones provocadas, colección Postman) en `backend/docs/comprobaciones.md`.

| Capa | Clases | Qué prueban |
|---|---|---|
| Unidad (sin Spring) | `storage/ImageTypeTest`, `storage/LocalImageStorageTest`, `security/TokenServiceTest` | Firma de imágenes, guardado/borrado en disco y `../`, claims y caducidad del JWT |
| **S17** · unidad con Mockito (`@Tag("reglas-negocio")`, los del CI) | `service/OrderServiceRulesTest`, `service/CompanyServiceSlotsTest`, `service/UserServiceRulesTest`, `model/OrderModelTest`, `model/DiningModelTest`, `model/TableSessionModelTest`, `model/KitchenModelTest` | Líneas iguales que se suman y «sin cebolla» aparte, productos inactivos, pedidos cerrados, franjas, reglas de usuarios y total del pedido. Sala: mesa que cabe y no pisa, rondas, total, nombre de mesas juntas, reparto entre comensales, pendiente, pasos de estado, estación, hora de listo, separar unidades y estado del pedido para llevar |
| Repositorio (`@DataJpaTest`) | `repository/CatalogRepositoriesTest`, `repository/OrderRepositoriesTest` | Consultas propias (búsquedas, orden, aforo, facturación, producción, estadísticas) y restricciones únicas de la BD |
| HTTP (MockMvc + seguridad real) | `web/*ApiTest` (Auth, Security, Company, MyCompany, User, Category, Ingredient, Product, ProductImage, Customer, Order, Production, Public, Dashboard, ErrorHandling; sala: Dining, TableService, Kitchen, Events, Payment, Report, DiningDashboard) | Cada endpoint: códigos 200/201/204/400/401/403/404/409, roles, aislamiento entre empresas, CORS y formato de errores. `EventsApiTest` abre la conexión SSE y comprueba que solo llegan los avisos de su empresa |
| Arranque | `config/SeedDataTest`, `PangestionApiApplicationTests` | Datos de ejemplo, usuarios de prueba que entran y pedidos de demo (sin domingos, sin pasar el aforo, sin duplicar) |

- Base común: `support/ApiTestSupport` (contexto completo, perfil `test`, cada test en una transacción que se deshace, ayudas
  `company()`, `user()`, `product()`, `order()`… y `as(usuario)` para el token) y `support/RepositoryTestSupport`.
- Perfil `src/test/resources/application-test.properties`: H2 en memoria, sin datos de ejemplo, clave JWT de tests e imágenes en `target/`.
- Colección Postman repetible en `backend/pruebas/` (23 peticiones, 41 aserciones; `npx newman run ...`, ver `backend/README.md`).
- Si es una regla de negocio, su test con Mockito en `service/` con `@Tag("reglas-negocio")` (entra en el CI).
- Al añadir una funcionalidad: su test HTTP en `web/` (caso correcto, validación 400, regla 409, otra empresa 404 y rol sin permiso 403).
- Los tests destaparon un fallo: `DataInitializer` no tenía `@Order(1)` y, con la BD vacía, `DemoOrdersGenerator` arrancaba antes y fallaba.

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
| Plano de mesas / reglas de colocación | `features/dining/` y `service/DiningService` + `model/DiningArea` (`fits`, `overlapping`) |
| Abrir mesa, rondas, comensales | `service/TableSessionService`, `features/service/` |
| Pantalla de cocina, estados | `service/KitchenService`, `model/ItemStatus`, `features/kitchen/` |
| Cobro, reparto, caja | `TableSessionService.pay/day`, `TableSession.guestShares`, `features/bar/` (precuenta en su `.html` y `@media print` en `styles.scss`) |
| Avisos en tiempo real | `events/LiveEventsHub` (y `publishEvent` en los servicios), `core/services/live-events.service.ts`, `shared/ready-alerts.component.ts` |
| Informes de sala | `service/DiningReportService`, `features/dining-report/` |
| Colores / tema claro | variables de `src/styles.scss` (`:root` y `:root[data-theme='light']`) |

- **Presupuesto de estilos:** `angular.json` limita el SCSS de cada componente a 14 kB (aviso) / 16 kB (error). La web pública es la más grande (~13 kB ya compilado; el `.scss` fuente ocupa ~19 kB).
- **Probar la web pública:** abrirla a tamaño móvil (375 × 812), hacer un pedido completo y después borrar el pedido y el cliente de prueba (con `admin@admin.es` o `admin2@admin.es`: `DELETE /api/orders/{id}` y `DELETE /api/customers/{id}`) para no ensuciar los datos de demo.
- El servidor de Angular a veces se queda colgado tras un error de compilación, y **no ve las carpetas nuevas** (`features/xxx/`) creadas mientras corre: pararlo y volver a arrancarlo.
- **Probar la sala:** entrar con `admin2@admin.es` (bar). Para probar a la vez camarero y cocina en el mismo navegador no sirve otra pestaña
  (comparten `localStorage`): usar otro navegador o una ventana privada.
- Si el backend no arranca con «Database may be already in use», queda otro backend vivo usando la BD H2: pararlo antes.

## Historial de cambios

| Fecha | Commit | Cambio |
|---|---|---|
| 2026-10-14 | `629ce3e` | Backend Spring Boot (Hibernate crea la BD) y panel Angular con las primeras pantallas |
| 2026-10-18 | `03b0988` | Multiempresa con SUPERADMIN, tipo de negocio, carta de El Mosset, catálogo de Oberta con fotos, nuevo flujo de pedido, quitar ingredientes en el panel, gráfica semanal, producción del día, descargar pedidos y pasar a "en proceso" |
| 2026-10-18 | `1b6487e` | Generador de pedidos de demo y copia de la BD en `backups/` |
| 2026-10-21 | `b8c16d2` | Web pública rediseñada: asistente de 5 pasos, móvil primero, tema claro accesible, quitar ingredientes desde la web, progreso en `sessionStorage` |
| 2026-10-22 | `e546367` | Web pública: tarjetas compactas + panel del producto, variantes "sin …" como líneas aparte, móvil minimalista (cabecera de una línea, iconos SVG, días en dos filas con scroll a las horas) y panel final «Revisa y confirma» |
| 2026-10-23 | `4f659fd` | CI del backend (`Compilar y probar`), `main` protegida por PR y la copia de la BD deja de subirse (repo público) |
| 2026-10-26 | `07255f2` | Se trabaja en la rama `dev`; `context.md` con guía de la web pública, guía rápida e historial |
| 2026-10-26 | `f7a999a` | Tests del backend por capas (202) y arreglo del orden de arranque (`DataInitializer` con `@Order(1)`) |
| 2026-10-26 | `d13dc3d` | Backend adaptado a las sesiones 5–17 (JSON estricto, 201 + Location, creación anidada, validación propia, errores, colección Postman) y CI solo con los tests de la S17 |
| 2026-10-27 | *sin commit* | **Modo claro** del panel: variables de color, `ThemeService`, botón en el menú |
| 2026-10-27 | *sin commit* | **Clientes:** estadísticas, filtros, tarjetas con pedidos y gasto (el backend devuelve `orderCount`, `totalSpent`, `lastOrderDate`), Particular/Negocio en la ficha |
| 2026-10-27 | *sin commit* | **Sala, fase 1:** zonas y mesas con editor visual |
| 2026-10-27 | *sin commit* | **Fase 2:** rol CAMARERO, servicio de mesas (abrir, juntar/cambiar, comensales, liberar) y comanda móvil por rondas |
| 2026-10-27 | *sin commit* | **Fase 3:** estación por producto, estado por producto, rol COCINA y pantalla táctil de cocina/barra |
| 2026-10-27 | *sin commit* | **Fase 4:** avisos en tiempo real (SSE): rondas a cocina, «listo» al camarero, pantallas que se refrescan solas |
| 2026-10-27 | *sin commit* | **Fase 5:** barra y cobros (todo, por comensal, partes iguales; efectivo/tarjeta), precuenta 80 mm, caja del día |
| 2026-10-27 | *sin commit* | **Fase 6:** informes de sala y hora de «listo» por producto |
| 2026-10-28 | *sin commit* | **Menú e inicio según el tipo de negocio:** inicio de sala (`GET /api/dashboard/dining`), menú Sala / Para llevar en bares y restaurantes, sin «Producción del día»; textos sin «panadería» fija |
| 2026-10-28 | *sin commit* | **Panel y cocina:** menú lateral plegable (flecha, se recuerda), menú del usuario en sus iniciales (modo claro/oscuro y cerrar sesión); cocina a todo el alto con tickets compactos, columnas de ancho variable, una unidad por toque, color e inicial de categoría; el rol COCINA solo ve su pantalla |
| 2026-10-28 | *sin commit* | **Sin comensales por producto:** los comensales solo dividen la cuenta («Entre comensales», a partes iguales); fuera de la comanda, la cocina y la API; no se cambia el nº de comensales si alguno ya pagó |
| 2026-10-28 | *sin commit* | **Bebidas en barra, orden en cocina y extras:** estación por categoría (Bebidas → barra), página «Orden en cocina» (`PUT /api/categories/kitchen-order`), precio de extra en ingredientes y extras con precio en comanda, pedidos y web pública; en cocina, «+ Huevo» en verde y «sin Cebolla» en rojo |
| 2026-10-28 | *sin commit* | **Barra sin «Preparando»** (de pendiente a listo para servir) y «pax» → «pers» |
| 2026-10-28 | *sin commit* | **Pedidos a domicilio y repartidor:** tipo de pedido (recoger / a domicilio con dirección), envíos por negocio, estado Transportando, rol REPARTIDOR con su pantalla y `/api/delivery`, aviso al repartidor cuando un pedido queda listo; **móvil:** menú inferior fijo, hoja «Menú/Yo», lista de mesas por defecto |
| 2026-10-28 | *sin commit* | **Repartidor, versión final:** cabecera con iniciales y nombre sin menú lateral ni inferior, pestaña «Entregados» (solo lo entregado hoy), icono de moto 🏍️, pestañas sin el fondo que cortaba el degradado |
| 2026-10-27 | *sin commit* | Arreglos: `--panel-strong` del tema oscuro se refería a sí mismo (modales y tarjetas sin fondo) y `SchemaUpgrade` para los roles nuevos en BD ya creadas |

## Pendiente / ideas

- Horario por día de la semana y con tramos partidos (El Mosset: mañanas y noches, distinto lunes y domingo).
- Extras con suplemento ("ingrediente extra +1 €") y precios por día (pizzas a 6 € los miércoles).
- Reparto a domicilio con zonas y pedido mínimo.
- Nombres repetidos entre categorías ("Barbacoa" pizza/bocadillo/hamburguesa): mostrar la categoría en el detalle del pedido.
- Redimensionar imágenes al subirlas; almacenamiento S3/R2 al desplegar.
- Paginación en listados grandes, tests de integración de la API y despliegue (VPS con Docker + PostgreSQL).
- Quitar los usuarios de prueba del login antes de publicar.

**Sala (bares y restaurantes), siguiente paso recomendado:**

- **Separar por módulos dentro de la misma app** (decidido no hacer dos apps): pasar lo de sala a `com.pangestion.dining`,
  una sola comprobación «solo bares y restaurantes» en lugar de una por servicio, rutas de sala agrupadas. Opcional:
  dos portadas o subdominios (panaderías / restaurantes) apuntando a la misma app.
- **Web Push** (avisos con el móvil bloqueado): VAPID, service worker, tabla de suscripciones, librería `web-push` y HTTPS.
- **Varios servidores:** `LiveEventsHub` guarda las conexiones en memoria; con más de una instancia haría falta un intermediario
  (Redis pub/sub, PostgreSQL `LISTEN/NOTIFY`…).
- Impresión ESC/POS directa (cajón, corte) con un agente local; ticket fiscal (Veri\*factu).
- Mover mesas de una zona a otra en el editor (hoy: borrar y crear). Reservas de mesa.
- En la BD local hay un servicio de prueba («4 + 5») cerrado sin cobrar antes de que existiera esa regla: sale en informes con 34,50 € facturados y 0 cobrados.
