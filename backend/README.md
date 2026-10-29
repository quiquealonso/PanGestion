# PanGestión API (Spring Boot)

API REST del proyecto PanGestión. Java 21 · Spring Boot 4.1 · Spring Data JPA / Hibernate · Spring Security (JWT) · PostgreSQL (H2 en desarrollo).

## Arrancar

```bash
./mvnw spring-boot:run            # Windows: mvnw.cmd spring-boot:run
```

- Por defecto usa **H2 en fichero** (`./data/pangestion`), sin instalar nada. Consola: http://localhost:8080/h2-console
- Con **PostgreSQL**: copia `.env.example` a `.env` y rellénalo. Después ejecuta `docker compose up -d` y `./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres`.
  El fichero `.env` (credenciales de la BD y `JWT_SECRET`) **no se sube a Git**. En este perfil no se crean usuarios de prueba salvo con `SEED_DATA=true`.
- Hibernate crea/actualiza las tablas a partir de las entidades (`ddl-auto=update`).
- Con H2, la primera vez se cargan datos de ejemplo (`config/DataInitializer`): un SUPERADMIN y dos empresas con sus usuarios: "Panadería Demo" (catálogo de Oberta) y el bar "El Mosset" (carta completa en `src/main/resources/seed/el-mosset.json`). Las credenciales están en ese fichero. Para empezar de cero, borra la carpeta `data/`.

### Generar pedidos de demostración

`config/DemoOrdersGenerator` crea pedidos de prueba (lunes a sábado, dentro del horario y del aforo de cada franja)
para una empresa y un rango de fechas. Solo se ejecuta si se pasan los parámetros:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--app.demo-orders.company-id=1 --app.demo-orders.from=2026-10-12 --app.demo-orders.to=2026-10-24"
```

Los días pasados quedan como entregados (con algún cancelado) y los de hoy en adelante como pendientes. Siempre
genera los mismos datos (semilla fija) y se salta los días que ya tienen pedidos.

## Estructura

```
com.pangestion
├── model/        Entidades JPA (mapean las tablas) y enums
├── repository/   Interfaces Spring Data (consultas)
├── dto/          Records de entrada (Request, con validaciones) y salida (Response)
├── service/      Lógica de negocio y transacciones
├── controller/   Endpoints REST (solo reciben y devuelven DTOs)
├── security/     Generación de tokens JWT
├── events/       Avisos en tiempo real (Server-Sent Events)
├── config/       Seguridad, CORS, datos de ejemplo, ajustes de esquema y tareas periódicas
└── exception/    Excepciones de negocio y manejador global de errores
```

Flujo de una petición: `Controller → Service → Repository → BD`, y la respuesta vuelve convertida a DTO (nunca se exponen las entidades).

## Modelo de datos

La aplicación es **multiempresa**: cada **empresa** es un negocio independiente (panadería, bar o restaurante). Todo lo demás
(categorías, ingredientes, productos, clientes, pedidos y usuarios) pertenece a una empresa, y cada
usuario solo ve los datos de la suya.

```
companies (panadería: datos fiscales, dirección, horario y franjas de recogida)
   │
   ├──< users (SUPERADMIN sin empresa | ADMIN | GESTOR | VISOR)
   ├──< categories ──< products >──< product_ingredients >── ingredients
   ├──< customers
   ├──< orders ──< order_lines >── products
   │        └── customer
   │
   │   Sala (solo BAR y RESTAURANTE)
   ├──< dining_areas ──< dining_tables
   └──< table_sessions >──< table_session_tables >── dining_tables   (mesas que ocupa mientras está abierta)
            ├──< service_lines >── products
            └──< payments
