# Entorno Docker de testing para Frontend Web

## Objetivo

Este stack levanta un entorno independiente para testing y presentación de MisMascotas:

- PostgreSQL 17.6;
- backend Java 21;
- frontend Web con Node 22 y Vinext;
- Mailpit v1.31.5 para capturar correos QA.

No reemplaza ni comparte la base de datos del entorno definido en
`docker-compose.yml`.

## Requisitos

La PC necesita únicamente:

- Docker Desktop con Docker Compose;
- el repositorio o los archivos de distribución.

No requiere instalar Node, npm, Java, Maven ni PostgreSQL.

Los puertos `3000`, `8080` y `8025` deben estar disponibles. Mailpit publica
su bandeja y API únicamente en la interfaz local de la PC.

Si el stack de desarrollo ya está usando esos puertos, pueden definirse
temporalmente `WEB_TESTING_FRONTEND_PORT` y
`WEB_TESTING_BACKEND_PORT`. El puerto del backend también se incorpora al
build de Web para que el navegador use el destino correcto.

## Configuración inicial

Desde la raíz del repositorio, crear el archivo local de configuración:

```powershell
Copy-Item .env.web-testing.example .env.web-testing
```

En Linux o macOS:

```bash
cp .env.web-testing.example .env.web-testing
```

`.env.web-testing` está ignorado por Git y no debe versionarse.

Antes de iniciar, editarlo y reemplazar:

- `POSTGRES_PASSWORD`;
- `DB_PASSWORD`, con el mismo valor que `POSTGRES_PASSWORD`;
- `JWT_SECRET`, usando un valor aleatorio de al menos 32 caracteres.

No modificar el host de `DB_URL`: dentro de la red Docker el backend debe
conectarse a `db-web-testing`, no a `localhost`.

## SMTP y 2FA

El registro y el inicio de sesión generan un código de verificación de dos
factores. El Compose dirige el SMTP del backend a `mailpit-web-testing:1025`,
sin autenticación ni STARTTLS, dentro de la red QA. Estos valores tienen
prioridad sobre los `MAIL_*` del archivo local para mantener el correo de
testing en Mailpit.

Abrir <http://localhost:8025>, buscar el destinatario con el que se registró
o inició sesión, abrir el mensaje y copiar el último código. También se
pueden utilizar direcciones ficticias como las documentadas en `cuentas_qa.md`.
Los mensajes se capturan localmente; no llegan a Gmail ni a otro buzón externo.

Docker descarga la imagen de Mailpit al iniciar por primera vez. No requiere
una instalación adicional en Windows ni credenciales de un proveedor de correo.

El backend espera a que Mailpit esté saludable. El stack mantiene el flujo
real de 2FA. El detalle de la configuración, la API y las comprobaciones está
en `mailpit_qa.md`.

Para incorporar Mailpit en una instalación ya construida:

```powershell
docker compose --env-file .env.web-testing -f docker-compose.web-testing.yml up -d --no-build --wait mailpit-web-testing backend-web-testing frontend-web-testing
```

La actualización recrea el backend cuando cambia su configuración; conserva
la base QA. No usar `down -v` para agregar Mailpit.

## Construcción y arranque

```bash
docker compose -f docker-compose.web-testing.yml up -d --build
```

Para ejecutarlo en paralelo al entorno de desarrollo desde PowerShell:

```powershell
$env:WEB_TESTING_FRONTEND_PORT = "13000"
$env:WEB_TESTING_BACKEND_PORT = "18080"
docker compose -f docker-compose.web-testing.yml up -d --build
```

En ese caso los accesos pasan a ser
`http://localhost:13000` y `http://localhost:18080`.

El build de Web ejecuta `npm ci` dentro de una imagen Linux Node 22. Las
dependencias y `node_modules` instalados en Windows nunca se copian a la
imagen.

`NEXT_PUBLIC_API_URL` se define durante el build como
`http://localhost:8080`. Esta URL es intencional: el código se ejecuta en el
navegador del usuario, no dentro de la red Docker.

## Acceso

