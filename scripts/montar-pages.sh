#!/usr/bin/env bash
# Monta la carpeta que se publica en GitHub Pages: la web promocional (site/) en la raiz y la app en app/.
# Lo usan el despliegue (deploy.yml) y las pruebas end-to-end en modo produccion (e2e.yml), para que
# las pruebas usen exactamente lo mismo que se publica.
#   scripts/montar-pages.sh <build de Angular (dist/.../browser)> <carpeta de salida>
set -euo pipefail

build="${1:?Falta la carpeta del build de Angular}"
out="${2:?Falta la carpeta de salida}"

rm -rf "$out"
mkdir -p "$out"
cp -r site/. "$out/"
cp -r "$build" "$out/app"
# GitHub Pages no conoce las rutas de Angular: pedido/index.html para que la web de pedidos (y su app
# instalable) responda 200, y 404.html = la app, para que cualquier ruta funcione al recargar.
# Los enlaces de antes de mover la app a /app/ (/pedido, /auth/login...) los redirige pwa-init.js.
mkdir -p "$out/app/pedido" && cp "$out/app/index.html" "$out/app/pedido/index.html"
cp "$out/app/index.html" "$out/404.html"
# Quien instalo la app cuando estaba en la raiz tiene alli su service worker: se cambia por el
# "safety worker" de Angular, que lo da de baja y borra su cache (despues abre la web nueva)
cp "$out/app/safety-worker.js" "$out/ngsw-worker.js"
