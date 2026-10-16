# MisMascotas Frontend Native

Este proyecto usa Expo Go para correr la app móvil.

## Requisitos

- Node.js
- npm
- Expo Go instalado en tu celular
- una URL del backend accesible desde el dispositivo o emulador

## 1) Instalar dependencias

Desde la carpeta `frontend-native`:

```bash
npm install
```

La autenticación usa `expo-secure-store` para guardar el token de sesión de forma segura. Si la dependencia todavía no está instalada, ejecutá:

```bash
npx expo install expo-secure-store
```

Después de instalarla, reiniciá Expo limpiando la caché:

```bash
npx expo start -c
```

## 2) Configurar la URL del backend

Crear un archivo `.env` dentro de `frontend-native/` con esta línea:

```env
EXPO_PUBLIC_API_URL=http://IP_DE_WINDOWS:8080
```

Ejemplo:

```env
EXPO_PUBLIC_API_URL=http://192.168.1.50:8080
```

> Importante: `localhost` dentro de Expo apunta al dispositivo o emulador, no necesariamente al host Windows. Usá una IP o URL que el entorno donde corre la app pueda alcanzar. Para Android Emulator suele ser `http://10.0.2.2:8080`; para un teléfono físico, la IP LAN de Windows o una URL pública.

## 3) Levantar el backend

Asegurate de que el backend de Spring Boot esté corriendo localmente en:

```bash
http://localhost:8080
```

## 4) Opcional: exponer el backend con ngrok

En otra terminal, desde la raíz del proyecto, ejecutá:

```bash
ngrok http 8080
```

Copiá la URL pública que te da ngrok, por ejemplo:

```bash
https://abcd1234.ngrok-free.app
```

Y usá esa URL como `EXPO_PUBLIC_API_URL` en el `.env`.

## 5) Levantar Expo

Desde `frontend-native`:

```bash
npx expo start
```

Luego abrí la app en Expo Go y escaneá el QR.

## Nota importante

Si la URL de ngrok cambia, tenés que:

1. actualizar el `.env`
2. reiniciar Expo
3. volver a abrir la app en Expo Go

## Si no querés usar ngrok

El teléfono y Windows deben estar en una red que permita acceder al puerto 8080 mediante la IP LAN de Windows. También podés desplegar el backend en un servicio público.

## Puerto clave

- `8081`: puerto del Expo Dev Server
- `8080`: puerto del backend Spring Boot

No confundas esos dos.

