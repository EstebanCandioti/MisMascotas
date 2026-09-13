# MisMascotas Frontend Native

Este proyecto usa Expo Go para correr la app móvil.

## Requisitos

- Node.js
- npm
- Expo Go instalado en tu celular
- ngrok para exponer el backend públicomente desde tu computadora

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
EXPO_PUBLIC_API_BASE_URL=https://TU_URL_DE_NGROK
```

Ejemplo:

```env
EXPO_PUBLIC_API_BASE_URL=https://861e-152-170-185-250.ngrok-free.app
```

> Importante: la URL debe ser la de ngrok, no `localhost`. El backend corre en `http://localhost:8080`, pero Expo Go no puede acceder a tu PC usando `localhost`.

## 3) Levantar el backend

Asegurate de que el backend de Spring Boot esté corriendo localmente en:

```bash
http://localhost:8080
```

## 4) Exponer el backend con ngrok

En otra terminal, desde la raíz del proyecto, ejecutá:

```bash
ngrok http 8080
```

Copiá la URL pública que te da ngrok, por ejemplo:

```bash
https://abcd1234.ngrok-free.app
```

Y reemplazá esa URL en el `.env`.

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

No hay forma de que Expo Go se conecte al backend local con `localhost` desde el teléfono. Para probar desde un celular, o bien:

- usás ngrok, o
- desplegás el backend en un servicio público como Render

## Puerto clave

- `8081`: puerto del Expo Dev Server
- `8080`: puerto del backend Spring Boot

No confundas esos dos.

