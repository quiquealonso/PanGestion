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
`OrderServiceRulesTest` (12), `CompanyServiceSlotsTest` (11), `UserServiceRulesTest` (12) y `OrderModelTest` (6) = 41 tests
al adaptar las sesiones. Con la sala se añadieron `DiningModelTest` (3), `TableSessionModelTest` (6) y `KitchenModelTest` (8): **58 tests**.
El CI ejecuta solo estos: `./mvnw -B verify -Dgroups=reglas-negocio`.

**Regresiones provocadas a propósito** (sin integrarlas) para demostrar que los tests las detectan:

| Avería introducida | Test que la detecta | Resultado | Tras corregir |
|---|---|---|---|
| `CompanyService.validateSlot`: `countInSlot(...) >= ordersPerSlot` cambiado por `>` (deja entrar un pedido de más en una franja llena) | `CompanyServiceSlotsTest.validateFull` · «Expecting code to raise a throwable» | 41 tests, 1 fallo, BUILD FAILURE (el PR quedaría bloqueado) | 41 / 41 |
| `OrderService.setLines`: la clave de agrupación sin los ingredientes quitados (junta «pizza» y «pizza sin cebolla») | `OrderServiceRulesTest.customizedLinesAreSeparate` | 41 tests, 1 fallo, BUILD FAILURE | 41 / 41 |

## Batería completa (en local)

`./mvnw verify`: **279 tests, 0 fallos** (202 al adaptar las sesiones + 77 de clientes, sala, extras, orden en cocina, reparto y logo de la empresa). Además de los 58 de la S17 incluye
repositorios (S22), HTTP con MockMvc (S31), permisos y aislamiento entre empresas (S38) y el arranque con datos de ejemplo. No se ejecutan en el CI.

Los tests destaparon un fallo: `DataInitializer` no tenía `@Order(1)` y, con la base de datos vacía y los pedidos
de demo activados, `DemoOrdersGenerator` arrancaba antes y fallaba. Corregido.

## Sala de bares y restaurantes (fases 1–6)

Cada fase se probó con sus tests y después en el navegador con los usuarios del bar (admin, camarero y cocina).

| Qué se comprobó | Cómo | Resultado |
|---|---|---|
| Arrastrar una mesa encima de otra | Ratón en el editor | Vuelve a su sitio y avisa «Ahí no cabe: pisaría otra mesa» |
| Tres clics rápidos en «+ plazas» | Script en el navegador | Al principio el servidor guardaba uno menos (peticiones en paralelo que llegaban desordenadas). Arreglado con cola por mesa: pantalla y servidor coinciden |
| Renombrar una mesa con un nombre que ya existe | Panel lateral | 409 «Ya existe una mesa con el nombre 2» y el nombre vuelve al anterior |
| Abrir mesa juntando la 4 y la 5 con 4 comensales | Móvil (375 × 812) como camarero | «Mesa 4 + 5»; el plano marca las dos ocupadas |
| Ronda con comensales, «sin ingrediente» y nota | Comanda | Agrupado por comensal con la ronda R1; total correcto (antes del 28-10: ya no hay comensal por producto) |
| Cocina pasa un producto a listo | Pantalla de cocina con el ratón y por API con el token de cocina | Aparece en «Listo para servir»; el camarero ve «Servir» y la mesa en verde |
| Aviso en tiempo real | Camarero con la sala abierta; cocina marca «listo» desde otra sesión | Aviso «🔔 Mesa 1 · 1× Diabola (C2) · tu mesa» al instante, sin recargar |
| Ronda nueva en cocina | Cocina con su pantalla abierta; el camarero envía una ronda | Aparece el ticket sin recargar (y pita) |
| Cobro por comensal en efectivo | Barra: C1 paga 66,25 € con 70 € | Cambio 3,75 €; pendiente 25,75 € (antes del 28-10: el reparto era «lo suyo + lo compartido») |
| Resto con tarjeta y liberar | Barra | «Mesa 1 cobrada y libre»; caja del día: 66,25 € efectivo, 25,75 € tarjeta |
| Liberar una mesa sin cobrar | API y comanda | 409 «Quedan 9,50 € por cobrar en la mesa 1» |
| Informe de sala | Página de informes como admin | Cifras coherentes con la caja y gráficos con tooltip y tabla |
| Inicio y menú por tipo de negocio | Admin del bar y admin de la panadería | Bar: menú Sala / Para llevar e inicio de sala; al abrir una mesa y enviar una ronda, el inicio pasa a 1/9 mesas, 2 en cocina y 1 en barra sin recargar. Panadería: igual que antes |
| Pasar una unidad en cocina | Pantalla de cocina (1400 px): «3× Pepperoni» de la mesa 11 preparándose, dos toques | 1.º: 2× preparando y 1× en Listo; 2.º: 1× y 2× (se juntan en una fila) |
| Columnas y tarjetas de cocina | Con 8 mesas abiertas y el menú lateral plegado | Preparando (más tickets) se ensancha y Pendiente se estrecha; Listo en una columna; color e inicial de categoría en cada fila |
| Usuario de cocina | Entrar con `cocina@pangestion.local` e ir a `/orders` | Vuelve a `/kitchen`; solo ve «Cocina y barra»; sin avisos de listo; por API `/api/products` da 403 |
| Comanda sin comensales | Móvil (375 × 812) como camarero, mesa 11: 2 Vegetal y 1 Calzone | Sin chips de comensal; «Por enviar» y «Pedido» en lista única; ronda R2 enviada |
| Cobro entre comensales | Barra, mesa 11 (65 €, 4 comensales) | 16,25 € cada uno |
| Extras en la comanda | Camarero, mesa 11: Barbacoa sin cebolla y con huevo, más una caña | 9,50 € (8,50 + 1,00); en cocina «+ Huevo» verde y «sin Cebolla» rojo; la caña solo en Barra |
| Orden en cocina | Admin del bar, «Orden en cocina»; mesa 1 en Preparando | Entrantes arriba (Calamar, Pechuguitas) y luego pizzas |
| Bebidas en barra | Categoría Bebidas con «Se prepara en: Barra» | Sus 6 productos en barra; en el formulario del producto, el desplegable bloqueado |
| Barra sin «Preparando» | Pantalla de barra como usuario de cocina (1400 px) | Dos columnas: Pendiente (todo el ancho) y Listo para servir |
| Repartidor en el móvil (375 × 812) | Entrar con `repartidor@pangestion.local` | Entra en Reparto; menú inferior «Reparto / Yo»; #641 en «Para llevar» |
| Me lo llevo y entregado | Tocar #641 → «Me lo llevo»; luego «Entregado» | Pasa a «Míos» (Transportando a su nombre) y después a «Entregados» |
| Aviso al repartidor | Con la pantalla abierta, el admin pone #642 en «Listo» | Aviso «🏍️ #642 · 21:45 · Luis Pérez · Av. del Mar 4 · Listo para llevar» al instante y la lista se actualiza |
| Camarero en el móvil | Servicio de mesas y comanda (375 × 812) | Menú inferior Mesas / Cobrar / Yo; lista de mesas por defecto; «Enviar» encima del menú |
| Admin en el móvil | «☰ Menú» | Hoja con usuario, todo el menú, modo y cerrar sesión; en escritorio el lateral sigue igual |
| Menú lateral y del usuario | Plegar/desplegar con la flecha; tocar las iniciales | Franja estrecha con las iniciales; menú con rol, nombre, email, modo claro/oscuro y cerrar sesión; se cierra con Escape o clic fuera |

