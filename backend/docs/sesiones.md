# Sesiones del módulo y código de PanGestión

Correspondencia entre las sesiones de *Desarrollo web en entorno servidor* y el backend de PanGestión.
✅ hecho · 🟡 en parte · ❌ pendiente. Las comprobaciones de cada cambio están en [`comprobaciones.md`](comprobaciones.md).

## UD1 · HTTP y Spring Boot (S1–S4)

| S | Objetivo | Dónde |
|---|---|---|
| 1 | Arrancar Spring Boot; una respuesta correcta y un 404 | ✅ `PangestionApiApplication` |
| 2 | Rutas de listado y detalle con parámetros | ✅ `@GetMapping`, `@PathVariable` y `@RequestParam` en `controller/*` |
| 3 | JSON ↔ Java y primera escritura | ✅ `@RequestBody` + DTO |
| 4 | CRUD de dos entidades | ✅ superado por la persistencia (UD5) |

## UD2 · Peticiones, respuestas y CRUD (S5–S8)

| S | Objetivo | Dónde |
|---|---|---|
| 5 | Distinguir JSON mal formado, tipo incompatible y datos incompletos; modo estricto | ✅ `fail-on-unknown-properties=true`; `GlobalExceptionHandler.handleUnreadable` |
| 6 | Códigos correctos: 201 + Location, 204, 404; PATCH | ✅ `Locations` + `ResponseEntity.created(...)`; `PATCH /orders/{id}/status`, `/products/{id}/active` |
| 7 | Colección ejecutable con entorno y aserciones | ✅ `pruebas/PanGestion.postman_collection.json` + `Local.postman_environment.json` |
| 8 | Relación entre entidades y colección repetible | ✅ `GET /api/customers/{id}/orders`; la colección limpia sus datos |

## UD3 · REST, DTO, validación y errores (S9–S14)

| S | Objetivo | Dónde |
|---|---|---|
| 9 | Contrato REST; creación anidada con Location al detalle canónico | ✅ `POST /api/customers/{id}/orders` → `Location: /api/orders/{id}` |
| 10 | DTO de salida sin datos internos | ✅ `dto/*.Response` (nunca la contraseña ni entidades) |
| 11 | DTO de entrada; ids y calculados los pone el servidor | ✅ `dto/*.Request` y `from()`; id, total y orden los calcula el servidor |
| 12 | Validar las entradas | ✅ Bean Validation + validación propia `validation/ValidOpeningHours` |
| 13 | 400/404/409 coherentes y sin trazas | ✅ `GlobalExceptionHandler` + `ErrorResponse` (también 405, 413 y 415) |
| 14 | Publicar el contrato; el repo compila en CI | ✅ `README.md` (Endpoints y contrato común) + `.github/workflows/ci.yml` |

## UD4 · Arquitectura por capas (S15–S18)

| S | Objetivo | Dónde |
|---|---|---|
| 15 | Separar controller, service y repository | ✅ `controller/` → `service/` → `repository/` |
| 16 | Inyección de dependencias por constructor | ✅ `@RequiredArgsConstructor` en todos los componentes |
| 17 | Reglas de negocio probadas con JUnit + Mockito; una regresión hace fallar un test | ✅ `@Tag("reglas-negocio")`: `OrderServiceRulesTest`, `CompanyServiceSlotsTest`, `UserServiceRulesTest`, `OrderModelTest`. Único grupo que ejecuta el CI |
| 18 | Justificar dónde va cada regla | ✅ las reglas viven en los servicios (`README.md` › Reglas de negocio) |

## Unidades siguientes (ya avanzadas en el proyecto)

| S | Objetivo | Estado |
|---|---|---|
| 19–21 | PostgreSQL sin credenciales en el repo, entidades JPA y consultas propias | ✅ perfil `postgres`, `.env`, `model/*`, `@Query` |
| 22 | Tests de repositorio con `@DataJpaTest` contra PostgreSQL | 🟡 `repository/*Test` hechos con H2 (pasar a una base `pangestion_test`) |
| 23–25 | Relaciones 1:N y N:M, transacciones e integridad | ✅ Empresa→Categoría→Producto, Pedido→Líneas, Producto↔Ingrediente, `@Transactional` |
| 26–28 | N+1 y versión desplegada | 🟡 `@EntityGraph`, `join fetch`, `batch_fetch_size` · ❌ despliegue |
| 29 | Relaciones expuestas y filtros | ✅ `search`, `status`, `from`, `to`, `categoryId`, `active` |
| 30 | Paginación y ordenación | ❌ |
| 31 | Tests HTTP (MockMvc) y OpenAPI | 🟡 `web/*ApiTest` · ❌ OpenAPI |
| 32 | Evolucionar el contrato | ❌ |
| 33–34 | Cliente de navegador y CORS | ✅ Angular + `SecurityConfig.corsConfigurationSource` |
| 35–39 | Permisos, BCrypt, usuarios y roles, permisos por recurso, JWT | ✅ `SecurityConfig`, `TokenService`, `CurrentUser`; probado en `SecurityApiTest` |
| 40 | CSRF, CORS con credenciales y cierre seguro | 🟡 CSRF desactivado por ser API sin estado (JWT) · ❌ cierre de sesión en el servidor |
| 41–42, 44 | Servicio externo, timeouts y fallos parciales | ❌ |
| 43 | Ficheros | 🟡 imágenes validadas por su firma (`storage/`); son públicas a propósito |
| 45–46 | Estrategia de pruebas, logs y documentación | 🟡 202 tests y documentación · ❌ cobertura (JaCoCo) |
| 47–52 | Ampliación final, Angular, despliegue y defensa | 🟡 Angular conectado · ❌ despliegue |
