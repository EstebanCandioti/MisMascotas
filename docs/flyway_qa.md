# Flyway y el punto de partida de QA

## Modelo adoptado

La imagen PostgreSQL QA contiene **la estructura actual V9, el catálogo y los datos
ficticios de prueba**. El primer inicio sobre un volumen vacío los carga directamente.
El backend no reconstruye primero una base antigua ni recarga usuarios con Flyway.

Docker Hub distribuye la imagen, no el volumen. Cada PC crea su volumen a partir
del inicializador de esa imagen y después conserva sus propios datos. Cambiar la
imagen no vuelve a ejecutar los scripts de inicialización sobre un volumen existente.

Flyway registra el punto de partida conocido y gestiona las actualizaciones posteriores.
La configuración local sigue con `spring.flyway.enabled=false` y sus claves en el
mismo `backend/src/main/resources/application.properties`. No se agrega un `.env`
ni un script de arranque local y no se aplicaron cambios en Supabase.

## Una fuente para la estructura inicial

`backend/src/main/resources/db/migration/B9__esquema_actual.sql` contiene el esquema
consolidado y 15 estados, sin usuarios ni historial de Flyway. Se generó aplicando
V1–V9 en PostgreSQL temporal y exportando exclusivamente su estructura y catálogo.
No se exportó Supabase ni un volumen de pruebas usado por una persona.

Ese mismo archivo sirve para:

- Inicializar la estructura de la imagen DB QA.
- Crear una base vacía directamente con Flyway, como sucede en CI.

Es una **baseline migration** estándar de Flyway. Un entorno vacío utiliza B9 como
punto de partida; los entornos con historial continúan usando las migraciones
versionadas pendientes. B9 no reemplaza el comando `baseline()` ni equivale a él.

Los archivos V1–V9 permanecen para actualizar volúmenes históricos. Un volumen de
la entrega anterior no puede recibir los `CREATE TABLE` de B9 sobre tablas existentes.

## Cuándo actúa cada componente

```mermaid
sequenceDiagram
    participant PG as Imagen PostgreSQL QA
    participant DB as Volumen PostgreSQL
    participant App as Spring Boot / Flyway
    participant JPA as Hibernate
    alt Volumen vacío
        PG->>DB: Cargar estructura B9 y catálogo
        PG->>DB: Cargar seed ficticio qa-base-v2
        PG->>DB: Crear marca de inicialización completa
    else Volumen existente
        PG->>DB: Abrir sus datos; no ejecutar el seed
    end
    App->>DB: Consultar historial y estructura si debe adoptar la base
    opt Base actual conocida sin historial y adopción habilitada
        App->>DB: Registrar baseline V9 sin ejecutar V1-V9
    end
    App->>DB: Validar/aplicar actualizaciones posteriores pendientes
    JPA->>DB: Validar mapeos con ddl-auto=validate
    App->>App: Completar arranque y atender peticiones
```

Flyway se inicializa durante el arranque de Spring Boot, antes del
`EntityManagerFactory` de JPA. No espera una primera petición HTTP.

## Casos de arranque

| Estado de la base | Resultado |
| --- | --- |
| Nueva imagen QA y volumen vacío | PostgreSQL crea directamente V9 y el seed. El backend reconoce la estructura y registra un baseline V9. No ejecuta V1–V9. |
| Base vacía sin inicializador, por ejemplo CI | Flyway ejecuta B9 una vez, sin las cuentas ficticias. Después aplica V10, V11, etc., si existen. |
| Base actual V9 con historial | Flyway valida las migraciones versionadas y aplica únicamente las pendientes posteriores. |
| Volumen QA histórico sin historial y con su estructura original | La adopción verificada registra V1 y ejecuta V2–V9. Conserva sus datos y no carga el seed nuevo. |
| Volumen histórico que ya fue migrado | Continúa desde su historial; no vuelve a adoptar ni recrear tablas. |
| Esquema desconocido sin historial | Se detiene el arranque para revisarlo. No lo marca como actualizado ni lo reinicia. |

## Activación y configuración

