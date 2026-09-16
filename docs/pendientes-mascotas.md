# Pendientes tecnicos de Mascotas

Estos puntos quedan documentados como deuda tecnica para resolver en tareas futuras. No forman parte de la base de auditoria implementada en esta rama.

## Resueltos

- Eliminacion logica unificada: el borrado logico de mascotas se expresa solo mediante `fechaEliminacion`, igual que en el resto de las entidades del modelo. Se retiro el estado de mascota para evitar dos fuentes de verdad sobre el mismo hecho y eliminar el riesgo de desincronizacion.
- Autorizacion para edicion de mascotas: solo el propietario puede editar el perfil base.
- Autorizacion para eliminacion de mascotas: solo el propietario puede eliminar logicamente la mascota.
- Limite de mascotas segun plan: usuarios gratuitos pueden tener hasta 5 mascotas activas; usuarios premium no tienen limite.
- Ampliacion de columnas de auditoria: `valor_anterior` y `valor_nuevo` estan en `text` y el mapeo JPA usa `columnDefinition = "text"`.

## Pendientes

- Definir revocacion de accesos compartidos al eliminar una mascota; depende del modulo de cuidado compartido.
- Definir notificacion a cuidadores al eliminar una mascota; depende del modulo de notificaciones push.
- Implementar purga fisica o proceso de limpieza luego de 30 dias; requiere un proceso programado aun no implementado.

# Pendientes tecnicos de Recordatorios

Estos puntos quedan documentados como deuda tecnica para resolver en tareas futuras. No forman parte del cierre de autorizacion por propietario, validacion de tipos, confirmacion basica y auditoria de escrituras.

## Resueltos

- Autorizacion basica por propietario: las operaciones de recordatorios validan que el actor sea propietario de la mascota asociada.
- Validacion de tipos predefinidos: el DTO acepta solo `PIPETA_ANTIPARASITARIO`, `VISITA_VETERINARIO`, `MEDICACION`, `VACUNA` u `OTRO`.
- Confirmacion y desmarcado de recordatorios cumplidos por propietario: la confirmacion registra `confirmadoPor` y `confirmadoEn`; el desmarcado limpia ambos campos.
- Auditoria de escrituras: creacion, edicion, cambio de estado, confirmacion, reversion de confirmacion y eliminacion logica quedan anotadas con `@Auditable`.

## Pendientes

- Confirmacion por parte de perfiles Familia y Cuidador; depende del modulo de cuidado compartido.
- Permisos para perfiles Familia y Cuidador; depende del modulo de cuidado compartido.
- Vista de recordatorios del dia agrupados por mascota y accesible a todos los usuarios con acceso a la mascota; depende del modulo de cuidado compartido.
- Visibilidad del estado de confirmacion para todos los usuarios con acceso a la mascota; depende del modulo de cuidado compartido.
- Programacion de tareas y calculo del proximo disparo en recordatorios repetitivos.
- Notificacion de aviso cuando un recordatorio no fue confirmado antes de su horario; depende de la infraestructura de notificaciones.
- Notificaciones push y almacenamiento de tokens de dispositivo.
- Multiples avisos previos por recordatorio.
- Desactivacion y reactivacion con cancelacion y reprogramacion efectiva de notificaciones.