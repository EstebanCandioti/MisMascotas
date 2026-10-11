Plan de desarrollo — Backend MisMascotas

Última actualización: 11 de octubre de 2026

Estado actual

Módulos funcionando (9): Auth con 2FA, refresh tokens y logout · Usuario (/me) · Mascotas · Recordatorios · Eventos Clínicos · Álbumes · Fotos · Badges · Auditoría (escritura activa, consulta deshabilitada temporalmente).

Módulos sin construir (4):

Módulo	Estado
Cuidado compartido (CU4, CU25)	La entidad AccesoCompartido no existe; Invitacion sin service
Pagos y suscripciones (CU26)	SuscripcionService vacío; Pago sin service ni controller
Calendario (CU15)	No existe
Exportación PDF (CU12)	No existe

Pendientes transversales: notificaciones push (bloqueado por development build del front), adjuntos (bloqueado por decisión de almacenamiento), endpoint de recordatorios del día (CU11, RN-051).

CU9 (historial y evolución) probablemente ya esté cubierto con GET /mascotas/{id}/eventos-clinicos?tipo=peso. Confirmar contra el CU.

Estado de ramas al 7 de octubre: backend y main en el mismo commit (96eae5f). En el working tree de backend hay cambios sin commitear de la centralización de acceso (AccesoMascotaService.java, cambios en seis services, docs/decisiones-acceso-mascotas.md).

Cómo se trabaja

División de roles entre agentes: Codex implementa, Claude verifica leyendo el código. El prompt de implementación no incluye verificación funcional — eso se hace por separado y sin el sesgo de haber escrito el código.

Criterio de tamaño de tarea: una tarea es un cambio verificable de una pasada, sin decisiones abiertas adentro. Si hay una bifurcación real, se resuelve antes y el prompt ya la trae cerrada.

Ramas: backend es la rama de integración del backend; las features se desprenden de ahí y vuelven ahí. main refleja el estado entregable y se actualiza al cierre de cada fase. frontend cumple el mismo rol del lado del cliente.

Punto ciego conocido: backend y frontend no se cruzan hasta main, así que los cambios de contrato de la API no se detectan hasta el merge. La mitigación es mantener un documento de contrato que ambas partes consulten sin leer el código de la otra — ver claude/front-web-especificacion-tecnica.md y claude/front-nativo-especificacion-tecnica.md, que contienen el contrato verificado contra el código.

Fases
Fase 0 — Destrabar
Pushear el trabajo acumulado ✅
Centralizar el acceso a mascotas. ✅ Implementado y verificado en `AccesoMascotaService` y los seis módulos consumidores. El acceso ajeno sigue devolviendo 403 ACCESO_DENEGADO; mascota inexistente o eliminada, 404. Familia y Cuidador quedan fuera de alcance hasta la fase de cuidado compartido.
Revisar las ramas del front contra el contrato real.
Front web (Yasmin): ✅ documentado en claude/front-web-que-cambio.md y claude/front-web-especificacion-tecnica.md. Pendiente de aplicar.
Front nativo (Santiago): ✅ documentado en claude/front-nativo-que-cambio.md y claude/front-nativo-especificacion-tecnica.md. Pendiente de aplicar. Hallazgo principal: el login está roto contra el backend actual (lee token en lugar de accessToken/refreshToken). La versión vigente es feature-reminders, que contiene health y albums.
Actualizar la colección de Postman. Es el instrumento de todas las pruebas que siguen y hoy no refleja el contrato actual. El contrato de álbumes, fotos, badges y usuario quedó relevado el 7 de octubre y está volcado en la especificación del front nativo.
Fase 1 — Docker base
Dockerfile del backend — sirve para local y para despliegue, no compromete ninguna decisión posterior.
Compose con Postgres, backend, init.sql y seed, con ddl-auto=validate.

Desde acá el equipo trabaja contra un entorno reproducible y desaparece la clase entera de problemas de schema: base efímera, nada que migrar, y si una entidad no coincide con el schema la aplicación no arranca en vez de avisar tres días después.

Agregar .gitattributes en esta fase, para evitar que archivos con terminadores de Windows rompan scripts dentro del contenedor.

Fase 2 — Verificar lo construido
Pruebas manuales con Postman contra el Docker, sobre el seed: flujos de sesión completos, validaciones de negocio, borrado lógico, auditoría y contrato de la API.
Corregir lo que aparezca.

No invertir en probar autorización: es lo que se reescribe en la fase 4.

Fase 3 — Recordatorios y calendario
Decidir dia_semana (singular vs varios días) y migrar el schema.
Motor de recurrencia: cálculo del próximo disparo y expansión de ocurrencias.
Endpoint de recordatorios del día (CU11, RN-051).
Calendario: GET /calendario?desde=&hasta= con fechas obligatorias; el resto de los filtros los aplica el front.
Fase 4 — Cuidado compartido
Entidades Invitacion y AccesoCompartido, más el estado RECHAZADA que falta en la tabla estados.
Flujo de invitación (CU4) y gestión (CU25).
Abrir permisos por perfil, sobre el componente centralizado de la fase 0.
Fase 5 — Lo que queda
Exportación PDF (CU12).
Adjuntos y almacenamiento de archivos, si entran en alcance.
Álbum asociado a varias mascotas (N:M en el contrato de la API). Requiere cambio de DTO y UI de selección múltiple en ambos fronts.
En paralelo — Pagos
MercadoPago, webhook y plan premium real. No depende del camino crítico, así que puede repartirse.
Pre-entrega
Rotar los tres secretos (SMTP, base de datos, firma de tokens) y sacarlos del repositorio. Rotar es obligatorio: sacarlos del archivo no los borra del historial.
Rate limiting en los endpoints de autenticación.
Definir el formato de la demo y preparar un plan de respaldo.
Decisiones tomadas

