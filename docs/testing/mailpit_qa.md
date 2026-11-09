# Correo QA con Mailpit

## Objetivo y alcance

El entorno `mismascotas-web-testing` captura los correos de registro y login en Mailpit, manteniendo el desafío 2FA de la aplicación. Docker descarga la imagen oficial `axllent/mailpit:v1.31.5`; no se instala Mailpit por separado en Windows.

La configuración se incorpora únicamente a `docker-compose.web-testing.yml`. El Compose de desarrollo y las credenciales del archivo local `.env.web-testing` se conservan. Los valores SMTP explícitos del servicio backend tienen prioridad y no utilizan las credenciales reales que pudiera contener ese archivo.

## Servicios y accesos

| Uso | Dirección |
|---|---|
| Bandeja local | `http://localhost:8025` |
| API para las pruebas | `http://localhost:8025/api/v1/` |
| SMTP utilizado por el backend dentro de Docker | `mailpit-web-testing:1025` |

El SMTP no se publica al host. La bandeja y la API se publican únicamente en `127.0.0.1`; no se configura reenvío a proveedores externos. El entorno recibe también emails ficticios del dominio `mismascotas.test`.

El backend QA usa `MAIL_SMTP_AUTH=false`, `MAIL_SMTP_STARTTLS_ENABLE=false` y `MAIL_SMTP_STARTTLS_REQUIRED=false`, con usuario y contraseña vacíos. Esta configuración corresponde al servicio de captura dentro de la red QA.

## Arranque de una instalación existente

Desde `D:\AAAIES\MisMascotas-front`:

```powershell
docker compose --env-file .env.web-testing -f docker-compose.web-testing.yml up -d --no-build --wait mailpit-web-testing backend-web-testing frontend-web-testing
```

La base se inicia como dependencia si estaba detenida y su volumen se conserva. Este comando necesita que las imágenes de backend y frontend ya existan; para la primera construcción completa usar el procedimiento con `--build` de `docker-web.md`.

El backend espera a que Mailpit esté saludable. Si se cambia el puerto web mediante `WEB_TESTING_MAILPIT_PORT`, la URL de acceso debe usar ese puerto; el backend sigue enviando al puerto SMTP interno `1025`.

## Uso manual

1. Registrar una cuenta o iniciar sesión desde la web.
2. Abrir la bandeja Mailpit y buscar el email utilizado en ese intento.
3. Abrir el último mensaje del destinatario y copiar el código.
4. Completar el desafío en la aplicación.

Los códigos llegan a Mailpit, incluso cuando el usuario escribe una dirección real. La entrega a un proveedor externo se prueba por separado, cuando sea necesaria; Mailpit comprueba el circuito de la aplicación y la recepción local.

Las seis cuentas del catálogo `cuentas_qa.md` podrán utilizar este mecanismo cuando se implemente su carga al seed. La incorporación de Mailpit no crea esas cuentas.

## Uso automatizado

La API permite buscar por destinatario, recuperar el contenido de un mensaje y extraer el código. Cada caso debe usar un destinatario único o correlacionar el intento con mensajes nuevos de ese destinatario. No usar el último mensaje global de la bandeja, porque puede corresponder a otra prueba.

Se usan esperas acotadas con consulta de la API, sin pausas fijas largas. Las pruebas deben informar claramente si falló la petición de autenticación, el envío del mensaje, su recepción o la verificación del código. No se imprimen tokens, contraseñas reales ni códigos en los reportes compartidos.

## Bandeja y restauración

La base de mensajes está en `/tmp/mailpit.db`, dentro del contenedor, con un máximo de 500 mensajes. No usa un volumen persistente del host. Detener y arrancar el mismo contenedor conserva la bandeja; recrear o eliminar ese contenedor la vacía. La API también permite limpiar mensajes específicos al finalizar las pruebas.

La restauración de la base PostgreSQL y la limpieza de la bandeja son operaciones diferentes. No se elimina el volumen de PostgreSQL para limpiar correos.

## Verificación inicial

Antes de dar por integrada la herramienta se comprueba:

- Los servicios necesarios están saludables y la bandeja responde por HTTP.
- La configuración efectiva del backend apunta a Mailpit, sin autenticación SMTP ni credenciales de Gmail.
- Un registro temporal genera un mensaje local para el destinatario correcto.
- El código extraído permite verificar 2FA y consultar `/usuario/me`.
- La sesión temporal se cierra y los datos del usuario de diagnóstico se retiran de la base QA, sin borrar cuentas existentes.

El resultado de estas comprobaciones se registra al realizarlas; documentarlas no constituye evidencia de que hayan pasado.

### Resultado del 22 de octubre de 2026

La verificación inicial se completó en el entorno local:

- Bandeja Mailpit y frontend: HTTP 200. Backend protegido sin token: HTTP 401 esperado.
- Configuración efectiva del backend: SMTP `mailpit-web-testing:1025`, autenticación y STARTTLS desactivados, usuario y contraseña vacíos.
- Registro de una cuenta ficticia temporal: correo recibido para el destinatario correcto, código verificado, perfil autenticado consultado y sesión cerrada.
- Login posterior de la misma cuenta: un correo nuevo distinto del anterior, código verificado, perfil consultado y sesión cerrada.
- La corrida satisfactoria capturó dos correos. La cuenta temporal y sus códigos, sesiones y entradas de auditoría se retiraron; las cuentas previas se conservaron.

La bandeja conserva mensajes de diagnóstico como evidencia; las cuentas temporales asociadas ya no existen. Estas comprobaciones verifican la integración local de correo y autenticación, no la matriz completa ni la entrega mediante proveedores externos. Las seis cuentas planificadas del seed siguen pendientes de carga.

## Referencias

- [Imagen Docker oficial y versiones fijadas](https://mailpit.axllent.org/docs/install/docker/).
- [API de Mailpit](https://mailpit.axllent.org/docs/api-v1/).
- [Versión utilizada: v1.31.5](https://github.com/axllent/mailpit/releases/tag/v1.31.5).
