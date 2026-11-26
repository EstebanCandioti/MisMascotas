# QA local: álbumes, fotos y badges Premium

## Estado actual

Verificado el 26/11/2026 contra el backend QA habitual en `http://localhost:8080`, con PostgreSQL persistente y Mailpit en `http://localhost:8025`. El backend se actualizó a la imagen local `mismascotas-backend:badges-validacion` y quedó saludable. No se recreó el volumen de datos ni se inició el frontend.

Antes de actualizar se guardó un respaldo completo de PostgreSQL en el workspace de trabajo, fuera del repositorio. Las pruebas usaron cuentas y recursos temporales, que se retiraron al finalizar. Las cantidades y huellas de todas las tablas públicas coincidieron antes/después de la prueba.

Durante la ejecución se pausaron exclusivamente los procesos programados de notificaciones de MP y auditoría. Después se retiró esa pausa y se confirmó nuevamente el estado saludable del backend con su configuración normal.

## Álbumes

**41 solicitudes y 69 aserciones aprobadas**, sin omisiones ni fallos. Se verificaron creación/edición Premium; rechazo Free/vencido; lectura compartida; carga de fotos; borrado lógico y eliminación voluntaria sin Premium.

Colección: `postman/MisMascotas Albumes Premium.postman_collection.json`. Preparación y contrato: [albumes_premium.md](albumes_premium.md).

## Fotos y foto de perfil

**34 solicitudes y 69 aserciones aprobadas**, sin omisiones ni fallos. Se verificaron conservación por ausencia o URL igual; retiro con `null`; rechazo de URL nueva sin Premium; alta general Free; alta/reemplazo Premium y permisos de invitados.

Colección: `postman/MisMascotas Fotos Premium.postman_collection.json`. Preparación y contrato: [fotos_premium.md](fotos_premium.md).

## Badges

**37 solicitudes y 62 aserciones aprobadas**, sin omisiones ni fallos. Se verificaron tercera y cuarta badge; `409 / LIMITE_BADGES_ALCANZADO`; liberación del cupo; independencia por mascota; histórico excedido conservado; permisos y eliminación sin Premium.

Colección: `postman/MisMascotas Badges Premium.postman_collection.json`. Preparación, contrato y prueba concurrente PostgreSQL de CI: [badges_premium.md](badges_premium.md).

## Evidencia y repetición

Total: **112 solicitudes y 200 aserciones aprobadas** mediante las tres colecciones reales ejecutadas con Newman. Las comprobaciones adicionales sobre PostgreSQL confirmaron las bajas lógicas y la conservación del histórico. Esta ejecución verifica QA; las 791 pruebas de CI ya habían pasado en el cierre de implementación.

Evidencia local, fuera del repositorio: `artifacts/testing/multimedia-qa/20261126T043256Z/resultado.json`, `newman-albumes.json`, `newman-fotos.json` y `newman-badges.json`. El respaldo está en la misma carpeta y contiene datos de QA: no debe agregarse al repositorio.

Las colecciones conservan vacíos los tokens e IDs de las cuentas; para repetirlas manualmente hay que preparar los fixtures descritos en cada contrato. Las cuentas temporales de esta verificación ya no existen.

La configuración del contenedor actual combina el Compose de distribución QA, el override local de MP y `tmp/qa_multimedia/compose.backend-local.json` del workspace. Este último fija la imagen nueva con `pull_policy=never`; al recrear QA se debe conservar ese override, ya que el Compose de distribución todavía fija su imagen publicada anterior.
