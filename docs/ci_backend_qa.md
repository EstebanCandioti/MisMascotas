# Pipeline del backend y actualización de QA

## Alcance actual

GitHub Actions verifica el backend con PostgreSQL temporal y publica las imágenes
de backend y DB con seed en Docker Hub. Flyway está incorporado al backend y a CI.
La imagen DB contiene directamente el esquema actual V9. No se modifica la entrega de QA existente,
no se ejecuta SQL en Supabase y no se publica desde la rama `feature-pagos`.

La migración automática está implementada y se verifica con bases nuevas y anteriores.
Falta integrarla en el Compose y en la publicación de la entrega QA.
Por eso esta etapa publica `sha-<commit-completo>`, sin mover una etiqueta `qa`
ni reemplazar los digests del paquete distribuido. Esta imagen no constituye
por sí sola una entrega nueva para testers.

## Archivos

- `.github/workflows/backend-qa.yml`: verificación y publicación.
- `ci/compose.yaml`: PostgreSQL 17.6 temporal, exclusivo de CI.
- `ci/verificar_configuracion.py`: verifica la plantilla pública y la exclusión de la configuración local.
- `backend/configuracion/application.properties`: plantilla pública para CI y Docker.
- `backend/src/test/resources/application-ci.properties`: configuración ficticia para las pruebas.
- `docker/qa-db/Dockerfile`: PostgreSQL con la base actual B9 y el seed qa-base-v2.
- `ci/verificar_imagen_qa.py`: validación del seed, arranque del backend, Premium, cuidado compartido, SMTP con Mailpit y persistencia.
- `ci/verificar_premium_qa.py`: nueve escenarios por HTTP del backend empaquetado; usa datos y JWT sintéticos exclusivos del entorno aislado.
- `ci/verificar_cuidado_qa.py`: gestión, cupo Free, visibilidad y revocación por HTTP; consulta de cualquier fecha, confirmación diaria para todos los perfiles e indicadores temporales. Verifica correos en Mailpit. Usa cuentas sintéticas y retira sus recordatorios y vínculos antes de limpiar las cuentas.

## Configuración en GitHub y Docker Hub

1. En Docker Hub, utilizar el repositorio `estebancandioti/mismascotas-backend`
   existente. Crear un **Personal Access Token** con permisos de lectura y
   escritura; no hace falta permiso de borrado. No usar la contraseña de la cuenta.
2. En GitHub, abrir `EstebanCandioti/MisMascotas` → **Settings → Secrets and
   variables → Actions**.
3. En **Variables → New repository variable**, crear
   `DOCKERHUB_USUARIO` con valor `estebancandioti`.
4. En **Secrets → New repository secret**, crear `DOCKERHUB_TOKEN` y pegar el
   token de Docker Hub. No guardarlo en el repositorio, Compose ni este documento.
5. Verificar en **Settings → Actions → General** que Actions esté habilitado y
   permita las acciones de GitHub y Docker utilizadas por el workflow.

No se necesitan credenciales de Supabase, Mercado Pago ni correo para la pipeline.
Las únicas credenciales externas son las de publicación en Docker Hub.

## Cuándo se ejecuta

| Disparador | Pruebas | Publicación |
| --- | --- | --- |
| PR dirigida a `backend`, con cambios relevantes | Sí | No |
| Push a `backend`, con cambios relevantes | Sí | Solo si pasan |
| Ejecución manual desde `backend` | Sí | Solo si pasan |
| Ejecución manual desde otra rama | Sí | No |

Los cambios relevantes son `backend/`, `DB/`, `ci/` y el propio workflow.
GitHub muestra **Run workflow** cuando el workflow está disponible en la rama
predeterminada; hasta entonces una PR dirigida a `backend` permite verificarlo.
La PR verifica los cambios sin fusionarlos ni publicar imágenes en Docker Hub.

## Verificación y aislamiento

El job `verificar` crea el proyecto Compose `mismascotas-ci`, sin usar la base
de desarrollo ni el proyecto `mismascotas-qa`. Usa el puerto local 35437 y comienza
vacío. Tras esperar disponibilidad TCP, las pruebas de Spring inicializan estructura
y catálogo mediante Flyway, antes de validar JPA. PostgreSQL persiste solo durante el job; el cierre elimina sus
volúmenes temporales.

