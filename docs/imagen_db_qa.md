# Imagen inicial PostgreSQL QA

## Contenido y uso

La receta `docker/qa-db/Dockerfile` deriva de PostgreSQL 17.6 y contiene:

1. La estructura actual V9 y el catálogo de 15 estados, a partir del mismo archivo
   `B9__esquema_actual.sql` que usa Flyway para entornos nuevos.
2. El seed ficticio `qa-base-v2`, con las mismas seis cuentas y datos de la entrega
   QA anterior: 15 mascotas, 20 eventos clínicos y 15 recordatorios.
3. Un control de salud que espera la inicialización completa y disponibilidad TCP.

No contiene un volumen físico, un backup de Supabase, usuarios reales ni claves
de MP. Cada tester obtiene su propio volumen al iniciar la imagen. La inicialización
se ejecuta una sola vez; actualizar la imagen no restaura ni borra ese volumen.

## Relación con el backend

El contenedor backend debe recibir:

```yaml
environment:
  FLYWAY_ENABLED: "true"
  MIGRACIONES_ADOPTAR_BASE_QA: "true"
```

En el primer arranque reconoce la estructura actual y registra un baseline V9.
No ejecuta V1–V9 ni vuelve a cargar el seed. Las actualizaciones futuras se entregan
como V10, V11, etc. El desarrollo local mantiene Flyway desactivado.

Un volumen QA histórico conserva sus datos. La estrategia reconoce su estructura
V1 y aplica las actualizaciones necesarias; no ejecuta el inicializador de la nueva
imagen. Un volumen de estructura desconocida no se reinicia ni adopta silenciosamente.

## Construcción y comprobación

Desde la raíz del repositorio:

```powershell
docker build -f docker/qa-db/Dockerfile -t mismascotas-db-qa:verificacion .
docker build -f backend/Dockerfile -t mismascotas-backend:verificacion backend
python ci/verificar_imagen_qa.py mismascotas-db-qa:verificacion mismascotas-backend:verificacion
```

Son comandos de mantenimiento/CI, no nuevos pasos para el tester. El contexto de
construcción de la DB utiliza `Dockerfile.dockerignore` y excluye explícitamente
la configuración local. La comprobación crea recursos temporales etiquetados,
sin puertos públicos, y elimina únicamente los que creó.

La verificación exige los recuentos iniciales, el catálogo, la ausencia de columnas
legacy y la huella del esquema V9. Arranca el backend y comprueba que el historial
registre V9 sin ejecutar las migraciones históricas. Agrega un usuario ficticio,
recrea el contenedor conservando el volumen y verifica que el usuario, el catálogo
y el historial permanezcan: el seed no se repite.

## Publicación automatizada

La pipeline `backend-qa.yml` realiza las pruebas del backend, construye ambas
imágenes y ejecuta la comprobación anterior antes de habilitar la publicación.
En una PR solo verifica; en un push a `backend` publica:

```text
estebancandioti/mismascotas-backend:sha-<commit>
estebancandioti/mismascotas-db-qa:sha-<commit>
```

Utiliza la variable `DOCKERHUB_USUARIO` y el secreto `DOCKERHUB_TOKEN` ya previstos.
Las imágenes deben descargarse juntas según la versión de entrega. Publicarlas
no actualiza automáticamente un contenedor ya ejecutándose ni modifica el Compose
histórico con digests fijados.

El paquete de `MisMascotas-front` sigue siendo la referencia de distribución.
Antes de entregar esta versión hay que actualizar sus referencias de imágenes,
habilitar Flyway en el backend y conservar el volumen y los comandos del README.
No se debe usar `down -v` como procedimiento de actualización.

## Evolución

B9 y `qa-base-v2` son el punto de partida inicial. Los cambios posteriores de la
aplicación se escriben en migraciones versionadas; no requieren regenerar B9 ni
restablecer los datos de los testers. Si en el futuro se publica otro punto de
partida, deberá identificarse con otra versión y comprobar ambas rutas de actualización.

El contrato de Flyway se detalla en [flyway_qa.md](flyway_qa.md).

## Punto de partida publicado — 23 de noviembre de 2026

```text
estebancandioti/mismascotas-db-qa:qa-2026.11.23-base-v2
sha256:3b5474e2ea694bc39edd2ba22963c017644acaf135a40e4eaeb7e2d74feeb9ba
```

La etiqueta es nueva y no reemplaza `qa-2026.10.25-01`. El digest identifica el
índice OCI publicado para `linux/amd64`, junto con sus metadatos de construcción.
La construcción inicial se hizo antes de incorporar los cambios fuente a Git.
Su procedencia se identifica por el digest publicado y los archivos de esta entrega;
no se atribuye a una revisión Git anterior que no contenía esos cambios.

Validaciones realizadas:

- Diez casos de migraciones en PostgreSQL real: correctos. Incluyen la base nueva,
  el volumen histórico, la adopción V9 y una V10 futura simulada conservando datos.
- Selección completa de CI: **481 pruebas, 0 fallos, 0 errores, 0 omitidas**.
- Imagen DB: seis usuarios, 15 mascotas, 20 eventos clínicos, 15 recordatorios y
  15 estados. Las columnas textuales legacy ya no existen.
- Arranque real del backend: registra baseline V9 y responde HTTP 401 en el endpoint
  protegido. No ejecuta V1–V9 sobre la imagen inicializada.
- Persistencia: usuario agregado, catálogo e historial conservados al recrear DB
  sobre el mismo volumen; no se repite el seed.
- Workflow validado con actionlint 1.7.7.
- Manifest remoto y digest confirmados mediante acceso anónimo: QA puede descargar
  esta imagen pública sin una cuenta ni credenciales de Docker Hub.

Supabase y el volumen QA existente permanecen sin cambios. El paquete histórico
todavía no apunta a esta imagen y el backend nuevo aún no fue publicado.
