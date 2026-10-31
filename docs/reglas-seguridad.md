# Reglas de seguridad de PanGestión

Cada regla indica cómo se cumple, dónde está el código y qué la comprueba. Antes de fusionar en `main`, repasa las reglas que toque tu cambio.

**Estados:**
- ✅ cumplida
- ➖ no aplica a esta arquitectura (se explica por qué)
- ⏳ pendiente

## Cómo añadir una regla

1. Añade una sección `### S<número> · <nombre>` al final de su bloque, sin reutilizar números de reglas borradas.
2. Rellena los cinco campos: **Estado**, **Cómo**, **Dónde**, **Comprobación** y **Fecha**.
3. Si la regla se puede automatizar, añade su test o su paso de CI y enlázalo en **Comprobación**.

---

## Secretos y configuración

### S01 · Ocultar claves y secretos
- **Estado:** ✅
- **Cómo:** los secretos (contraseña de la BBDD, `JWT_SECRET`, `ENCRYPTION_KEY`) solo existen como variables de entorno o en el `.env` de cada máquina, y `.env` está en `.gitignore`. En Git solo hay `.env.example` con valores de ejemplo. Las claves de desarrollo (perfil H2) y las de los tests son distintas de las de producción. En GitHub, la clave de despliegue es un *secret* del repositorio.
- **Dónde:** `.gitignore`, `backend/deploy/.env.example`, `application*.properties`
- **Comprobación:** gitleaks en el workflow `Seguridad` (`.github/workflows/security.yml`)
- **Fecha:** 2026-10-30

### S02 · Ningún secreto en el historial de Git
- **Estado:** ✅
- **Cómo:** gitleaks revisa todo el historial en cada PR, en cada push a `main` y `dev`, y cada lunes. Las excepciones, revisadas a mano, están en `.gitleaks.toml`. Si alguna vez se sube un secreto: rótalo primero (genera uno nuevo), luego purga el historial con `git filter-repo` y haz force push.
- **Dónde:** `.gitleaks.toml`, `.github/workflows/security.yml`
- **Comprobación:** job `Secretos en el historial de Git`
- **Fecha:** 2026-10-30

### S03 · Clave pública de base de datos (tipo Supabase)
- **Estado:** ➖
- **Cómo:** no aplica. El navegador nunca habla con la base de datos: solo lo hace la API. PostgreSQL no publica ningún puerto (solo es accesible en la red interna de Docker), y la contraseña de la BBDD solo la conoce el contenedor de la API.
- **Dónde:** `backend/deploy/docker-compose.yml` (servicio `db` sin `ports`)
- **Comprobación:** revisión del compose
- **Fecha:** 2026-10-30

## Autenticación y acceso

### S04 · Seguridad a nivel de fila (aislamiento entre empresas)
- **Estado:** ✅ (en la aplicación, no con RLS de PostgreSQL)
- **Cómo:** el JWT lleva el `companyId`, y todos los servicios filtran por él mediante `CurrentUser`. Si se pide un id de otra empresa, la respuesta es 404, no 403, para no confirmar que existe.
- **Dónde:** `security/CurrentUser`, servicios (`findByIdAndCompanyId`...)
- **Comprobación:** `SecurityApiTest` y los `*ApiTest` (aislamiento entre empresas)
- **Fecha:** 2026-10-30

### S05 · Autenticación forzada en el servidor
- **Estado:** ✅
- **Cómo:** toda la API exige un JWT válido, salvo `/api/auth/login`, `/api/public/**` y `/uploads/**`. Los permisos por rol se definen en `SecurityConfig`, no en el frontend. El token de gestión caduca a las 8 horas. Hacer un pedido en la web pública y ver la cuenta exigen el token de un cliente (rol `CLIENTE`), y ese rol no puede usar nada de la API de gestión (S21).
- **Dónde:** `config/SecurityConfig.java`
- **Comprobación:** `SecurityApiTest`, `AuthApiTest`
- **Fecha:** 2026-10-30

### S06 · Bloquear el acceso a registros ajenos (IDOR)
- **Estado:** ✅
- **Cómo:** igual que S04. Además, la web pública no devuelve los datos guardados de un cliente aunque se conozca su email.
- **Dónde:** servicios, `OrderDto.PublicResponse`
- **Comprobación:** `PublicApiTest.customerTampering` y los tests de aislamiento
- **Fecha:** 2026-10-30

