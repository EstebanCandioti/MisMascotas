# MisMascotas — paquete de pruebas Web

Versión: **qa-2026.10.25-01**. Datos iniciales: **qa-base-v1**.

## Preparación

1. Instalar y abrir Docker Desktop, con contenedores Linux. Mantenerlo abierto.
2. Extraer el ZIP completo en una carpeta. Conservar juntos `compose.yaml` y
   `configuracion-qa.env`; no hace falta editar esos archivos.
3. Abrir una terminal en esa carpeta. En Windows, se puede abrir la carpeta,
   escribir `powershell` en la barra de direcciones del Explorador y pulsar Enter.

Esta entrega contiene imágenes Linux para Intel/AMD de 64 bits. No requiere
Java, Node, Git, Postman ni PostgreSQL instalados. Mac/ARM no se verificó para
esta entrega.

## Descargar y encender

Ejecutar, en este orden:

```powershell
docker compose pull
docker compose up -d --wait
```

La primera descarga puede tardar varios minutos. Docker debe terminar sin errores
y los cuatro servicios deben figurar saludables antes de probar.

- Aplicación: http://localhost:3000
- Correos de verificación: http://localhost:8025
- API: http://localhost:8080

Los puertos 3000, 8080 y 8025 deben estar libres. No ejecutar a la vez otro
entorno de MisMascotas que use esos mismos puertos. Esta Web tiene la dirección
de la API incorporada en la imagen; cambiar el puerto de la API requiere otra
imagen Web preparada para ese cambio.

## Iniciar sesión

Consultar las cuentas ficticias en `cuentas-qa.md`.

1. Abrir la aplicación e ingresar el email y la contraseña de una cuenta QA.
2. Abrir la bandeja de correos, buscar ese destinatario y abrir el mensaje más
   reciente. El correo queda en Mailpit, no llega a Gmail ni a una dirección real.
3. Copiar el código de seis dígitos y completar la verificación en la aplicación.

También se pueden crear cuentas de prueba con direcciones ficticias terminadas
en `@mismascotas.test`.

## Datos y versiones

Al iniciar un volumen vacío se crean automáticamente seis usuarios, 15 mascotas,
20 eventos clínicos y 15 recordatorios. Las fechas son relativas al día de carga
en Argentina. Cada PC tiene su propia base y no modifica la de los otros testers.

Los datos se conservan al apagar y volver a encender. Volver a ejecutar `up` o
descargar imágenes no restaura el seed sobre una base ya inicializada.

La configuración contiene únicamente credenciales ficticias y una clave pública
de QA. Los servicios se publican solo en localhost. Es un entorno de pruebas
local; no usar cuentas ni información personales o de producción.

El backend y la Web son las mismas imágenes usadas en las tandas existentes.
Esta entrega conserva sus defectos conocidos. La versión del paquete y los
identificadores de imagen están en `version.json`; el commit de origen de esos
dos builds históricos no está verificado.

Al informar un problema, anotar la versión **qa-2026.10.25-01**, el caso probado,
los pasos, el resultado observado y una captura sin códigos ni contraseñas.

## Apagar y volver a usar

```powershell
docker compose stop
docker compose up -d --wait
```

Para retirar los contenedores conservando los datos:

```powershell
docker compose down
```

La restauración de datos es una operación diferente del encendido. Solicitar el
procedimiento de restauración si una tanda necesita volver al estado inicial.

## Si no abre

Comprobar el estado:

```powershell
docker compose ps
```

Conservar el mensaje de error. Para obtener el diagnóstico:

```powershell
docker compose logs --tail 100
```

Compartir el mensaje relevante con el responsable de QA. Evitar enviar correos
de Mailpit, códigos de verificación o archivos con sesiones.
