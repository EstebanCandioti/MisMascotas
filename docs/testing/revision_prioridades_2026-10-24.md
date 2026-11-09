# Revisión de prioridades del 24 de octubre de 2026

## Alcance y criterio

Se revisaron individualmente los 240 casos del catálogo. Se cambió exclusivamente la columna `prioridad` en 77 casos. Se conservaron IDs, orden, escenarios, precondiciones, acciones, resultados esperados, tipos y fundamentos. No se ejecutaron pruebas ni se modificó su historial de resultados.

La prioridad indica el orden de ejecución según el riesgo específico del caso. No es la severidad automática de una incidencia ni el estado de implementación. Se ponderan impacto, frecuencia del flujo, posibilidad de recuperación y controles ya cubiertos por otros casos. No se busca una distribución por cuotas.

- **Crítica:** barreras de autenticación y acceso a datos sensibles, pérdida o corrupción de información relevante, integridad de pagos y flujos esenciales de mascotas, historial y tareas.
- **Alta:** reglas de negocio, cupos y habilitaciones comerciales, validaciones importantes, variantes de recurrencia, recuperación y funciones habituales con alternativas. Incluye permisos acotados sobre contenido decorativo recuperable.
- **Media:** navegación, presentación, filtros auxiliares, contenido decorativo recuperable y validaciones secundarias. También variantes de menor riesgo ya acompañadas de controles principales de mayor prioridad.

Se evalúa el fallo específico cubierto por el caso. Una afirmación general de ausencia de efectos colaterales no convierte toda edición descriptiva en crítica. En cambio, impedir un borrado al cancelar, rechazar una credencial vencida o evitar mezcla de propietarios sí son controles explícitos de alto impacto.

Los casos negativos no bajan por ser negativos. Los escenarios de acceso indebido conservan o aumentan su prioridad. Las funciones pendientes conservan la prioridad de su riesgo y se registran como bloqueadas o no ejecutadas cuando corresponda.

## Distribución

| Prioridad | Antes | Después |
|---|---:|---:|
| Crítica | 104 | 89 |
| Alta | 126 | 106 |
| Media | 10 | 45 |
| Total | 240 | 240 |

| Módulo | Crítica | Alta | Media | Total |
|---|---:|---:|---:|---:|
| Autenticación | 14 | 14 | 7 | 35 |
| Mascotas | 13 | 5 | 1 | 19 |
| Historial clínico | 8 | 8 | 1 | 17 |
| Recordatorios | 10 | 14 | 1 | 25 |
| Notificaciones | 2 | 7 | 1 | 10 |
| Cuidado compartido | 9 | 6 | 0 | 15 |
| Calendario | 3 | 10 | 9 | 22 |
| Exportación PDF | 3 | 7 | 2 | 12 |
| Álbumes y fotos | 10 | 14 | 7 | 31 |
| Premium y pagos | 10 | 3 | 1 | 14 |
| Badges | 0 | 6 | 6 | 12 |
| Configuración y perfil | 5 | 6 | 9 | 20 |
| Auditoría | 2 | 6 | 0 | 8 |

## Tandas y consulta

- Smoke: subconjunto representativo de críticas. Los diez IDs de `tandas/smoke_api_01.md` se mantienen críticos y no se modifica su selección.
- Regresión habitual: críticas y altas del alcance afectado.
- Regresión completa: incorpora también las medias. Media no significa prescindible.
- `pruebas_qa.ods` lee el mismo CSV. Actualizar sus vínculos para ver las nuevas prioridades. No se modifica el archivo abierto ni se crea otro catálogo.

## Cambios por caso

Este registro documenta decisiones. `casos_prueba.csv` sigue siendo el catálogo maestro.

