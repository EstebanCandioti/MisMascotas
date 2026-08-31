# Mis Mascotas — Frontend React + TypeScript

MVP frontend mobile-first basado en los mockups de Figma del proyecto académico PP2.

## Requisitos

- Node.js 22 o superior
- npm
- Visual Studio Code

## Ejecutar en Visual Studio Code

1. Descomprimir la carpeta.
2. Abrir la carpeta `mis-mascotas-frontend` en Visual Studio Code.
3. Abrir una terminal integrada.
4. Ejecutar `npm install`.
5. Ejecutar `npm run dev`.
6. Abrir la dirección local indicada por la terminal.

El comando `npm run dev` es compatible con PowerShell, CMD, Windows, macOS y Linux. Para generar una compilación desde Windows sin Git Bash, usar `npm run build:windows`.

Si `npm install` informa vulnerabilidades de paquetes transitivos, no ejecutar `npm audit fix --force`: puede instalar versiones incompatibles. Para levantar la demostración local no es necesario.

## Cuenta demostrativa

- Correo: `usuario@demo.com`
- Contraseña: `Demo1234`

## Funcionalidades incluidas

- Login y registro demostrativos.
- Dashboard responsive.
- Mascotas propias y cuidado compartido.
- Perfil y edición de mascota.
- Registro de eventos clínicos.
- Historial y evolución.
- Creación de recordatorios.
- Marcado de cuidados completados.
- Calendario.
- Álbumes.
- Invitación y gestión de cuidadores.
- Configuración de notificaciones y seguridad.
- Exportación local de datos en JSON.

## Integración con Spring Boot

La versión entregada usa estado local para poder demostrarse sin servidor. Los formularios están preparados para reemplazar su callback de guardado por solicitudes HTTP al backend Spring Boot. Se recomienda crear una capa `services/` con un cliente HTTP y configurar la URL de la API mediante una variable de entorno.

No se incluyen `node_modules` ni archivos compilados. Se regeneran mediante `npm install` y `npm run build`.
