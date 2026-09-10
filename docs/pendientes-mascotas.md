# Pendientes tecnicos de Mascotas

Estos puntos quedan documentados como deuda tecnica para resolver en tareas futuras. No forman parte de la base de auditoria implementada en esta rama.

## Resueltos

- Invariante de eliminacion logica: al eliminar una mascota se actualiza `estado` a ELIMINADA y se establece `fechaEliminacion` dentro de la misma transaccion.
- Autorizacion para edicion de mascotas: solo el propietario puede editar el perfil base.
- Autorizacion para eliminacion de mascotas: solo el propietario puede eliminar logicamente la mascota.
- Limite de mascotas segun plan: usuarios gratuitos pueden tener hasta 5 mascotas activas; usuarios premium no tienen limite.
- Ampliacion de columnas de auditoria: `valor_anterior` y `valor_nuevo` estan en `text` y el mapeo JPA usa `columnDefinition = "text"`.

## Pendientes

- Definir si corresponde implementar recuperacion de mascota eliminada; no esta documentado en ningun caso de uso vigente.
- Definir revocacion de accesos compartidos al eliminar una mascota; depende del modulo de cuidado compartido.
- Definir notificacion a cuidadores al eliminar una mascota; depende del modulo de notificaciones push.
- Implementar purga fisica o proceso de limpieza luego de 30 dias; requiere un proceso programado aun no implementado.