`SPRING_CONFIG_LOCATION=classpath:/` excluye los archivos locales externos durante
las pruebas. `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC` fija la zona de las JVM de
pruebas; las reglas de negocio siguen usando sus zonas explícitas.
La configuración `ci` usa credenciales ficticias, desactiva los procesadores
periódicos y dirige MP/correo a localhost sin servicios externos. Las pruebas
del ciclo sustituyen Mercado Pago por un proveedor simulado. No se realizan
cobros ni se envían correos externos. `CuidadoCorreoPostgresIT` usa el listener real con transporte SMTP simulado.

Se ejecuta:

```bash
cd backend
bash mvnw -B -ntp -DreuseForks=false '-Dtest=*Tests,SuscripcionCicloPostgresIT,MigracionesPostgresIT,SuscripcionConsultaPostgresIT,SuscripcionCancelacionPostgresIT,SuscripcionPrimerCobroPostgresIT,MascotaPremiumPostgresIT,UsuarioPremiumPostgresIT,CuidadoCompartidoBasePostgresIT,CuidadoCompartidoPostgresIT,CuidadoPermisosPostgresIT,CuidadoCorreoPostgresIT' verify
```

Esto incluye las clases `*Tests` y las once integraciones PostgreSQL listadas:
ciclo, migraciones, consulta, cancelación, primer cobro, límite de mascotas y perfil
con login/refresh reales, base, gestión HTTP, matriz de permisos y correos después del commit de cuidado compartido. Se habilitan con `RUN_POSTGRES_CICLO_IT`,
`RUN_POSTGRES_MIGRACIONES_IT`, `RUN_POSTGRES_CONSULTA_IT`,
`RUN_POSTGRES_PRIMER_COBRO_IT`, `RUN_POSTGRES_PREMIUM_IT` y `RUN_POSTGRES_CUIDADO_IT`, todos en `true`.
Se verifican concurrencia e idempotencia de cobros y vencimientos, rollback de
auditoría y coherencia del Premium efectivo entre perfil, suscripción y cupo.
Las demás clases `*PostgresIT` no están incluidas en esta selección.

Después se construyen ambas imágenes sin publicarlas y se ejecuta
`ci/verificar_imagen_qa.py`. Verifica la estructura actual, los datos ficticios,
el registro de baseline V9 sin ejecutar V1–V9, el arranque del backend y que un
usuario agregado persista después de recrear el contenedor DB. También recorre
nueve casos Premium: Free, manual, mensual, anual, cancelada, pausada, vencida,
pago sin fecha y fecha futura sin pago aprobado. Compara los dos GET con el alta
de una sexta mascota y prueba aislamiento entre cuentas. Son fixtures SQL locales;
no se envían notificaciones ni cobros a MP. Las sesiones completas se verifican en
`UsuarioPremiumPostgresIT`; esta comprobación Docker firma JWT con la clave ficticia
de ese contenedor. Las fixtures se retiran antes de comprobar persistencia.
También inicia `axllent/mailpit:v1.31.5` en la misma red temporal. El backend entrega por SMTP a ese contenedor; se comprueban destinatarios, contenido y enlaces mediante su API. No se usa un proveedor externo. Los puertos efímeros de backend y Mailpit se publican exclusivamente en `127.0.0.1`; los recursos llevan etiquetas únicas y se eliminan al finalizar. Si falla, no se habilita la publicación.

Cada clase utiliza su propia JVM para evitar que el logging configurado por las
pruebas MVC contamine las comprobaciones de privacidad del cliente HTTP. No se
silencian fallos ni se omiten pruebas fallidas para publicar.

Los informes Surefire se conservan como artefacto durante 14 días, también ante
fallo. Si la verificación falla, `publicar` no se ejecuta. Los SHA de las acciones
están fijados para identificar las implementaciones usadas.

## Configuración local y credenciales

Las claves permanecen en `backend/src/main/resources/application.properties`,
en la misma ruta y con los mismos valores locales. Ese archivo deja de estar
versionado y se excluye del contexto Docker. Git mostrará su retirada del índice,
pero el archivo no se borra del disco. No se agrega un `.env` ni un script para
iniciar Spring Boot. El archivo externo `backend/config/application.properties`
preexistente no recibe copias nuevas de las claves de MP.

