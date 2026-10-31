# Pendientes

Cosas que se dejaron para más adelante. Cuando hagas una, márcala con `[x]`, apunta la fecha y bórrala cuando ya no haga falta.

## Cuentas de cliente y confirmación por email

El código está hecho y probado, pero las cuentas están **desactivadas** (`CUSTOMER_ACCOUNTS_ENABLED=false`). Mientras tanto, la web de pedidos funciona sin cuenta: nombre, email y teléfono se escriben en el formulario, como antes.

- [ ] **Crear un correo solo para la app**, por ejemplo `pangestion.avisos@gmail.com`. Mejor no usar el personal: así no aparece como remitente y la contraseña de aplicación no da acceso a tu buzón.
- [ ] **Activar la verificación en dos pasos** en esa cuenta de Google: https://myaccount.google.com/security. Sin esto, Google no deja crear contraseñas de aplicación.
- [ ] **Crear una contraseña de aplicación** en https://myaccount.google.com/apppasswords (nombre: «PanGestion»). Son 16 letras. No la pegues en ningún chat: va solo en el `.env` de la VM.
- [ ] **Configurar el SMTP y activar las cuentas** en el `.env` de la VM. Conéctate con el botón SSH de Google Cloud y ejecuta `nano ~/pangestion/app/backend/deploy/.env`. Rellena estas líneas:
  ```
  CUSTOMER_ACCOUNTS_ENABLED=true
  SMTP_HOST=smtp.gmail.com
  SMTP_PORT=587
  SMTP_USERNAME=pangestion.avisos@gmail.com
  SMTP_PASSWORD=lasdieciseisletras
  MAIL_FROM="PanGestión <pangestion.avisos@gmail.com>"
  ```
- [ ] **Reiniciar la API** para que lea la configuración:
  ```bash
  cd ~/pangestion/app/backend/deploy && docker compose up -d api
  ```
  No hace falta volver a desplegar el frontend: pregunta a la API (`GET /api/public/settings`) si las cuentas están activas.
- [ ] **Probarlo:**
  1. Crea una cuenta en la web de pedidos con un email tuyo.
  2. Te debe llegar «Confirma tu cuenta de PanGestion». Si no aparece, mira en Spam.
  3. Abre el enlace del email: deja la sesión iniciada.
- [ ] **Decidir qué tendrá la cuenta del usuario:** mis pedidos, datos guardados, direcciones, «olvidé mi contraseña»... El sitio previsto es la cabecera de la web de pedidos, donde ya sale «👤 Nombre · Salir».

Hay dos cosas que conviene saber:
- Si pones `CUSTOMER_ACCOUNTS_ENABLED=true` sin SMTP, las cuentas se activan al crearlas, sin confirmar el email.
- Gmail permite unos 500 envíos al día. Al principio algunos correos pueden ir a Spam; si pasa a menudo, la solución es un dominio propio con un proveedor como Brevo.

## Web promocional (`site/`)

- [ ] **Correo de contacto:** el formulario «Pide una demo» abre el correo del visitante con el mensaje ya escrito. Pon tu dirección en `data-email` del `<form id="contact-form">` en `site/index.html`; mientras esté vacío, el formulario avisa de que estará disponible pronto.
- [ ] **Precios:** cada plan tiene `data-price` en `site/index.html` (por ejemplo `data-price="39 €/mes"`); vacío muestra «Pide presupuesto». Revisa también qué incluye cada plan.
- [ ] **Textos comerciales:** repasa las promesas («te ayudamos a cargar la carta», «videollamada»...) y ajústalas a lo que vayas a ofrecer.
- [ ] **Textos legales (RGPD):** aviso legal, política de privacidad y de cookies, con enlace en el pie. Son obligatorios si la web recoge datos o se usa para vender. La web no usa cookies ni analítica, así que no necesita banner de cookies.
- [ ] **Dominio propio** (por ejemplo `pangestion.es`): da más confianza que `github.io`, permite un correo `hola@...` y GitHub Pages lo admite gratis. La API pasaría a `api.pangestion.es` (cambiar `API_DOMAIN`, CORS y la CSP de `src/index.prod.html`).
- [ ] **Demo para enseñar:** una empresa de prueba con datos inventados y un usuario de solo lectura, para enlazarla desde la web («Pruébala tú mismo»). Ahora no hay demo pública: la app de producción tiene datos reales.
- [ ] **Vídeo corto** (30-60 s) del flujo: pedido en el móvil → cocina → listo → cobro. Es lo que más convence; va en la sección «Pantalla a pantalla».
- [ ] **Testimonios reales:** cuando haya negocios usándola, una frase y su nombre (con su permiso). No poner testimonios inventados.
- [ ] **Cartel con código QR** para el mostrador, que lleve a la web de pedidos de cada negocio. La app aún no lo genera: sería un botón en «Mi empresa».
- [ ] **Analítica sin cookies** (Plausible o GoatCounter) para saber cuántas visitas hay y de dónde vienen; habría que añadirla a la CSP.
- [ ] **Google:** dar de alta la web en Google Search Console (el `sitemap.xml` ya está) y crear la ficha de Google Business.

## Producción

- [ ] **Guardar `ENCRYPTION_KEY` fuera de la VM**, por ejemplo en un gestor de contraseñas. Sin esa clave no se pueden leer los teléfonos, CIF y direcciones cifrados. Para verla, desde la VM: `grep ENCRYPTION_KEY ~/pangestion/app/backend/deploy/.env`.
- [ ] **Borrar `~/pangestion/backend` en la VM**: era el despliegue manual anterior y ya no se usa. Ahora se usa `~/pangestion/app`.
- [ ] **Revisar los avisos de Hibernate al arrancar**: no consigue aplicar algunos `alter column ... default` en PostgreSQL (columnas con `columnDefinition`). La API funciona igual, pero ensucian el log.
