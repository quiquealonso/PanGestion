#!/usr/bin/env bash
# Despliegue de la API en la VM. Lo lanza GitHub Actions (deploy.yml) por SSH con la clave de despliegue,
# que en ~/.ssh/authorized_keys solo puede ejecutar este script (command="..."): recibe el commit a desplegar.
# Se ejecuta la copia ~/pangestion/deploy.sh (fuera del repositorio); tras cada despliegue se actualiza con esta.
#
#   ssh pangestion@VM <sha>                (desde Actions)
#   ~/pangestion/deploy.sh <sha>           (a mano, en la VM)
#
# 1. Comprueba que el commit esta en main  2. Copia de seguridad de la BBDD  3. Construye y arranca
# 4. Espera a que la API responda; si no responde, vuelve al commit anterior.
#
# Todo va dentro de main() para que bash lo lea entero antes de ejecutar: el propio checkout cambia este fichero.
set -euo pipefail

main() {
  local sha="${1:-${SSH_ORIGINAL_COMMAND:-}}"
  if [[ ! "$sha" =~ ^[0-9a-f]{40}$ ]]; then
    echo "Uso: deploy.sh <sha de 40 caracteres>" >&2
    exit 2
  fi

  local repo="$HOME/pangestion/app"
  local deploy_dir="$repo/backend/deploy"
  local backups="$HOME/pangestion/backups"
  cd "$repo"

  git fetch --quiet origin main
  if ! git merge-base --is-ancestor "$sha" origin/main; then
    echo "El commit $sha no esta en main: no se despliega" >&2
    exit 3
  fi
  local previous
  previous="$(git rev-parse HEAD)"
  echo "== Desplegando $sha (antes: $previous)"
  git checkout --quiet --detach "$sha"

  cd "$deploy_dir"
  # Clave de cifrado de datos personales: se genera una vez y no se cambia nunca (sin ella no se leen esos datos)
  if ! grep -q '^ENCRYPTION_KEY=.\{40,\}' .env; then
    echo "ENCRYPTION_KEY=$(openssl rand -base64 32)" >> .env
    echo "== ENCRYPTION_KEY generada en $deploy_dir/.env (haz una copia fuera de la VM)"
  fi

  mkdir -p "$backups"
  local backup
  backup="$backups/$(date -u +%Y%m%d-%H%M%S)-${sha:0:7}.sql.gz"
  if docker compose ps --status running --services | grep -qx db; then
    docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d pangestion' | gzip > "$backup"
    chmod 600 "$backup"
    echo "== Copia de la BBDD: $backup"
    ls -1t "$backups"/*.sql.gz | tail -n +15 | xargs -r rm --
  fi

  docker compose up -d --build --remove-orphans
  if wait_healthy; then
    docker image prune -f > /dev/null
    [ -f "$deploy_dir/deploy.sh" ] && install -m 755 "$deploy_dir/deploy.sh" "$HOME/pangestion/deploy.sh"
    echo "== Desplegado $sha"
    return 0
  fi

  echo "== La API no responde: vuelvo a $previous" >&2
  docker compose logs --tail 80 api >&2 || true
  cd "$repo" && git checkout --quiet --detach "$previous" && cd "$deploy_dir"
  docker compose up -d --build --remove-orphans
  wait_healthy || true
  echo "== Despliegue fallido. Si la BBDD quedo mal, la copia de antes es $backup" >&2
  exit 1
}

# La API esta lista cuando responde por HTTPS a traves de Caddy (hasta 3 minutos: compilar y arrancar)
wait_healthy() {
  local domain
  domain="$(grep '^API_DOMAIN=' .env | cut -d= -f2)"
  for _ in $(seq 1 36); do
    if curl -fsS -o /dev/null --max-time 5 "https://$domain/api/public/companies"; then
      return 0
    fi
    sleep 5
  done
  return 1
}

main "$@"; exit $?
