#!/usr/bin/env bash
set -Eeuo pipefail

# Solo corre en el primer inicio de un volumen vacío de esta imagen QA.
psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --set ON_ERROR_STOP=1 <<'SQL'
SET mismascotas.entorno = 'qa-web';
\i /opt/mismascotas-qa/restablecer-seed.sql
SQL

# El control de salud exige esta marca para no aceptar una inicialización parcial.
printf '%s\n' 'qa-base-v1' > "$PGDATA/.mismascotas-qa-seed"
