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
├── config/       Seguridad, CORS y datos de ejemplo
└── exception/    Excepciones de negocio y manejador global de errores
```

Flujo de una petición: `Controller → Service → Repository → BD`, y la respuesta vuelve convertida a DTO (nunca se exponen las entidades).

## Modelo de datos

La aplicación es **multiempresa**: cada **empresa** es una panadería independiente. Todo lo demás
(categorías, ingredientes, productos, clientes, pedidos y usuarios) pertenece a una empresa, y cada
usuario solo ve los datos de la suya.

```
companies (panadería: datos fiscales, dirección, horario y franjas de recogida)
   │
   ├──< users (SUPERADMIN sin empresa | ADMIN | GESTOR | VISOR)
   ├──< categories ──< products >──< product_ingredients >── ingredients
   ├──< customers
   └──< orders ──< order_lines >── products
            └── customer
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

Estados de pedido: `PENDIENTE → EN_PROCESO → LISTO → ENTREGADO` (o `CANCELADO`).

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
| VISOR | Solo lectura de su empresa |

`/api/public/**` no requiere login (web pública de pedidos).

## Endpoints

| Recurso | Endpoints |
|---|---|
| Auth | `POST /api/auth/login`, `GET /api/auth/me` |
| Mi empresa | `GET /api/company`, `PUT /api/company` (ADMIN), `GET /api/company/slots?date=yyyy-MM-dd` |
| Empresas | CRUD `/api/companies` (SUPERADMIN) |
| Inicio | `GET /api/dashboard`, `GET /api/dashboard/weekly-sales?date=yyyy-MM-dd` (unidades por día de esa semana y de la anterior) |
| Pedidos | `GET /api/orders?search&status&from&to`, `GET/PUT/DELETE /api/orders/{id}`, `POST /api/orders`, `PATCH /api/orders/{id}/status`, `POST /api/orders/start-day?date=` (pasa los pendientes de ese día a EN_PROCESO), `GET /api/orders/production?date=&pending=` (unidades por producto, personalización y hora de recogida; sin cancelados, y con `pending=true` sin entregados) |
| Clientes | `GET /api/customers?search`, `GET /api/customers/{id}/orders`, `POST /api/customers/{id}/orders` (creación anidada: la ruta decide el cliente; cuerpo sin `customerId`), CRUD `/api/customers` |
| Productos | `GET /api/products?search&categoryId&active`, `PATCH /api/products/{id}/active`, `PUT /api/products/order`, `POST/DELETE /api/products/{id}/image` (multipart `file`), CRUD `/api/products` |
| Ingredientes | CRUD `/api/ingredients` (cada uno indica en cuántos productos se usa) |
| Categorías | `PUT /api/categories/order`, CRUD `/api/categories` |
| Usuarios | CRUD `/api/users` (SUPERADMIN: todos; ADMIN: los de su empresa) |
| Público | `GET /api/public/companies`, `/companies/{id}/slots?date=`, `/companies/{id}/categories`, `/companies/{id}/products`, `POST /api/public/orders` |

**Contrato común:**

- `POST` de alta responde **201** con el recurso y la cabecera **`Location`** con la URL de su detalle (`/api/categories/7`). Las altas anidadas y la web pública apuntan al detalle canónico `/api/orders/{id}`. `DELETE` responde 204.
- **JSON estricto:** un campo que no existe en el DTO (una errata, o `id`, `total`, `customerId` donde no toca) se rechaza con 400 `Campo no permitido: <campo>`, nunca se ignora en silencio.
- **Errores** siempre con el mismo formato `{ status, message, fieldErrors, timestamp }` y sin trazas internas:
  400 validación (`fieldErrors` por campo), JSON mal formado, tipo incorrecto o parámetro obligatorio que falta · 401 sin login o token no válido · 403 rol sin permiso · 404 no existe o ruta desconocida · 405 método no permitido · 409 regla de negocio · 413 fichero demasiado grande · 415 tipo de contenido no admitido.
- Validación propia `@ValidOpeningHours` (paquete `validation`): la apertura antes que el cierre (400 en `closingTime`).

## Tests y colección HTTP

```bash
./mvnw verify                            # todos los tests (unidad, repositorio, HTTP, arranque)
./mvnw verify -Dgroups=reglas-negocio    # solo los de la sesión 17: lo que ejecuta el CI en cada PR
```

- Los tests de la sesión 17 (reglas de negocio con JUnit + Mockito, sin base de datos) llevan `@Tag("reglas-negocio")`:
  `OrderServiceRulesTest`, `CompanyServiceSlotsTest`, `UserServiceRulesTest` y `OrderModelTest`. El CI (`.github/workflows/ci.yml`) compila todo pero solo ejecuta estos.
- Colección Postman repetible en `pruebas/` (prepara sus datos, comprueba y limpia). Con el backend arrancado:
  `npx newman run pruebas/PanGestion.postman_collection.json -e pruebas/Local.postman_environment.json`
- Qué se comprobó en cada sesión: [`docs/comprobaciones.md`](docs/comprobaciones.md). Qué parte del código cubre cada sesión: [`docs/sesiones.md`](docs/sesiones.md).
