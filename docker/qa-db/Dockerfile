FROM postgres:17.6

LABEL org.opencontainers.image.title="MisMascotas PostgreSQL QA" \
      org.opencontainers.image.description="Esquema actual V9 y datos ficticios qa-base-v2" \
      org.opencontainers.image.version="qa-base-v2" \
      ar.mismascotas.esquema="9" \
      ar.mismascotas.seed="qa-base-v2"

# La misma base consolidada que utiliza Flyway para instalaciones nuevas.
COPY backend/src/main/resources/db/migration/B9__esquema_actual.sql /docker-entrypoint-initdb.d/01-base-actual.sql
COPY DB/qa/restablecer-seed.sql /opt/mismascotas-qa/restablecer-seed.sql
COPY docker/qa-db/02-seed-qa.sh /docker-entrypoint-initdb.d/02-seed-qa.sh
RUN chmod 0755 /docker-entrypoint-initdb.d/02-seed-qa.sh

# Acepta también un volumen anterior correctamente inicializado: Flyway lo actualizará.
HEALTHCHECK --interval=5s --timeout=5s --start-period=15s --retries=18 \
    CMD test -f "$PGDATA/.mismascotas-qa-seed" && pg_isready -h 127.0.0.1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"
