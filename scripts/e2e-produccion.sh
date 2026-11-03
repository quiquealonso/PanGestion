#!/usr/bin/env bash
# Monta PanGestion como en produccion para las pruebas end-to-end y espera a que responda:
#   - API: la imagen Docker de la VM, con PostgreSQL y Caddy (backend/deploy/docker-compose.yml y su Caddyfile)
#   - Web: el build de produccion de Angular, montado como GitHub Pages y servido por HTTPS
# Lo unico distinto de produccion: los dominios son localhost (API en https://localhost, web en
# https://localhost:4443/PanGestion/) y la base de datos empieza con los datos de demo.
# Necesita Docker. Para pararlo: scripts/e2e-produccion.sh down
set -euo pipefail
cd "$(dirname "$0")/.."

compose=(docker compose -p pangestion-e2e -f backend/deploy/docker-compose.yml -f backend/deploy/e2e/docker-compose.e2e.yml
  --env-file backend/deploy/e2e/.env)

if [ "${1:-}" = "down" ]; then
  "${compose[@]}" down -v
  exit 0
fi

PROD_API="https://34-175-204-112.sslip.io"
E2E_API="https://localhost"
WEB="https://localhost:4443"

echo "== Variables (secretos aleatorios, solo para esta prueba)"
cat > backend/deploy/e2e/.env <<EOF
API_DOMAIN=localhost
DB_USER=pangestion
DB_PASSWORD=$(openssl rand -hex 16)
JWT_SECRET=$(openssl rand -hex 32)
ENCRYPTION_KEY=$(openssl rand -base64 32)
CORS_ORIGINS=$WEB
SEED_DATA=true
EOF

echo "== Build de produccion del frontend (el mismo que se publica)"
npx ng build --base-href /PanGestion/app/
# El build apunta a la API de la VM: se cambia por la de la prueba (en el codigo y en la CSP de index.html)
grep -rl "$PROD_API" dist/gestion-pedidos/browser | xargs sed -i "s#$PROD_API#$E2E_API#g"
if grep -rq "$PROD_API" dist/gestion-pedidos/browser; then echo "Queda la API de produccion en el build"; exit 1; fi
# Los ficheros han cambiado: se rehace ngsw.json (hashes del service worker), como hace ng build
npx ngsw-config dist/gestion-pedidos/browser ngsw-config.json /PanGestion/app/
scripts/montar-pages.sh dist/gestion-pedidos/browser pages

echo "== API, PostgreSQL y Caddy (docker compose)"
"${compose[@]}" up -d --build

echo "== Esperando a la API y a la web"
for i in $(seq 1 90); do
  api=$(curl -sk -o /dev/null -w '%{http_code}' "$E2E_API/api/public/companies" || true)
  web=$(curl -sk -o /dev/null -w '%{http_code}' "$WEB/PanGestion/app/" || true)
  if [ "$api" = "200" ] && [ "$web" = "200" ]; then
    echo "Listo: API $E2E_API y web $WEB/PanGestion/app/"
    exit 0
  fi
  sleep 2
done
echo "No arranca (API $api, web $web)"
"${compose[@]}" logs --tail 80
exit 1
