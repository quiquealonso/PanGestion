# Contexto del Proyecto

## Objetivo
**PanGestión**: aplicación web para una panadería familiar (ver `../../ProjecteI/PanGestion.pdf`).
- **Panel de gestión** (backoffice): pedidos, clientes, empresas, productos, ingredientes, categorías, tiendas y usuarios, con resumen en la pantalla de inicio.
- **Parte pública**: pantalla "Nuevo pedido" donde el cliente elige tienda, día, franja y productos.

## Stack
- Frontend: Angular 22 (standalone, signals, control flow), SCSS, HttpClient. En la raíz del repo.
- Backend: Java 21, Spring Boot 4.1, Spring Data JPA / Hibernate, Spring Security con JWT (resource server, HS256), Bean Validation, Lombok. En `backend/`.
- BD: PostgreSQL (perfil `postgres`, `backend/docker-compose.yml`) o H2 en fichero (perfil por defecto `h2`). Hibernate genera las tablas (`ddl-auto=update`).

## Comandos
- Backend: `cd backend && ./mvnw spring-boot:run` (puerto 8080). Tests: `./mvnw test`.
- Frontend: `npm install --no-bin-links`, `npm start` (puerto 4200, proxy `/api` → 8080), `npm run build`.
- Angular CLI 22 exige Node >= 24.15 (en Windows: Node LTS 24.20 instalado con winget).
- El proyecto vive en un SSD portátil **exFAT** (sin symlinks ni permisos de ejecución): instalar con `npm ci --no-bin-links`. `node_modules` es específico del SO; al cambiar de sistema, borrar y reinstalar. Ver README.

## Backend (`backend/src/main/java/com/pangestion`)
- `model/`: entidades `Category`, `Ingredient`, `Product`, `ProductIngredient` (receta), `Company`, `Customer`, `Store`, `Order` (tabla `orders`), `OrderLine`, `User`; enums `OrderStatus`, `Role`.
- `repository/`, `service/`, `controller/`, `dto/` (records `XxxDto.Request` / `XxxDto.Response`), `exception/`, `security/`, `config/`.
- `config/DataInitializer`: datos de ejemplo y usuarios de prueba si la BD está vacía.
- Detalle de modelo, reglas y endpoints: `backend/README.md`.

## Frontend (`src/app`)
- `core/models`: interfaces alineadas con los DTOs de la API.
- `core/services`: `CrudService` base + un servicio por recurso, `AuthService` (token en localStorage), `NotificationService` (toasts), `PublicApiService`.
- `core/interceptors/auth.interceptor.ts` (Bearer + logout en 401), `core/guards/auth.guard.ts` (`authGuard`, `adminGuard`).
- `features/`: dashboard, orders (listado + detalle + wizard de alta/edición), customers, companies, catalog (products, ingredients, categories), stores, users, auth, public-order.
- Estilos compartidos (panel, listas, formularios, botones, etiquetas de estado) en `src/styles.scss`.

## Rutas
- `/auth/login`, `/pedido` (pública)
- Con login: `/dashboard`, `/orders`, `/orders/new`, `/orders/:id`, `/orders/:id/edit`, `/customers`, `/companies`, `/catalog/products`, `/catalog/ingredients`, `/catalog/categories`, `/stores`, `/users` (solo ADMIN)

## Roles
- ADMIN: todo. GESTOR: todo menos usuarios. VISOR: solo lectura (el front oculta botones y deshabilita formularios).

## Convenciones
- Código en inglés, textos de UI en español. Tema oscuro (Space Grotesk / IBM Plex Sans).
- Backend por capas: Controller → Service → Repository; los controladores nunca devuelven entidades.
- Priorizar cambios pequeños y verificables; usar este archivo como contexto antes de editar.

## Próximos pasos posibles
- Descontar stock de ingredientes al preparar pedidos.
- Paginación en listados grandes y tests de integración de la API.
- Despliegue (perfil postgres, secreto JWT por variable de entorno).