```

| Tabla | Campos principales |
|---|---|
| `companies` | name, business_type (PANADERIA, BAR o RESTAURANTE; solo lo cambia el SUPERADMIN), tax_id (CIF, único), email, phone, address, opening_time, closing_time, slot_minutes, orders_per_slot, active |
| `categories` | company_id, name (único por empresa), color, description, sort_order |
| `ingredients` | company_id, name (único por empresa), allergen. Indica de qué está hecho un producto (no controla stock) |
| `products` | company_id, category_id, name, abbreviation, description, price, active, color (nulo = el de la categoría), sort_order, image_path |
| `product_ingredients` | product_id, ingredient_id: tabla intermedia muchos a muchos |
| `customers` | company_id, name, email (único por empresa), phone, business_name y tax_id (si es un negocio), created_at |
| `orders` | company_id, customer_id, pickup_date, pickup_time, status, notes, total, created_at |
| `order_lines` | order_id, product_id, quantity, unit_price (precio en el momento de la compra) |
| `order_line_removed_ingredients` | order_line_id, ingredient_name: ingredientes quitados en esa línea ("pizza sin cebolla"), guardados por nombre |
| `users` | company_id (nulo para SUPERADMIN), name, email (único global), password (BCrypt), role, active, last_login |

Columnas de sala en tablas existentes: `products.station` (COCINA, BARRA o NINGUNA; por defecto COCINA) y en
`order_lines`: `station` (copiada del producto al pedir), `status` (estado del producto) y `status_changed_at`.

| Tabla de sala | Campos principales |
|---|---|
| `dining_areas` | company_id, name (único por empresa), width y height (casillas de la cuadrícula), sort_order |
| `dining_tables` | company_id, area_id, name (único por empresa), seats, shape (ROUND/RECT), pos_x, pos_y, width, height (casillas) |
| `table_sessions` | company_id, waiter_id, table_label («4 + 5», se conserva al cerrar), guests, notes, status (OPEN/CLOSED), rounds, opened_at, closed_at |
| `table_session_tables` | session_id, table_id: mesas que ocupa el servicio (se vacía al cerrarlo) |
| `service_lines` | session_id, product_id, quantity, unit_price, guest (nulo = para la mesa), notes, round_number, station, status, status_changed_at, ready_at, created_at |
| `service_line_removed_ingredients` | service_line_id, ingredient_name |
| `payments` | session_id, amount, method (EFECTIVO/TARJETA), guest, label, cash_given, cashier_id, paid_at |

Estados de pedido: `PENDIENTE → EN_PROCESO → LISTO → TRANSPORTANDO → ENTREGADO` (o `CANCELADO`). `TRANSPORTANDO` solo en pedidos a domicilio.
Tipo de pedido: `RECOGER` (por defecto) o `DOMICILIO` (con `deliveryAddress`; el negocio debe tener `delivery`).
Estados de cada producto en cocina: `PENDIENTE → PREPARANDO → LISTO → SERVIDO` (lo que no se prepara nace LISTO; en la barra se pasa de PENDIENTE a LISTO sin PREPARANDO).
En un pedido para llevar de un bar, el estado del pedido sigue al de sus productos (todos listos = LISTO; alguno empezado = EN_PROCESO).

## Reglas de negocio

- **Aislamiento por empresa:** el token JWT lleva el `companyId` del usuario y todos los servicios filtran por él (`security/CurrentUser`). Pedir un id de otra empresa devuelve 404.
- Las franjas de recogida salen del horario de la empresa (`slot_minutes`) y tienen aforo (`orders_per_slot`); las pasadas o completas no se pueden elegir. Los cancelados no ocupan franja. Al editar un pedido, su propio hueco no cuenta.
- El total del pedido se calcula en el servidor con el precio actual de cada producto. Un pedido solo admite productos de su misma empresa.
- En la web pública el cliente elige la panadería; se le identifica por email **dentro de esa panadería** (el mismo email en dos panaderías son dos clientes distintos).
- Solo se pueden pedir productos activos. Un producto con pedidos no se borra: se desactiva.
- No se puede borrar una categoría con productos, un ingrediente que lleva algún producto, un cliente con pedidos ni una empresa con usuarios o pedidos.
- Los pedidos entregados o cancelados no se editan.
- **Personalizar productos:** cada línea puede quitar ingredientes del producto (`removedIngredientIds`); el precio no cambia. Solo se pueden quitar ingredientes que lleva el producto. Las líneas del mismo producto con la misma personalización se suman; con distinta, quedan separadas (2 pizzas + 1 pizza sin cebolla).
- Categorías y productos se listan por su orden (`sort_order`). `PUT .../order` recibe `{"ids": [...]}` en el orden deseado; los productos solo se ordenan dentro de una misma categoría.
- Cada empresa conserva al menos un ADMIN activo y la plataforma al menos un SUPERADMIN; nadie puede borrarse ni quitarse el rol a sí mismo.

**Sala (solo bares y restaurantes; una panadería recibe 409):**

- **Plano:** cada mesa cabe dentro de su zona y no pisa a otra; nombres de zona y de mesa únicos en la empresa; no se encoge
  una zona dejando mesas fuera; no se borra una mesa ni una zona con mesas ocupadas.
- **Servicio:** una mesa solo puede estar en un servicio abierto (juntar = el servicio ocupa varias); los productos son de toda
  la mesa (no llevan comensal); no se cambia el nº de comensales si alguno ya ha pagado su parte; un servicio cerrado no se toca.
- **Cocina:** cada producto avanza o retrocede un paso; un pedido para llevar no se «sirve»; el camarero solo anula lo que cocina
  no ha empezado (o lo que no se prepara y no se ha servido). Se pueden pasar unidades sueltas: la línea se separa y, si en el
  estado de destino ya está lo mismo (misma ronda, notas e ingredientes), se juntan. Más unidades de las que quedan: 409.
- **Cobro:** no más de lo pendiente; un comensal paga exactamente su parte (el total a partes iguales; los céntimos
  sobrantes, el comensal 1) y una sola vez; en efectivo lo entregado cubre el importe; no se libera una mesa con algo pendiente;
  con algo cobrado no se anula un producto si el total quedaría por debajo.
- **Extras:** una línea puede llevar `addedIngredientIds` (ingredientes con `extraPrice`); su precio se suma al unitario.
  Ingrediente sin precio de extra o producto sin ingredientes: 409. La categoría con `station` impone esa estación a sus productos.
- Los roles CAMARERO y COCINA solo existen en bares y restaurantes.

## Imágenes

- La base de datos solo guarda la **ruta** (`products.image_path`); el fichero va a un almacenamiento detrás de la interfaz `storage/ImageStorage`.
- Implementación actual: `LocalImageStorage`, que guarda en disco en `backend/uploads/{empresa}/products/{uuid}.{ext}` (carpeta configurable con `UPLOADS_DIR`, fuera de Git). Para usar S3/R2 bastaría con añadir otra implementación.
- Al subir se valida el **contenido** (firma JPEG, PNG o WEBP, no la extensión) y el tamaño (máx. 2 MB), y se genera un nombre aleatorio. Al cambiar o quitar la foto, o al borrar el producto, se borra el fichero.
- Se sirven en `/uploads/**` sin login y con caché de 30 días.
- Las fotos de ejemplo (copiadas de Oberta) están en `src/main/resources/seed-images/` y `DataInitializer` las copia al almacenamiento al crear la base de datos.

## Seguridad

`POST /api/auth/login` devuelve un JWT que se envía como `Authorization: Bearer <token>`.

| Rol | Permisos |
|---|---|
| SUPERADMIN | Alta y gestión de empresas (`/api/companies`) y de usuarios de cualquier empresa. No trabaja con catálogo ni pedidos. |
| ADMIN | Todo en su empresa, incluidos sus usuarios y los datos/horario de la empresa (`PUT /api/company`) |
| GESTOR | Lectura y escritura de su empresa salvo usuarios y datos de la empresa |
| VISOR | Solo lectura de su empresa; en bares y restaurantes, también los informes de sala |
| CAMARERO | Bares y restaurantes: `/api/service/**` (abrir mesas, comandas, cobros) y servir en `/api/kitchen`. El resto, solo lectura; sin informes |
| COCINA | Bares y restaurantes: solo `/api/kitchen/**`, `/api/events` y `/api/auth/me` (el resto, 403 incluso para leer) |
| REPARTIDOR | Cualquier negocio: solo `/api/delivery/**`, `/api/events` y `/api/auth/me` (el resto, 403 incluso para leer) |

`/api/public/**` no requiere login (web pública de pedidos). `/api/reports/**`: ADMIN, GESTOR y VISOR.

## Endpoints

| Recurso | Endpoints |
|---|---|
| Auth | `POST /api/auth/login`, `GET /api/auth/me` |
| Mi empresa | `GET /api/company`, `PUT /api/company` (ADMIN), `POST`/`DELETE /api/company/logo` (ADMIN, multipart `file`: JPG, PNG o WEBP ≤ 2 MB), `GET /api/company/slots?date=yyyy-MM-dd` |
| Empresas | CRUD `/api/companies` (SUPERADMIN) |
| Inicio | `GET /api/dashboard`, `GET /api/dashboard/weekly-sales?date=yyyy-MM-dd` (unidades por día de esa semana y de la anterior), `GET /api/dashboard/dining` (bares y restaurantes: mesas ocupadas, comensales, listos, colas de cocina y barra, para llevar de hoy y caja de hoy; una panadería recibe 409) |
| Pedidos | `GET /api/orders?search&status&from&to`, `GET/PUT/DELETE /api/orders/{id}`, `POST /api/orders`, `PATCH /api/orders/{id}/status`, `POST /api/orders/start-day?date=` (pasa los pendientes de ese día a EN_PROCESO), `GET /api/orders/production?date=&pending=` (unidades por producto, personalización y hora de recogida; sin cancelados, y con `pending=true` sin entregados) |
| Clientes | `GET /api/customers?search`, `GET /api/customers/{id}/orders`, `POST /api/customers/{id}/orders` (creación anidada: la ruta decide el cliente; cuerpo sin `customerId`), CRUD `/api/customers` |
| Productos | `GET /api/products?search&categoryId&active`, `PATCH /api/products/{id}/active`, `PUT /api/products/order`, `POST/DELETE /api/products/{id}/image` (multipart `file`), CRUD `/api/products` |
| Ingredientes | CRUD `/api/ingredients` (cada uno indica en cuántos productos se usa y su `extraPrice`: precio si se añade como extra; nulo = no es extra) |
| Categorías | `PUT /api/categories/order` (orden de la carta), `PUT /api/categories/kitchen-order` (orden en la pantalla de cocina), CRUD `/api/categories` (con `station`: dónde se preparan todos sus productos; nulo = cada producto) |
| Usuarios | CRUD `/api/users` (SUPERADMIN: todos; ADMIN: los de su empresa) |
| Reparto | `GET /api/delivery` (pedidos a domicilio de hoy), `POST /api/delivery/{id}/take` (me lo llevo: LISTO → TRANSPORTANDO), `/deliver` (entregado), `/release` (no me lo llevo). ADMIN, GESTOR y REPARTIDOR |
| Público | `GET /api/public/companies`, `/companies/{id}/slots?date=`, `/companies/{id}/categories`, `/companies/{id}/products`, `/companies/{id}/extras`, `POST /api/public/orders` |
| Plano de sala | `GET/POST /api/dining/areas`, `GET/PUT/DELETE /api/dining/areas/{id}`, `PUT /api/dining/areas/order`, `POST /api/dining/areas/{id}/tables` (anidada: la ruta decide la zona; `Location` → `/api/dining/tables/{id}`), `PUT/DELETE /api/dining/tables/{id}` |
| Servicio de mesas | `GET /api/service/floor` (plano con el servicio de cada mesa), `POST /api/service/sessions` (abrir), `GET/PUT /api/service/sessions/{id}` (comensales y notas), `PUT …/{id}/tables` (juntar/cambiar), `POST …/{id}/rounds` (enviar ronda), `DELETE …/{id}/lines/{lineId}`, `GET …/{id}/bill`, `POST …/{id}/payments`, `DELETE …/{id}/payments/{paymentId}`, `POST …/{id}/close` (liberar), `GET /api/service/day?date=` (caja) |
| Cocina y barra | `GET /api/kitchen?station=COCINA\|BARRA` (tickets), `PATCH /api/kitchen/items` (`{"items":[{"source":"MESA","id":7,"units":1}],"status":"LISTO"}`; `units` opcional, sin él pasan todas las unidades). Cada producto trae `categoryName` y `categoryColor` |
| Avisos | `GET /api/events` (`text/event-stream`): eventos `hello`, `change` (`{"scopes":[…]}`) y `ready` (`{sessionId, tableLabel, waiterName, items}`) |
| Informes | `GET /api/reports/dining?from=&to=` (por defecto, últimos 7 días; máximo un año) |

**Contrato común:**

- `POST` de alta responde **201** con el recurso y la cabecera **`Location`** con la URL de su detalle (`/api/categories/7`). Las altas anidadas y la web pública apuntan al detalle canónico `/api/orders/{id}`. `DELETE` responde 204.
- **JSON estricto:** un campo que no existe en el DTO (una errata, o `id`, `total`, `customerId` donde no toca) se rechaza con 400 `Campo no permitido: <campo>`, nunca se ignora en silencio.
- **Errores** siempre con el mismo formato `{ status, message, fieldErrors, timestamp }` y sin trazas internas:
  400 validación (`fieldErrors` por campo), JSON mal formado, tipo incorrecto o parámetro obligatorio que falta · 401 sin login o token no válido · 403 rol sin permiso · 404 no existe o ruta desconocida · 405 método no permitido · 409 regla de negocio · 413 fichero demasiado grande · 415 tipo de contenido no admitido.
- Validación propia `@ValidOpeningHours` (paquete `validation`): la apertura antes que el cierre (400 en `closingTime`).
- Acciones que no crean un recurso con detalle propio (enviar ronda, cobrar, liberar) responden 200 con el servicio actualizado.

## Avisos en tiempo real

- `GET /api/events` deja la conexión abierta (Server-Sent Events) y envía los eventos de la empresa del usuario. El token va en la
  cabecera `Authorization`, como en el resto de la API (el frontend lo lee con `fetch`, no con `EventSource`, que no admite cabeceras).
- Los servicios publican `LiveEvent` con `ApplicationEventPublisher`; `LiveEventsHub` los envía con `@TransactionalEventListener`,
  es decir, **solo si la transacción se confirma**. Latido cada 25 s; las conexiones se cierran a los 30 min y el navegador reconecta.
- Las conexiones se guardan en memoria: con más de una instancia del backend haría falta un intermediario (Redis, `LISTEN/NOTIFY`…).

## Cambios de esquema en BD ya creadas

`ddl-auto=update` añade tablas y columnas pero no cambia restricciones. Por eso:

- Las columnas nuevas en tablas con datos llevan valor por defecto (`columnDefinition = "varchar(10) default 'COCINA' not null"`).
- `config/SchemaUpgrade` (al arrancar) quita el límite antiguo de `users.role` para admitir CAMARERO y COCINA: en PostgreSQL borra el
  `CHECK`, en H2 pasa la columna de `ENUM` a `varchar`.

## Tests y colección HTTP

```bash
./mvnw verify                            # todos los tests (unidad, repositorio, HTTP, arranque)
./mvnw verify -Dgroups=reglas-negocio    # solo los de la sesión 17: lo que ejecuta el CI en cada PR
```

- Los tests de la sesión 17 (reglas de negocio con JUnit + Mockito, sin base de datos) llevan `@Tag("reglas-negocio")`:
  `OrderServiceRulesTest`, `CompanyServiceSlotsTest`, `UserServiceRulesTest`, `OrderModelTest` y, de la sala, `DiningModelTest`,
  `TableSessionModelTest` y `KitchenModelTest` (58 en total). El CI (`.github/workflows/ci.yml`) compila todo pero solo ejecuta estos.
- Batería completa en local: **279 tests**. Los de la sala: `DiningApiTest`, `TableServiceApiTest`, `KitchenApiTest`, `EventsApiTest`,
  `PaymentApiTest`, `ReportApiTest` y `DiningDashboardApiTest`.
- Colección Postman repetible en `pruebas/` (prepara sus datos, comprueba y limpia). Con el backend arrancado:
  `npx newman run pruebas/PanGestion.postman_collection.json -e pruebas/Local.postman_environment.json`
- Qué se comprobó en cada sesión: [`docs/comprobaciones.md`](docs/comprobaciones.md). Qué parte del código cubre cada sesión: [`docs/sesiones.md`](docs/sesiones.md).