Calendario: el backend filtra por rango de fechas, obligatorio, porque es lo que determina el volumen. El front filtra por mascota, propietario y tipo, porque son interacciones de interfaz que deben ser instantáneas. Un parámetro de propietario en el servidor además obligaría a validar que ese propietario comparta con el usuario.

Docker: dockerizar sí, mudar la infraestructura no. El contenedor sirve igual para despliegue, así que no cierra ninguna puerta. Por etapas: Dockerfile, compose local, almacenamiento de archivos cuando se implementen fotos, fronts cuando estén integrados.

Almacenamiento de archivos: usar un SDK compatible con S3 en lugar del cliente propio de un proveedor. Permite apuntar a un servidor local durante el desarrollo y a cualquier proveedor en producción cambiando configuración.

Schema: el script de inicialización como fuente de verdad y ddl-auto=validate, para que las divergencias entre entidades y base aparezcan como error de arranque en lugar de como advertencias silenciosas.

Webhooks: el endpoint de notificación de pagos es el único componente que no puede vivir solo en entorno local, porque requiere una URL pública alcanzable. Durante el desarrollo se resuelve con un túnel; en producción, con el backend desplegado. Debe ser idempotente: la pasarela reenvía notificaciones si no recibe respuesta a tiempo.

Álbum N:M postergado (7 de octubre): el contrato de la API se mantiene con mascotaId singular por ahora. El schema ya soporta N:M, así que ampliarlo no requiere migración. Se posterga porque no es prioridad y el front nativo ya está construido sobre la forma singular. Queda como tarea de la fase 5.

Inconsistencias detectadas, sin resolver

Hallazgos de revisiones de código que no se corrigieron todavía porque exceden el alcance de la tarea donde aparecieron.

AlbumRequestDTO contradice el modelo N:M. Tiene un campo mascotaId singular y obligatorio, cuando la decisión arquitectónica fue que un álbum puede asociarse a varias mascotas. Postergado conscientemente: ver "Álbum N:M postergado". El PUT /albumes/{id} exige mascotaId pero lo ignora.

Nombre inconsistente en recordatorios. El DTO de request usa diasSemana y el de response devuelve diaSemana. Conviene unificarlo cuando se implemente el motor de recurrencia, que es donde ese campo va a cambiar de todos modos.

Álbum sin mascotas activas queda inaccesible. Si todas las mascotas asociadas a un álbum fueron eliminadas lógicamente, la verificación de acceso devuelve recurso no encontrado también para el propietario legítimo, que no puede ni verlo ni eliminarlo. Es un efecto secundario conocido del guard contra la lista vacía.

Edición de badges no implementada. CU27 paso 3.2 define modificar texto o emoji, y no existe endpoint.

Edición de perfil sin endpoint. UsuarioController solo expone GET /usuario/me. El front nativo tiene edición de nombre en local. Pendiente de desglosar los endpoints de usuario.

Mascota es la única entidad con estado de eliminación propio — en realidad ya no: se unificó el patrón y todas usan solo fecha de eliminación. Dejado como registro de la decisión.

Pendientes de decisión
dia_semana en singular vs varios días por recordatorio (requiere migración).
Expo Go y notificaciones push se excluyen mutuamente: las push necesitan development build, que no se abre escaneando un código QR.
El 2FA obligatorio complica una demostración donde los evaluadores usen la aplicación por su cuenta: el código llega a una casilla que ellos no controlan.
Si Docker va antes o después de las pruebas manuales. La recomendación es antes, para probar contra un seed conocido en lugar de contra una base con datos acumulados.
Restricciones de plan premium en álbumes, fotos y badges: relevadas pero no implementadas, incluido el límite de tres badges por mascota.
Endpoints de usuario: edición de perfil y lo que definan los CU de configuración (CU10, CU10a).
Reenvío del código 2FA: hoy no hay endpoint propio y los fronts reenvían volviendo a llamar a login, lo que obliga a retener la contraseña en memoria durante la verificación. Un endpoint de reenvío basado en el token de preautenticación lo evitaría.
Cuándo está terminado
Los 27 casos de uso documentados tienen soporte en el backend, o están descontinuados con justificación escrita.
Ningún endpoint permite operar sobre datos ajenos.
La colección de Postman corre completa sin fallos, incluidos los casos de error.
El contrato de la API está documentado y el front lo consume sin desencuentros.
Los secretos están rotados y fuera del repositorio.
La documentación refleja el código: casos de uso y reglas de negocio actualizados donde cambiaron.

El punto 6 no es cosmético. En una defensa académica, que el código diga una cosa y los casos de uso otra es de lo primero que se pregunta.