### S07 · Bloquear la manipulación de campos (mass assignment)
- **Estado:** ✅
- **Cómo:** la API solo recibe DTO (`record`) con los campos permitidos. Un campo desconocido se rechaza con 400 (`fail-on-unknown-properties`). El id, la empresa, los totales y el rol los fija siempre el servidor. Un pedido anónimo no puede cambiar los datos de un cliente que ya existe.
- **Dónde:** `dto/*`, `application.properties`, `CustomerService.findOrCreate`
- **Comprobación:** `ErrorHandlingApiTest`, `PublicApiTest.returningCustomer`
- **Fecha:** 2026-10-30

### S08 · Cookies de sesión seguras
- **Estado:** ➖
- **Cómo:** no hay cookies. El JWT viaja en la cabecera `Authorization`. El frontend (github.io) y la API (sslip.io) son sitios distintos, y una cookie de sesión entre sitios necesitaría `SameSite=None`, que Safari y Chrome bloquean. El riesgo de robar el token por XSS se reduce con S15 (escapado) y la CSP de S18.
- **Dónde:** `core/services/auth.service.ts`, `core/interceptors`
- **Comprobación:** —
- **Fecha:** 2026-10-30

### S09 · Contraseñas con hash
- **Estado:** ✅
- **Cómo:** se usa BCrypt, tanto para usuarios de gestión como para cuentas de cliente, y las contraseñas deben tener 8 caracteres como mínimo. Nunca se devuelven en la API.
- **Dónde:** `SecurityConfig.passwordEncoder`, `UserDto.Request`
- **Comprobación:** `UserApiTest`
- **Fecha:** 2026-10-30

### S10 · Limitar los intentos de login
- **Estado:** ✅
- **Cómo:** se permiten 20 intentos cada 5 minutos por IP y 10 cada 15 minutos por email, tanto en el login de gestión como en el de clientes; al pasarse, la API responde 429 con `Retry-After`. El registro de clientes admite 5 por hora y por IP, y el reenvío del enlace, 3 por hora y por email. El login tarda lo mismo exista o no el email, así que no revela qué cuentas existen. La IP real llega de Caddy (`server.forward-headers-strategy=native`).
- **Dónde:** `security/RateLimiter`, `AuthController`, `AuthService`, `CustomerAccountController`
- **Comprobación:** `RateLimiterTest`
- **Fecha:** 2026-10-30

### S11 · Protección contra bots
- **Estado:** ✅
- **Cómo:** para pedir hay que tener una cuenta con el email confirmado (S21). Además, la web pública admite 10 pedidos por hora y por IP, y el formulario tiene un campo trampa oculto (`website`); si llega relleno, la API responde 400.
- **Dónde:** `PublicController`, `OrderDto.PublicRequest`, `public-order-page`
- **Comprobación:** `PublicApiTest.honeypot`, `RateLimiterTest`
- **Fecha:** 2026-10-30

## Datos y entradas

### S12 · Cifrar los datos sensibles
- **Estado:** ✅
- **Cómo:** el teléfono y el CIF de los clientes, el teléfono de las cuentas de cliente y la dirección de entrega se guardan cifrados con AES-256-GCM (prefijo `enc:v1:`). Los datos antiguos se cifran al arrancar. La clave es `ENCRYPTION_KEY`: guarda una copia, porque sin ella esos datos no se pueden leer. Las contraseñas no se cifran, se hashean (S09).
- **Dónde:** `security/FieldEncryptor`, `security/EncryptedString`, `config/SensitiveDataMigration`
- **Comprobación:** `FieldEncryptorTest`, `PublicApiTest.createOrder` (comprueba que la BBDD guarda el valor cifrado)
- **Fecha:** 2026-10-30

### S13 · Consultas parametrizadas
- **Estado:** ✅
- **Cómo:** se usa JPQL con parámetros (`:nombre`) y nunca se concatena texto del usuario. El SQL dinámico de `SchemaUpgrade` y de `SensitiveDataMigration` solo usa nombres fijos del propio código.
- **Dónde:** `repository/*`
- **Comprobación:** revisión de código (buscar `createQuery` o `nativeQuery` con `+`)
- **Fecha:** 2026-10-30

### S14 · Validar todas las entradas
- **Estado:** ✅
- **Cómo:** todos los `@RequestBody` llevan `@Valid` y su DTO tiene Bean Validation (`@NotBlank`, `@Size`, `@Email`...). El frontend también valida, pero la validación que cuenta es la del servidor.
- **Dónde:** `controller/*`, `dto/*`
- **Comprobación:** tests `validation` de cada `*ApiTest`
- **Fecha:** 2026-10-30

### S15 · Escapar el contenido del usuario
- **Estado:** ✅
- **Cómo:** Angular escapa toda interpolación. No se usa `innerHTML` ni `bypassSecurityTrust*`, y la API solo devuelve JSON.
- **Dónde:** plantillas de `src/app`
- **Comprobación:** revisión de código (buscar `innerHTML` o `bypassSecurityTrust`)
- **Fecha:** 2026-10-30