`backend/configuracion/application.properties` es la plantilla pública, con
variables y sin claves locales. La pipeline la copia a la ruta de recursos antes
de compilar. Docker hace esa misma copia durante su build, usando únicamente la
plantilla: el archivo local nunca se envía al contexto de construcción.

En un checkout nuevo para desarrollo, copiar la plantilla una sola vez a
`backend/src/main/resources/application.properties` y completar los valores
locales. Después se arranca el backend normalmente desde el IDE. No volver a
agregar ese archivo a Git con `git add -f`.

El perfil `ci` está únicamente en recursos de prueba y no viaja dentro del JAR.
El control de propiedades es acotado: no sustituye una revisión de secretos del
resto del repositorio ni del historial antes de subir los cambios acumulados.

## Publicación y trazabilidad

Tras verificar pruebas e imágenes, otro job publica para `linux/amd64`:

```text
estebancandioti/mismascotas-backend:sha-<commit-completo>
estebancandioti/mismascotas-db-qa:sha-<commit-completo>
```

El resumen del workflow informa ambas etiquetas y digests. Las imágenes incluyen la revisión
Git como etiqueta OCI. No se sobrescribe la entrega histórica `qa-2026.10.25-01`.
Que una imagen se publique no actualiza contenedores ejecutándose en las PC de QA.

## Flyway y siguientes pasos de distribución

La imagen propia de PostgreSQL seguirá sirviendo para instalaciones nuevas y
datos ficticios iniciales. Los volúmenes existentes se actualizarán mediante
migraciones versionadas; cambiar la imagen no reaplica los SQL de inicialización.

Ya se incorporaron la base actual B9, V1 a V9 y una adopción de QA condicionada a sus estructuras conocidas
y pruebas de conservación, rechazo de esquemas desconocidos, checksums y reanudación
tras fallos. El contrato completo está en [flyway_qa.md](flyway_qa.md).
Flyway permanece desactivado en desarrollo local.

Antes de promover una entrega nueva a QA falta:

1. Integrar y subir los cambios revisados para ejecutar la pipeline en GitHub.
2. Definir la promoción al canal `qa` y ajustar el Compose/README de la nueva
   entrega para que los comandos actuales descarguen y apliquen la actualización.
   Ese Compose deberá habilitar `FLYWAY_ENABLED=true` y, para la adopción inicial,
   `MIGRACIONES_ADOPTAR_BASE_QA=true`.
3. Verificar por separado el esquema de Supabase, que ya tiene cambios manuales,
   antes de habilitar Flyway allí. No usar la adopción de QA para ese esquema.

La alineación se refiere al esquema de la aplicación y sus catálogos, no a copiar
usuarios, mascotas o pagos entre Supabase y cada tester. Las migraciones no deben
reiniciar el seed sobre volúmenes existentes. No se usa `down -v` para actualizar QA.

## Referencias oficiales

- [Publicar imágenes con GitHub Actions](https://docs.github.com/en/actions/tutorials/publish-packages/publish-docker-images).
- [Inicialización de PostgreSQL en Docker](https://hub.docker.com/_/postgres#initialization-scripts).
- [Historial de migraciones de Flyway](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/flyway-schema-history-table).

## Validación local de esta preparación — 23 de noviembre de 2026

- Workflow validado con actionlint 1.7.7, sin errores.
- Compose de CI validado y PostgreSQL temporal inicializado correctamente.
- Selección de CI con Flyway: **481 pruebas, 0 fallos, 0 errores, 0 omitidas**;
  incluye las 19 de `SuscripcionCicloPostgresIT` y las diez de
  `MigracionesPostgresIT`, contra PostgreSQL real.
- Control de configuración: verificados cinco casos de aceptación/rechazo con
  valores ficticios, sin imprimir los valores detectados.
- Imagen Docker construida y arrancada contra esa base temporal: endpoint
  protegido devuelve HTTP 401, como espera el paquete QA.
- JAR de la imagen contrastado con la plantilla pública: no contiene las claves
  locales de MP ni el perfil de pruebas `application-ci.properties`.

Estos resultados corresponden a la verificación local anterior a la primera PR.
La imagen DB inicial se publicó separadamente como `qa-2026.11.23-base-v2`; los detalles
y su digest están en [imagen_db_qa.md](imagen_db_qa.md). Las ejecuciones posteriores
de GitHub se consultan en Actions y en los checks de la PR. La variable y el secreto
de publicación ya fueron configurados por el usuario.