| Entorno | Configuración |
| --- | --- |
| Desarrollo local actual | `spring.flyway.enabled=false`. Flyway no conecta ni ejecuta migraciones; Hibernate mantiene su validación habitual. |
| CI | Perfil `ci`, Flyway habilitado y PostgreSQL temporal vacío. |
| Contenedor QA | `FLYWAY_ENABLED=true`. |
| Adopción inicial de una imagen QA conocida | Además `MIGRACIONES_ADOPTAR_BASE_QA=true`. |

Las variables anteriores son configuración del contenedor; no son un requisito
de arranque para desarrollo local. La plantilla pública configura también:

```properties
spring.flyway.baseline-on-migrate=false
spring.flyway.baseline-version=1
spring.flyway.default-schema=public
spring.flyway.clean-disabled=true
```

La estrategia selecciona internamente V1 o V9 según la estructura reconocida;
no hace falta cambiar manualmente `baseline-version` a 9. Rechaza la adopción
indiscriminada mediante `baseline-on-migrate=true`.

## Adopción verificada

`Configuracion/MigracionesQaConfig.java` implementa una `FlywayMigrationStrategy`.
Antes de invocar `migrate()`:

1. Consulta si existe `public.flyway_schema_history`.
2. Si la base tiene objetos y no tiene historial, exige la habilitación explícita
   de adopción QA para aceptar un punto de partida existente.
3. Compara una huella estructural contra los dos esquemas conocidos:
   `huella_base_v1.txt` y `huella_base_v9.txt`.
4. Solo si coincide, registra `baseline()` en V1 o V9 según corresponda.
5. Delega la validación y ejecución de pendientes en Flyway.

La huella considera tablas, columnas/tipos/defaults, nulabilidad, restricciones,
índices, secuencias, vistas, políticas RLS y triggers de usuario. No depende de los
IDs internos, propietarios ni del valor actual de las secuencias. No certifica
los datos, permisos de roles ni todos los objetos posibles de PostgreSQL. Se usa
para reconocer estas bases QA, no como autenticación ni como comparador genérico.

El Supabase actual sigue fuera de esta adopción automática: tiene cambios aplicados
manualmente y debe verificarse por separado antes de habilitar Flyway allí.

## Datos iniciales y persistencia

El seed `qa-base-v2` reutiliza las cuentas y distribución ficticia de la entrega anterior:

- Seis usuarios y sus mismas credenciales de QA.
- 15 mascotas, 20 eventos clínicos y 15 recordatorios.
- 15 estados del catálogo, incluido SUSCRIPCION/PENDIENTE.
- Tablas de pagos, suscripciones, notificaciones y auditoría inicialmente vacías.

No incluye tokens de MP, contraseñas locales, copias de correos ni usuarios reales.
El control de salud exige la marca de inicialización y disponibilidad TCP. Acepta
la marca de un volumen histórico para permitir que el backend lo actualice.

El entrypoint oficial de PostgreSQL ejecuta el inicializador solo con el directorio
de datos vacío. Recrear o actualizar un contenedor conserva el volumen; no ejecuta
de nuevo el seed. Una carga inicial fallida no se declara saludable ni se resuelve
mediante un borrado automático del volumen.

## Versiones y actualizaciones futuras

B9 representa el resultado de V1–V9:

| Versión histórica | Cambio |
| --- | --- |
| V1 | Estructura de la entrega QA anterior. |
| V2 | Catálogo inicial, sin duplicar estados existentes. |
| V3 | SUSCRIPCION/PENDIENTE, retiro de `suscripcion.estado` e índice de reservas. |
| V4 | Auditoría pendiente y su relación idempotente con el log. |
| V5 | Unicidad de IDs remotos de suscripción y pago. |
| V6 | Notificaciones MP, índice y RLS. |
| V7 | Estado y programación de reintentos. |
| V8 | Datos del ciclo de suscripción y unicidad de factura. |
| V9 | Retiro de `pago.estado`, conservando `estado_id`. |
| V10 | Base de cuidado compartido: RECHAZADA, emails normalizados, plazos obligatorios, índices y acceso activo único por par de cuentas. |
| V11 | Vínculo único de una invitación aceptada con su acceso concreto; repetir una aceptación no reactiva accesos revocados. |

Las bases nuevas ya nacen sin las columnas textuales de estado. Para un cambio
posterior se agrega V12, luego V13, etc. No hay que modificar
B9 ni volver a cargar el seed. B9 permanece como punto de partida estable de la imagen.

