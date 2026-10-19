# Entorno Docker de testing para Frontend Web

## Objetivo

Este stack levanta un entorno independiente para testing y presentación de MisMascotas:

- PostgreSQL 17.6;
- backend Java 21;
- frontend Web con Node 22 y Vinext.

No reemplaza ni comparte la base de datos del entorno definido en
`docker-compose.yml`.

## Requisitos

La PC necesita únicamente:

- Docker Desktop con Docker Compose;
- el repositorio o los archivos de distribución.

No requiere instalar Node, npm, Java, Maven ni PostgreSQL.

Los puertos `3000` y `8080` deben estar disponibles.

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
factores. Para recibirlo deben configurarse:

- `MAIL_HOST`;
- `MAIL_PORT`;
- `MAIL_USERNAME`;
- `MAIL_PASSWORD`;
- las opciones `MAIL_SMTP_*` correspondientes al proveedor.

Con Gmail normalmente se necesita una contraseña de aplicación. No debe
versionarse esa contraseña.

Si SMTP no está configurado o rechaza la conexión, el backend puede iniciar,
pero el código no llegará por correo y no será posible completar el flujo de
registro/login desde la Web. El stack no desactiva ni evita 2FA.

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

## Reset completo de la base

```bash
docker compose -f docker-compose.web-testing.yml down -v
docker compose -f docker-compose.web-testing.yml up -d
```

El reset elimina exclusivamente el volumen
`mismascotas_web_testing_db`. Al iniciarse nuevamente se ejecutan
`DB/01-init.sql` y `DB/02-seed.sql`.

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

La DB y el backend poseen healthchecks. El frontend espera que el backend esté
saludable antes de iniciar.

## Aislamiento

El stack utiliza:

- proyecto Compose: `mismascotas-web-testing`;
- volumen: `mismascotas_web_testing_db`;
- red: `mismascotas_web_testing_network`;
- servicios: `db-web-testing`, `backend-web-testing` y
  `frontend-web-testing`.

No comparte volumen ni red con el Docker de desarrollo.