| Caso | Antes | Después | Motivo |
|---|---|---|---|
| AUT-REG-003 | Alta | Media | Formato de email recuperable por el usuario, sin un control de acceso adicional al de los casos de autenticación. |
| AUT-REG-007 | Alta | Media | Combinación de campos vacíos. Los campos de autenticación individuales y la denegación de acceso siguen cubiertos por casos de mayor prioridad. |
| AUT-REG-008 | Crítica | Alta | Recuperación de un registro interrumpido. Es importante para el alta, pero no es un bypass de acceso ni el camino inicial de registro. |
| AUT-REG-009 | Crítica | Alta | Continuidad de navegación al desafío. El registro y la exigencia de 2FA conservan sus controles críticos propios. |
| AUT-REG-010 | Alta | Media | Nombre obligatorio de presentación. Su validación no concede acceso ni modifica la identidad autenticada. |
| AUT-REG-011 | Alta | Media | Combinación de campos vacíos. Los campos de autenticación individuales y la denegación de acceso siguen cubiertos por casos de mayor prioridad. |
| AUT-REG-012 | Alta | Media | Combinación de campos vacíos. Los campos de autenticación individuales y la denegación de acceso siguen cubiertos por casos de mayor prioridad. |
| AUT-REG-013 | Alta | Media | Combinación de campos vacíos. Los campos de autenticación individuales y la denegación de acceso siguen cubiertos por casos de mayor prioridad. |
| AUT-LOG-002 | Alta | Crítica | Credenciales de una identidad inexistente nunca deben producir una sesión. Es un control directo de autenticación. |
| AUT-LOG-006 | Alta | Media | Combinación de campos vacíos. Los campos de autenticación individuales y la denegación de acceso siguen cubiertos por casos de mayor prioridad. |
| AUT-2FA-002 | Crítica | Alta | Recuperación de un registro interrumpido. Es importante para el alta, pero no es un bypass de acceso ni el camino inicial de registro. |
| AUT-2FA-004 | Alta | Crítica | Aceptar un código vencido eludiría la vigencia del segundo factor. Se toma el riesgo del rechazo, aunque el caso también incluya recuperación. |
| AUT-SES-006 | Alta | Crítica | El caso incluye rechazar access tokens expirados, un control directo de vigencia de sesión. |
| MAS-REG-004 | Crítica | Alta | Límite comercial Free. Un exceso de cupo no equivale por sí mismo a acceso ajeno o pérdida de datos. |
| MAS-LIS-003 | Alta | Crítica | Una invitación pendiente no habilita acceso. Incluir la mascota como accesible vulneraría esa frontera de autorización. |
| MAS-EDI-001 | Crítica | Alta | Edición habitual de datos básicos recuperables. La autorización y la protección específica del peso tienen casos críticos propios. |
| MAS-EDI-004 | Alta | Crítica | El peso pertenece al historial clínico. El flujo de datos básicos no debe permitir sobrescribir ese historial. |
| MAS-ELI-002 | Alta | Crítica | Cancelar no debe ejecutar una baja de mascota y sus relaciones. No hay recuperación de usuario definida para una baja accidental. |
| ECL-REG-005 | Crítica | Alta | Primera carga de peso. Su indisponibilidad afecta una función importante, sin implicar sobrescritura de mediciones anteriores. |
| ECL-LIS-002 | Alta | Media | Filtro de consulta con alternativa de revisar el historial completo. No cambia mediciones ni permisos. |
| ECL-LIS-004 | Alta | Crítica | Consulta de historial clínico según acceso compartido vigente: datos sensibles y frontera de autorización. |
| REC-REC-001 | Crítica | Alta | Frontera mensual del día 31. Es una regla de recurrencia importante, no la generación básica de toda la serie. |
| REC-EST-001 | Crítica | Alta | Desactivación reversible de futuras tareas. Los controles de conservación y eliminación del historial mantienen su prioridad propia. |
| NOT-GEN-003 | Alta | Media | Explicación de una restricción del sistema operativo y presentación del estado de entrega. No se concede acceso ni se confirma la tarea. |
| NOT-GEN-004 | Crítica | Alta | Suscripción voluntaria al push de un colaborador ya autorizado. Es una preferencia, no la concesión de acceso a una mascota. |
| NOT-REA-002 | Crítica | Alta | Detener avisos adicionales tras confirmar. El estado de la ocurrencia y la entrega inicial se prueban por separado. |
| CUI-INV-003 | Crítica | Alta | Cupo Free de invitaciones. La propiedad y la aceptación se verifican con controles críticos separados. |
| CUI-INV-008 | Alta | Crítica | Una invitación cancelada no puede seguir concediendo acceso. Es revocación de una credencial de compartición. |
| CAL-VIS-001 | Crítica | Alta | Vista mensual de consulta. Las tareas del día y los controles de acceso tienen sus casos críticos específicos. |
| CAL-VIS-002 | Alta | Media | Cambio entre vistas conservando contexto. Es una función de navegación de un calendario de solo consulta. |
| CAL-FIL-001 | Alta | Media | Filtro de presentación sobre contenido accesible. No modifica datos y existe la consulta sin ese filtro. |
| CAL-FIL-002 | Alta | Media | Filtro de presentación sobre contenido accesible. No modifica datos y existe la consulta sin ese filtro. |
| CAL-DET-002 | Alta | Media | Ubicación de la acción de confirmar y detalle de solo lectura. La confirmación real y sus permisos se prueban en tareas del día. |
| CAL-ALT-002 | Alta | Media | Precarga de mascota editable al abrir otro formulario. Es un atajo con selección manual disponible. |
| CAL-ALT-004 | Alta | Media | Cancelación de un alta no guardada. Un recordatorio no deseado es corregible por su gestión habitual. |
| CAL-HIS-001 | Crítica | Alta | Reflejo del estado e historial en una vista de consulta. La mutación de la ocurrencia y la conservación real del historial conservan casos críticos. |
| CAL-HIS-003 | Crítica | Alta | Reflejo del estado e historial en una vista de consulta. La mutación de la ocurrencia y la conservación real del historial conservan casos críticos. |
| EXP-ACC-001 | Crítica | Alta | Exportación autorizada de apoyo. La consulta del historial permanece disponible si falla la descarga. |
| EXP-ACC-003 | Alta | Media | Repetición de una exportación gratuita. Es una variante de uso, sin modificación de datos ni cobro. |
| EXP-SEG-001 | Alta | Crítica | Evitar una copia clínica persistente accesible en el servidor es una condición de confidencialidad. |
| ALB-LIS-001 | Crítica | Alta | Consulta Free de contenido existente. La denegación ante pérdida de acceso mantiene su caso crítico. |
| ALB-ALT-001 | Crítica | Alta | Alta de una función multimedia complementaria. Las asociaciones no autorizadas tienen casos críticos propios. |
| ALB-ALT-002 | Alta | Media | Nombre obligatorio de un álbum: validación recuperable de contenido descriptivo. |
| ALB-ALT-005 | Alta | Crítica | Un álbum no puede mezclar propietarios. Es una frontera de aislamiento, no solo una validación de selección. |
| ALB-ALT-006 | Crítica | Alta | Restricción comercial Premium de creación. No implica por sí sola exposición o borrado de contenido ajeno. |
| ALB-ALT-007 | Crítica | Alta | Restricción de creación no destructiva por rol, sobre mascotas a las que el colaborador ya tiene acceso. |
| ALB-EDI-001 | Alta | Media | Cambios descriptivos recuperables del álbum, sin cambiar fotos ni asociaciones. |
| ALB-EDI-003 | Crítica | Alta | Restricción de edición descriptiva por plan o rol. El borrado y la modificación de archivos tienen controles críticos separados. |
| ALB-ELI-003 | Alta | Crítica | Cancelar un borrado debe conservar fotos irreversibles. Ejecutarlo accidentalmente causaría pérdida permanente. |
| FOT-ALT-002 | Alta | Media | Aceptación del tamaño máximo exacto. El usuario dispone de archivos menores; el rechazo de excesos se conserva en Alta. |
| FOT-ALT-006 | Alta | Media | Elección o reemplazo de portada. Es presentación recuperable, sin pérdida del conjunto de fotos. |
| FOT-ELI-002 | Alta | Media | Elección o reemplazo de portada. Es presentación recuperable, sin pérdida del conjunto de fotos. |
| PRE-REN-001 | Alta | Crítica | Una renovación pagada no debe quitar días ya adquiridos: impacto financiero directo. |
| PRE-REN-002 | Alta | Crítica | Un pago no aprobado no puede extender un período de servicio pagado. |
| PRE-VEN-002 | Crítica | Alta | Control comercial de nuevas acciones Premium tras el vencimiento. La conservación de datos sigue siendo crítica. |
| PRE-VEN-004 | Alta | Media | Disponibilidad de una prestación gratuita tras vencer Premium. Es una variante del PDF gratuito, sin cobro ni borrado. |
| BAD-LIS-001 | Alta | Media | Contenido decorativo y recuperable, sin efecto en cuidados, historial clínico, sesiones o cobros. |
| BAD-ALT-001 | Crítica | Media | Contenido decorativo y recuperable, sin efecto en cuidados, historial clínico, sesiones o cobros. |
| BAD-ALT-002 | Alta | Media | Contenido decorativo y recuperable, sin efecto en cuidados, historial clínico, sesiones o cobros. |
| BAD-ALT-003 | Alta | Media | Contenido decorativo y recuperable, sin efecto en cuidados, historial clínico, sesiones o cobros. |
| BAD-ALT-004 | Crítica | Alta | Cupo o habilitación comercial de una función Premium decorativa. |
| BAD-ALT-006 | Crítica | Alta | Cupo o habilitación comercial de una función Premium decorativa. |
| BAD-ALT-007 | Crítica | Alta | Permisos de modificación de elementos decorativos recuperables. No son una concesión de acceso a datos clínicos ni un borrado irreversible de archivos. |
| BAD-EDI-001 | Alta | Media | Contenido decorativo y recuperable, sin efecto en cuidados, historial clínico, sesiones o cobros. |
| BAD-EDI-002 | Crítica | Alta | Permisos de modificación de elementos decorativos recuperables. No son una concesión de acceso a datos clínicos ni un borrado irreversible de archivos. |
| BAD-ELI-001 | Alta | Media | Contenido decorativo y recuperable, sin efecto en cuidados, historial clínico, sesiones o cobros. |
| BAD-ELI-002 | Crítica | Alta | Permisos de modificación de elementos decorativos recuperables. No son una concesión de acceso a datos clínicos ni un borrado irreversible de archivos. |
| CON-VIS-002 | Alta | Media | Presentación del estado o de un error parcial. Las preferencias persistidas y los envíos se verifican por separado. |
| CON-VIS-003 | Alta | Media | Presentación del estado o de un error parcial. Las preferencias persistidas y los envíos se verifican por separado. |
| CON-EDI-001 | Alta | Media | Edición o validación de datos de presentación recuperables. El email y el acceso a cuentas ajenas tienen casos de seguridad específicos. |
| CON-EDI-002 | Alta | Media | Edición o validación de datos de presentación recuperables. El email y el acceso a cuentas ajenas tienen casos de seguridad específicos. |
| CON-EDI-003 | Alta | Media | Edición o validación de datos de presentación recuperables. El email y el acceso a cuentas ajenas tienen casos de seguridad específicos. |
| CON-EDI-004 | Alta | Media | Edición o validación de datos de presentación recuperables. El email y el acceso a cuentas ajenas tienen casos de seguridad específicos. |
| CON-EDI-006 | Alta | Crítica | El email identifica la cuenta y no tiene un flujo de cambio aprobado. Evitar modificaciones por este flujo protege la identidad. |
| CON-PUS-003 | Alta | Media | Indicaciones ante un permiso bloqueado por el sistema operativo: usabilidad de recuperación. |
| AUD-REG-001 | Crítica | Alta | Trazabilidad y recuperación del subsistema de auditoría. Los secretos y la inmutabilidad tienen controles críticos específicos. |
| AUD-REG-004 | Crítica | Alta | Trazabilidad y recuperación del subsistema de auditoría. Los secretos y la inmutabilidad tienen controles críticos específicos. |

## Controles de preservación

- 240 IDs únicos, en el orden original y con las diez columnas existentes.
- 77 cambios de prioridad y ningún cambio en las otras nueve columnas.
- UTF-8 sin BOM, separador `;` y saltos LF conservados.
- No se modifican `ejecuciones_pruebas.csv`, evidencias, seed, servicios ni casos de uso académicos.

SHA-256 del catálogo anterior: `07d75844ca2218033f53bf3a8550c16b89c86f4c2bfc02f380e702093cdf7eb3`.
SHA-256 del catálogo revisado: `ddc07e3f32728d1b91ff7cffd2b6eecbab5393b3646f855152b5c7680741e147`.
