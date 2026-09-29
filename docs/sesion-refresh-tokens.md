# Sesion con access token y refresh token

El backend reemplaza el esquema anterior de un unico JWT de larga duracion por dos credenciales:

- Access token JWT: dura 15 minutos y se envia en `Authorization: Bearer ...` para acceder a endpoints protegidos.
- Refresh token opaco: dura 14 dias, se guarda hasheado en la tabla `refresh_token` y se usa solo para renovar la sesion.

## Flujo vigente

1. `POST /auth/register` crea el usuario, genera un codigo de verificacion por email y devuelve un token de preautenticacion. No devuelve sesion.
2. `POST /auth/login` valida credenciales, genera un codigo de verificacion por email y devuelve un token de preautenticacion.
3. `POST /auth/verificar-codigo` recibe el token de preautenticacion por header Bearer y el codigo por body. Si el codigo es valido, devuelve `accessToken`, `refreshToken`, `tipo` y `accessTokenExpiraEn`.
4. `POST /auth/refresh` recibe el refresh token y devuelve un par nuevo. Cada uso rota el refresh token: el anterior queda revocado y enlazado al nuevo.
5. `POST /auth/logout` recibe el refresh token del dispositivo actual y lo revoca. El access token ya emitido sigue siendo valido hasta expirar porque no se consulta contra base.

## Seguridad del refresh token

El refresh token no es un JWT y no contiene informacion del usuario. Es una cadena aleatoria generada con `SecureRandom`. En base se persiste solo su hash SHA-256 codificado en Base64 URL-safe.

Si se intenta usar un refresh token que ya fue revocado por rotacion, se interpreta como posible reutilizacion de un token filtrado. En ese caso se revocan todos los refresh tokens activos del usuario y la renovacion se rechaza.

## Propiedades

- `jwt.access-token-expiration-minutes=15`
- `jwt.refresh-token-expiration-days=14`
- `jwt.pre-auth-expiration-minutes=10`

## Reglas/documentacion obsoleta

Queda obsoleta la regla anterior que indicaba una sesion JWT de 30 dias. Tambien quedan obsoletos los pasos de registro e inicio de sesion que asumian que el registro emitia una sesion inmediata o que el login final entregaba un unico token largo.

## Limpieza recomendada

No se implementa limpieza automatica en este cambio. La alternativa recomendada es un job programado diario que elimine fisicamente tokens expirados o revocados hace mas de 30 dias. El costo es bajo: una consulta `DELETE` indexable por `expira_en` y `revocado_en`, y una tarea `@Scheduled` o job externo de Supabase/cron. Para este proyecto escolar conviene documentarlo y dejarlo como tarea tecnica futura antes de implementar infraestructura adicional.
