FROM postgres:17.6

LABEL org.opencontainers.image.title="MisMascotas PostgreSQL QA" \
      org.opencontainers.image.description="Base local de pruebas con estructura y seed ficticio qa-base-v1" \
      org.opencontainers.image.version="qa-2026.10.25-01"

COPY DB/01-init.sql /docker-entrypoint-initdb.d/01-init.sql
COPY DB/02-seed.sql /docker-entrypoint-initdb.d/02-seed.sql
COPY DB/qa/restablecer-seed.sql /opt/mismascotas-qa/restablecer-seed.sql
COPY docker/qa-db/03-seed-qa.sh /docker-entrypoint-initdb.d/03-seed-qa.sh
RUN chmod 0755 /docker-entrypoint-initdb.d/03-seed-qa.sh