Las migraciones publicadas son inmutables. Flyway valida los checksums de las
migraciones versionadas aplicadas; un cambio posterior requiere otra versión.
Las baseline migrations se seleccionan para entornos nuevos y se ignoran en los
ya gestionados: B9 no es un control continuo de deriva de una base existente.
CI comprueba que la imagen coincida con su estructura congelada y que las rutas
nueva/histórica lleguen al mismo resultado.

Flyway no genera SQL a partir de JPA ni replica modificaciones manuales hechas en
Supabase. El alineamiento depende de entregar los cambios futuros como migraciones.

## Fallos y transacciones

| Situación | Comportamiento |
| --- | --- |
| Base desconocida o adopción no habilitada | El backend no completa el arranque. |
| Catálogo ambiguo o IDs remotos duplicados durante actualización | La migración falla sin corregir ni eliminar registros automáticamente. |
| Error SQL o restricción | Se revierte esa migración y se detiene el arranque. |
| Checksum de una migración versionada distinto | Falla la validación; no se ejecuta `repair` automáticamente. |
| Reinicio después de corregir el problema | Continúa con las versiones pendientes. |

En PostgreSQL cada migración de Flyway tiene su transacción. Las versiones anteriores
confirmadas permanecen si una posterior falla. No hay downgrade automático ni una
transacción única para toda la actualización. `clean` está deshabilitado.
Cambiar de versión mayor de PostgreSQL es otra operación; Flyway no la resuelve.

## Archivos principales

- `backend/src/main/resources/db/migration/B9__esquema_actual.sql`: esquema inicial consolidado.
- `backend/src/main/resources/db/migration/V*.sql`: actualizaciones históricas y futuras.
- `backend/src/main/resources/db/qa/`: consulta y huellas de los esquemas conocidos.
- `backend/src/main/java/com/MisMascotas/backend/Configuracion/MigracionesQaConfig.java`: adopción y arranque.
- `docker/qa-db/Dockerfile`: imagen PostgreSQL con la base actual.
- `DB/qa/restablecer-seed.sql`: seed ficticio actualizado; ejecución protegida para QA.
- `ci/verificar_imagen_qa.py`: verifica seed, arranque, baseline V9 y persistencia en recursos temporales.
- `backend/src/test/java/com/MisMascotas/backend/Configuracion/MigracionesPostgresIT.java`: migraciones desde bases vacías, V1 y V9, conservación y rechazo de datos incompatibles, e instalación de una V12 simulada exclusiva de pruebas.

El archivo de prueba `db/futuro/V12__prueba_actualizacion_qa.sql` no se incluye en
el JAR ni se ejecuta en QA real.

### Actualización V10 de cuidado compartido

B9 y sus huellas se conservan como puntos de partida inmutables. Tras aplicar V10,
el catálogo tiene 16 estados, incluido `INVITACION/RECHAZADA`. La imagen DB sigue
inicializando B9 y el seed qa-base-v2; el backend aplica V10 mediante Flyway al
arrancar. No se necesita recargar el seed para actualizar una base existente.

V10 conserva IDs, perfiles y accesos anteriores. Normaliza emails y completa solo
expiraciones faltantes con creación más 48 horas. Accesos activos duplicados,
autoaccesos, emails vacíos o plazos inválidos detienen la migración sin eliminar
datos. Tras corregir la causa, se puede retomar la migración. El seed de restauración
admite de forma explícita los catálogos B9 y B9+V10 y conserva RECHAZADA.

V11 agrega `invitacion.acceso_id`, con clave foránea y unicidad, sin inferir
asociaciones históricas. Una invitación aceptada sin vínculo verificable se
conserva y requiere revisión al intentar repetir su aceptación. Las fixtures
futuras pasan a V12 para no colisionar con la nueva migración real.

## Referencias

- [Baseline migrations de Flyway](https://documentation.red-gate.com/fd/baseline-migrations-273973336.html).
- [Inicialización de PostgreSQL en Docker](https://hub.docker.com/_/postgres#initialization-scripts).
- [Integración de Flyway con Spring Boot](https://docs.spring.io/spring-boot/how-to/data-initialization.html).
