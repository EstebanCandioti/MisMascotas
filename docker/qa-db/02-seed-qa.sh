#!/usr/bin/env bash
set -Eeuo pipefail

# El entrypoint oficial ejecuta este archivo solo sobre un volumen vacío.
# La estructura V9 y los 15 estados ya están creados por 01-base-actual.sql.
psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --set ON_ERROR_STOP=1 <<'SQL'
SET mismascotas.entorno = 'qa-web';
\i /opt/mismascotas-qa/restablecer-seed.sql
SQL

# Solo declarar lista la base después de confirmar la carga completa del seed.
printf '%s\n' 'qa-base-v2' > "$PGDATA/.mismascotas-qa-seed"
