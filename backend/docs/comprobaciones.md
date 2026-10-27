# Comprobaciones

Registro de lo comprobado al adaptar PanGestión a las sesiones del módulo (Desarrollo web en entorno servidor).
Qué parte del código corresponde a cada sesión: [`sesiones.md`](sesiones.md).

## S5 · JSON estricto

`spring.jackson.deserialization.fail-on-unknown-properties=true` en `application.properties`.

| Petición | Antes | Ahora |
|---|---|---|
| `POST /api/ingredients` `{"name":"Sal","alergeno":"ninguno"}` (errata) | 201 y el alérgeno se perdía en silencio | 400 `Campo no permitido: alergeno` |
| `POST /api/ingredients` `{"id":99,"name":"Sal"}` | 201, el `id` se ignoraba | 400 `Campo no permitido: id` (el id lo pone el servidor) |
| `POST /api/ingredients` con `{"name": ` (cortado) | 400 | 400 `Peticion mal formada` |

Antes de activarlo se revisó que todos los formularios de Angular envían exactamente los campos de cada DTO
(el de productos quita `ownColor` antes de enviar). Test: `ErrorHandlingApiTest.unknownField`.

## S6 y S9 · 201 + Location y creación anidada

| Petición | Resultado |
|---|---|
| `POST /api/categories` | 201, `Location: http://localhost:8080/api/categories/{id}` |
| `POST /api/customers/{id}/orders` (sin `customerId` en el cuerpo) | 201, `Location: …/api/orders/{id}` (detalle canónico) |
| `POST /api/customers/{id}/orders` con `"customerId"` en el cuerpo | 400 `Campo no permitido: customerId`: la ruta manda sobre el cuerpo |
| `POST /api/customers/{id-de-otra-empresa}/orders` | 404 y no se crea nada |
| `POST /api/public/orders` | 201, `Location: …/api/orders/{id}` |

Tests: `CustomerApiTest.nestedOrder*`, `CategoryApiTest.create`, `CompanyApiTest.createLocation`, `OrderApiTest.create`, `PublicApiTest.createOrder`.

## S12 · Validación propia

`@ValidOpeningHours` sobre `CompanyDto.Request`: la apertura debe ser anterior al cierre.

| Petición | Antes | Ahora |
|---|---|---|
| Empresa con apertura 20:00 y cierre 08:00 | 409 (lo detectaba el servicio) | 400, `fieldErrors.closingTime = "debe ser posterior a la hora de apertura"` |
| Apertura 08:00 y cierre 08:00 | 409 | 400 |

El servicio conserva su comprobación como segunda barrera. Test: `CompanyApiTest.createInvalidHours`.

## S13 · Errores coherentes

Todos con `{status, message, fieldErrors, timestamp}` y sin trazas:

| Caso | Estado | Mensaje |
|---|---|---|
| `GET /api/orders/production` sin `date` | 400 | `Falta el parametro obligatorio: date` |
| `GET /api/no-existe` | 404 | `Ruta no encontrada` |
| `PATCH /api/categories` | 405 | `Metodo PATCH no permitido en esta ruta` |
| `POST /api/ingredients` con `text/plain` | 415 | `Tipo de contenido no admitido: envia application/json` |

Test: `ErrorHandlingApiTest`.

## S7 y S8 · Colección repetible

`pruebas/PanGestion.postman_collection.json` + `pruebas/Local.postman_environment.json`: 23 peticiones y 41 aserciones.
Hace login y guarda el token. Después crea categoría, ingrediente, producto, cliente y pedido anidado,
guardando cada id que devuelve el servidor. Comprueba estados, `Location`, cuerpos y reglas
(400 por campo de más, 409 al borrar un cliente con pedidos) y al final borra todo y comprueba los 404.

```
npx newman run pruebas/PanGestion.postman_collection.json -e pruebas/Local.postman_environment.json
```

| Ejecución | Peticiones | Aserciones | Fallos |
|---|---|---|---|
| 1.ª | 23 | 41 | 0 |
| 2.ª (seguida, sin tocar la BD) | 23 | 41 | 0 |

La segunda pasada demuestra que no depende de la ejecución anterior.

## S17 · Reglas de negocio probadas (y CI)

Tests con JUnit 5 + Mockito, sin base de datos, marcados con `@Tag("reglas-negocio")`:
`OrderServiceRulesTest` (12), `CompanyServiceSlotsTest` (11), `UserServiceRulesTest` (12) y `OrderModelTest` (6) = **41 tests**.
El CI ejecuta solo estos: `./mvnw -B verify -Dgroups=reglas-negocio`.

**Regresiones provocadas a propósito** (sin integrarlas) para demostrar que los tests las detectan:

| Avería introducida | Test que la detecta | Resultado | Tras corregir |
|---|---|---|---|
| `CompanyService.validateSlot`: `countInSlot(...) >= ordersPerSlot` cambiado por `>` (deja entrar un pedido de más en una franja llena) | `CompanyServiceSlotsTest.validateFull` · «Expecting code to raise a throwable» | 41 tests, 1 fallo, BUILD FAILURE (el PR quedaría bloqueado) | 41 / 41 |
| `OrderService.setLines`: la clave de agrupación sin los ingredientes quitados (junta «pizza» y «pizza sin cebolla») | `OrderServiceRulesTest.customizedLinesAreSeparate` | 41 tests, 1 fallo, BUILD FAILURE | 41 / 41 |

## Batería completa (en local)

`./mvnw verify`: **202 tests, 0 fallos**. Además de los 41 de la S17 incluye repositorios (S22), HTTP con MockMvc (S31),
permisos y aislamiento entre empresas (S38) y el arranque con datos de ejemplo. No se ejecutan en el CI.

Los tests destaparon un fallo: `DataInitializer` no tenía `@Order(1)` y, con la base de datos vacía y los pedidos
de demo activados, `DemoOrdersGenerator` arrancaba antes y fallaba. Corregido.
