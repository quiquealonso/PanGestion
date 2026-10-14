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
- Con H2, la primera vez se cargan datos de ejemplo (`config/DataInitializer`), con tres usuarios de prueba (ADMIN, GESTOR, VISOR) cuyas credenciales están en ese fichero. Para empezar de cero, borra la carpeta `data/`.

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

```
categories ──< products >──< product_ingredients >── ingredients
                  │
                  └──< order_lines >── orders >── customers >── companies (opcional = particular)
                                         │
                                         └── stores
users (ADMIN | GESTOR | VISOR)
```

| Tabla | Campos principales |
|---|---|
| `categories` | name (único), color, description |
| `ingredients` | name (único), unit, stock, min_stock, unit_cost, allergen |
| `products` | name, description, price, active, category_id |
| `product_ingredients` | product_id, ingredient_id (únicos juntos), quantity — la receta |
| `companies` | name, tax_id (CIF, único), email, phone, address |
| `customers` | name, email (único), phone, company_id (nulo = particular), created_at |
| `stores` | name, address, phone, opening_time, closing_time, slot_minutes, orders_per_slot, active |
| `orders` | customer_id, store_id, pickup_date, pickup_time, status, notes, total, created_at |
| `order_lines` | order_id, product_id, quantity, unit_price (precio en el momento de la compra) |
| `users` | name, email (único), password (BCrypt), role, active, last_login |

Estados de pedido: `PENDIENTE → EN_PROCESO → LISTO → ENTREGADO` (o `CANCELADO`).

## Reglas de negocio

- Las franjas de recogida salen del horario de cada tienda (`slot_minutes`) y tienen aforo (`orders_per_slot`); las pasadas o completas no se pueden elegir. Los cancelados no ocupan franja.
- El total del pedido se calcula en el servidor con el precio actual de cada producto.
- Solo se pueden pedir productos activos. Un producto con pedidos no se borra: se desactiva.
- No se puede borrar una categoría con productos, un ingrediente usado en recetas, una empresa con clientes, un cliente con pedidos ni una tienda con pedidos.
- Los pedidos entregados o cancelados no se editan.
- Debe quedar siempre un administrador activo; nadie puede borrarse a sí mismo.

## Seguridad

`POST /api/auth/login` devuelve un JWT que se envía como `Authorization: Bearer <token>`.

| Rol | Permisos |
|---|---|
| VISOR | Solo lectura (GET) |
| GESTOR | Lectura y escritura de todo salvo usuarios |
| ADMIN | Todo, incluido `/api/users` |

`/api/public/**` no requiere login (pantalla pública de pedidos).

## Endpoints

| Recurso | Endpoints |
|---|---|
| Auth | `POST /api/auth/login`, `GET /api/auth/me` |
| Inicio | `GET /api/dashboard` |
| Pedidos | `GET /api/orders?search&status&storeId&from&to`, `GET/PUT/DELETE /api/orders/{id}`, `POST /api/orders`, `PATCH /api/orders/{id}/status` |
| Clientes | `GET /api/customers?search`, `GET /api/customers/{id}/orders`, CRUD `/api/customers` |
| Empresas | CRUD `/api/companies` |
| Productos | `GET /api/products?search&categoryId&active`, `PATCH /api/products/{id}/active`, CRUD `/api/products` |
| Ingredientes | `GET /api/ingredients?lowStock=true`, `PATCH /api/ingredients/{id}/stock`, CRUD `/api/ingredients` |
| Categorías | CRUD `/api/categories` |
| Tiendas | `GET /api/stores/{id}/slots?date=yyyy-MM-dd`, CRUD `/api/stores` |
| Usuarios | CRUD `/api/users` (solo ADMIN) |
| Público | `GET /api/public/stores`, `/stores/{id}/slots?date=`, `/categories`, `/products`, `POST /api/public/orders` |

Errores: siempre `{ status, message, fieldErrors, timestamp }` — 400 validación, 401 sin login, 403 sin permiso, 404 no existe, 409 regla de negocio.