### S16 · Restringir la subida de archivos
- **Estado:** ✅
- **Cómo:** solo se aceptan JPEG, PNG o WebP, reconocidos por los primeros bytes y no por la extensión. El tamaño máximo es de 2 MB y el nombre es un UUID, con lo que no se pueden usar rutas tipo `../`. Se sirven con `nosniff` y no se ejecuta nada.
- **Dónde:** `storage/ImageType`, `storage/LocalImageStorage`, `WebConfig`
- **Comprobación:** `ImageTypeTest`, `LocalImageStorageTest`, `ProductImageApiTest`
- **Fecha:** 2026-10-30

### S17 · Recortar las respuestas de la API
- **Estado:** ✅
- **Cómo:** la web pública recibe DTO reducidos: las empresas sin CIF, email interno ni contadores, y los pedidos sin los datos guardados del cliente. Los errores no incluyen trazas.
- **Dónde:** `CompanyDto.PublicResponse`, `OrderDto.PublicResponse`, `GlobalExceptionHandler`
- **Comprobación:** `PublicApiTest.companies`, `PublicApiTest.createOrder`
- **Fecha:** 2026-10-30

## Transporte y cabeceras

### S18 · Cabeceras de seguridad
- **Estado:** ✅
- **Cómo:** la API, a través de Caddy, envía HSTS, una CSP `default-src 'none'`, `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy` y `Permissions-Policy`. El frontend de producción lleva una CSP en un `<meta>` (sin scripts inline) porque GitHub Pages no permite cabeceras propias. Si cambia la URL de la API, actualízala también en `src/index.prod.html`.
- **Dónde:** `backend/deploy/Caddyfile`, `src/index.prod.html`
- **Comprobación:** prueba de humo del despliegue (`deploy.yml`)
- **Fecha:** 2026-10-30

### S19 · Forzar HTTPS
- **Estado:** ✅
- **Cómo:** Caddy obtiene el certificado de Let's Encrypt y redirige `http` a `https`, y HSTS lo deja fijado en el navegador. GitHub Pages tiene «Enforce HTTPS» activado. La CSP del frontend incluye `upgrade-insecure-requests`.
- **Dónde:** `backend/deploy/Caddyfile`
- **Comprobación:** prueba de humo del despliegue (`deploy.yml`)
- **Fecha:** 2026-10-30

## Dependencias

### S20 · Escanear las dependencias
- **Estado:** ✅
- **Cómo:** Dependabot abre PR semanales contra `dev` para npm, Maven, Docker y Actions. El workflow `Seguridad` ejecuta `npm audit` (falla con vulnerabilidades altas) y OSV-Scanner (frontend y backend) en cada PR y cada lunes.
- **Dónde:** `.github/dependabot.yml`, `.github/workflows/security.yml`
- **Comprobación:** job `Dependencias con vulnerabilidades`
- **Fecha:** 2026-10-30

## Cuentas de cliente

### S21 · Cuentas de cliente con email confirmado
- **Estado:** ✅ (la confirmación por email necesita SMTP; sin él, la cuenta se activa al crearla)
- **Cómo:** para pedir en la web pública hay que tener una cuenta. Al crearla se envía un enlace de un solo uso, que caduca a las 24 horas; en la BBDD solo se guarda su SHA-256. Hasta abrirlo no se puede entrar (403). El rol `CLIENTE` no tiene acceso a la API de gestión. Registrarse con un email que ya tiene cuenta responde igual que con uno nuevo (no revela qué emails existen) y avisa al dueño por email, sin cambiar su contraseña. El nombre y el email del pedido salen de la cuenta, no del formulario. La sesión del cliente dura 30 días y es independiente de la de gestión.
- **Dónde:** `CustomerAccountService`, `CustomerAccountController`, `MailService`, `SecurityConfig`, `customer-auth.service.ts`
- **Comprobación:** `CustomerAccountApiTest`, `CustomerAccountEmailApiTest`, `PublicApiTest.requiresCustomerAccount`
- **Fecha:** 2026-10-30

### S22 · Las apps instaladas se actualizan con cada despliegue
- **Estado:** ✅
- **Cómo:** cada despliegue tiene una versión (`1.0.<número de despliegue>`). El service worker la compara con la publicada al abrir la app, al volver a ella y cada 5 minutos, y muestra el botón «Actualizar», así nadie se queda con una versión con fallos ya corregidos.
- **Dónde:** `AppUpdateService`, `pwa-banner.component.ts`, `deploy.yml` (paso «Version»)
- **Comprobación:** prueba de humo del despliegue (`ngsw.json` publicado)
- **Fecha:** 2026-10-30

