# Mis Mascotas — Frontend Web

Frontend mobile-first en React y TypeScript para el proyecto académico PP2.

## Requisitos

- Node.js 22 o superior
- npm
- Backend Spring Boot disponible

## Configuración

Copiar `.env.example` a `.env.local` cuando se necesite sobrescribir la URL local:

```env
NEXT_PUBLIC_API_URL=http://localhost:8080
```

`NEXT_PUBLIC_API_URL` es la única configuración de conexión con la API.

## Ejecución en Windows

```powershell
npm ci
npm run dev:windows
```

Para validar una compilación sin depender de Bash:

```powershell
npm run build:windows
```

## Estado de integración

Actualmente están conectados al backend:

- Registro e inicio de sesión.
- Verificación de código en dos pasos.
- Persistencia y restauración de sesión mediante `sessionStorage`.
- Renovación coordinada de access y refresh tokens.
- Cierre de sesión.
- Perfil del usuario autenticado mediante `GET /usuario/me`.

Los módulos de mascotas, eventos clínicos, recordatorios, calendario, álbumes y cuidado compartido muestran estados vacíos. No contienen datos simulados ni operaciones que aparenten persistencia. Cada módulo debe integrarse con su contrato real antes de habilitar altas, ediciones o acciones de dominio.

## Arquitectura

- `lib/`: infraestructura transversal de configuración, HTTP, errores y almacenamiento técnico de sesión.
- `features/`: lógica específica de cada dominio.
- `components/screens/`: pantallas y estados de presentación.

No se incluyen `node_modules` ni artefactos compilados en Git.