- Web: <http://localhost:3000>
- Backend: <http://localhost:8080>
- Correos y API de Mailpit: <http://localhost:8025>

El puerto de la bandeja puede cambiarse con `WEB_TESTING_MAILPIT_PORT`.
Si se define en `.env.web-testing`, pasar también `--env-file .env.web-testing`
al comando Compose para que se aplique a la publicación del puerto.

PostgreSQL no se publica al host. El backend accede mediante la red exclusiva
`mismascotas_web_testing_network`.

## Estado y logs

Consultar el estado:

```bash
docker compose -f docker-compose.web-testing.yml ps
```

Seguir todos los logs:

```bash
docker compose -f docker-compose.web-testing.yml logs -f
```

Seguir un servicio:

```bash
docker compose -f docker-compose.web-testing.yml logs -f backend-web-testing
```

## Apagado

```bash
docker compose -f docker-compose.web-testing.yml down
```

Este comando conserva la base de datos de testing.
Los correos no usan un volumen propio: reiniciar el mismo contenedor Mailpit
conserva su bandeja, pero eliminarlo o recrearlo la vacía.

## Carga y restauración habitual del seed QA

Una vez iniciado el stack, desde la raíz del repositorio y en PowerShell:

```powershell
.\scripts\qa\Restablecer-QA.ps1
```

Sin argumentos solo valida el destino; no modifica datos. Para reemplazar los
datos QA por las seis cuentas y sus mascotas, eventos y recordatorios:

```powershell
.\scripts\qa\Restablecer-QA.ps1 -ConfirmarRestablecimiento
```

El procedimiento valida proyecto, contenedores, base y volumen; detiene el
backend QA, respalda la base anterior, vacía las tablas de negocio y carga el
seed en una transacción. Solo después de confirmar limpia Mailpit y reinicia
el backend si estaba encendido. No borra el volumen ni modifica el esquema.

Los respaldos y evidencias quedan en `.qa/restauraciones/`, fuera de Git.
Las credenciales están en `cuentas_qa.md`; el detalle, en `seed_qa.md`.
La carga es explícita: iniciar Docker por sí solo no inserta los datos QA.

## Reset completo de la estructura (excepcional)

Este procedimiento elimina el volumen y sus datos. Reservarlo para cambios
estructurales o una instalación desde cero, no para repetir pruebas habituales.

```bash
docker compose -f docker-compose.web-testing.yml down -v
docker compose -f docker-compose.web-testing.yml up -d
```

El reset elimina exclusivamente el volumen
`mismascotas_web_testing_db`. Al iniciarse nuevamente se ejecutan
`DB/01-init.sql` y `DB/02-seed.sql`. Después se debe ejecutar la restauración
QA anterior: el seed de negocio no se incorpora automáticamente al arranque.

## Reconstrucción después de cambios

```bash
docker compose -f docker-compose.web-testing.yml up -d --build
```

Para forzar una reconstrucción completa sin caché:

```bash
docker compose -f docker-compose.web-testing.yml build --no-cache
docker compose -f docker-compose.web-testing.yml up -d
```

## Validación de la configuración

```bash
docker compose -f docker-compose.web-testing.yml config
docker compose -f docker-compose.web-testing.yml build
docker compose -f docker-compose.web-testing.yml up -d
docker compose -f docker-compose.web-testing.yml ps
```

La DB, Mailpit, el backend y el frontend poseen healthchecks. El backend espera
a la DB y a Mailpit; el frontend espera al backend.

## Aislamiento

El stack utiliza:

- proyecto Compose: `mismascotas-web-testing`;
- volumen: `mismascotas_web_testing_db`;
- red: `mismascotas_web_testing_network`;
- servicios: `db-web-testing`, `backend-web-testing` y
  `frontend-web-testing`, más `mailpit-web-testing`;
- SMTP de Mailpit: solo interno en la red QA, sin publicar el puerto `1025` al host;
- bandeja Mailpit: publicada en `127.0.0.1:8025` por defecto.

No comparte volumen ni red con el Docker de desarrollo.