**Fallos encontrados por el camino (y corregidos):**

| Fallo | Causa | Arreglo |
|---|---|---|
| No se podía crear un camarero en la BD ya existente (409 «viola una restricción») | Hibernate había creado `users.role` como `ENUM` (H2) / con `CHECK` (PostgreSQL) con los roles antiguos, y `ddl-auto=update` no lo amplía | `config/SchemaUpgrade` al arrancar |
| Un cobro anulado seguía contando en la caja | Quitarlo de la colección (`orphanRemoval`) no lo borraba antes de la consulta de totales | `paymentRepository.delete(payment)` explícito; lo detecta `PaymentApiTest.equalPartsAndDay` |
| Modales y barra de enviar sin fondo en el tema oscuro | Al crear el modo claro, `--panel-strong` quedó como `var(--panel-strong)` (se refería a sí mismo) | Restaurado `rgba(13, 25, 48, 0.96)` |
| La comanda se salía del ancho en el móvil | Las filas con scroll horizontal ensanchaban la rejilla (`min-width: auto`) | `min-width: 0` en los hijos y `minmax(0, 1fr)` |
| Al pasar una unidad no se juntaba con la que ya estaba en ese estado (quedaban 1× + 1×) | Los ingredientes quitados son una colección de Hibernate (`PersistentBag`), cuyo `equals` compara la instancia, no el contenido | `sameDishAs` compara copias (`List.copyOf`); lo detecta `KitchenApiTest.oneUnitAtATime` |
| Arrancaba sin `productRepository` («No property 'search' found») | El método nuevo `findAllByCategoryId` se insertó entre el `@Query` y `search`, que se quedó sin consulta | Movido arriba del todo |
| La columna «Listo» de cocina tenía barra horizontal | El contenido sobresalía unos píxeles al aparecer la barra vertical | `overflow-x: hidden` en las columnas |
| El menú del usuario no se veía con el lateral plegado | El lateral recorta lo que sobresale (`overflow` y `backdrop-filter`) | El menú se pinta fuera del lateral, con `position: fixed` |
| `ng serve` no encontraba las páginas nuevas | El servidor de desarrollo no ve carpetas creadas mientras corre | Reiniciarlo (anotado en `context.md`) |

**No verificado:** la impresión real en papel de la precuenta (se comprobó el contenido del bloque, no el papel) y las
notificaciones del sistema con la pestaña oculta (necesitan conceder permiso a mano).
