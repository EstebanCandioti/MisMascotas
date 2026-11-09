# Eventos clínicos del seed QA

## Estado y referencias

Catálogo implementado y cargado en `mismascotas-web-testing` el **2026-10-22**, mediante `qa-base-v1`. Conserva los **20 eventos clínicos** previstos en `mascotas_qa.md`: 17 mediciones de peso, una vacuna, una medicación y una consulta. Las identidades y credenciales están en `cuentas_qa.md`; la restauración y los UUID, en `seed_qa.md`.

Los tipos corresponden a los que admite actualmente `EventoClinicoService`: `PESO`, `VACUNA`, `MEDICACION` y `CONSULTA_VETERINARIA`. No se incorporan tipos nuevos al contrato para preparar los datos.

## Fechas y autoría

**D0** es la fecha local de restauración en Argentina y **T0** el instante de esa restauración. Los eventos de este catálogo ocurren a las **12:00 de Argentina** del día indicado; se convierten a UTC antes de persistir. D0 y T0 se anotan en la evidencia de la corrida.

Cada evento pertenece exclusivamente a su `QA-MAS` y su `registrado_por_id` corresponde al propietario de esa mascota. La fecha clínica puede ser anterior a la fecha de carga: son antecedentes históricos precargados. Todos comienzan sin eliminación lógica.

## Catálogo base

| ID de fixture | Mascota | Fecha local | Tipo | Contenido principal |
|---|---|---|---|---|
| QA-ECL-001 | QA-MAS-001 — Toby habitual | D0 − 90 días | PESO | 19,00 kg |
| QA-ECL-002 | QA-MAS-001 — Toby habitual | D0 − 60 días | VACUNA | Vacuna de prueba QA |
| QA-ECL-003 | QA-MAS-001 — Toby habitual | D0 − 45 días | MEDICACION | Medicación de prueba QA |
| QA-ECL-004 | QA-MAS-001 — Toby habitual | D0 − 30 días | PESO | 19,80 kg |
| QA-ECL-005 | QA-MAS-001 — Toby habitual | D0 − 14 días | CONSULTA_VETERINARIA | Control de prueba QA |
| QA-ECL-006 | QA-MAS-001 — Toby habitual | D0 − 7 días | PESO | 18,50 kg |
| QA-ECL-007 | QA-MAS-002 — Nala habitual | D0 − 7 días | PESO | 3,20 kg |
| QA-ECL-008 | QA-MAS-003 — Luna | D0 − 7 días | PESO | 12,40 kg |
| QA-ECL-009 | QA-MAS-004 — Milo | D0 − 7 días | PESO | 4,60 kg |
| QA-ECL-010 | QA-MAS-005 — Chispa | D0 − 7 días | PESO | 2,10 kg |
| QA-ECL-011 | QA-MAS-006 — Mora | D0 − 7 días | PESO | 4,20 kg |
| QA-ECL-012 | QA-MAS-007 — Coco | D0 − 7 días | PESO | 1,80 kg |
| QA-ECL-013 | QA-MAS-008 — Bruno | D0 − 7 días | PESO | 32,75 kg |
| QA-ECL-014 | QA-MAS-009 — Ámbar | D0 − 7 días | PESO | 4,35 kg |
| QA-ECL-015 | QA-MAS-010 — Pipa | D0 − 7 días | PESO | 0,08 kg |
| QA-ECL-016 | QA-MAS-011 — Simón | D0 − 7 días | PESO | 5,10 kg |
| QA-ECL-017 | QA-MAS-012 — Lola | D0 − 7 días | PESO | 0,65 kg |
| QA-ECL-018 | QA-MAS-013 — León | D0 − 7 días | PESO | 8,90 kg |
| QA-ECL-019 | QA-MAS-014 — Toby propietario A | D0 − 7 días | PESO | 25,20 kg |
| QA-ECL-020 | QA-MAS-015 — Nala propietario B | D0 − 7 días | PESO | 4,90 kg |

## Campos de cada tipo

Los campos que no correspondan al tipo se guardan como `null`, no como cadenas vacías ni con datos de otro evento.

- **PESO:** `valorPeso` contiene el número de la tabla, con dos decimales. `nombre`, `dosis`, `motivo`, `diagnostico` y `observaciones` son nulos.
- **QA-ECL-002:** `nombre=Vacuna de prueba QA`, `observaciones=Aplicación ficticia para pruebas.`. Los otros campos opcionales son nulos.
- **QA-ECL-003:** `nombre=Medicación de prueba QA`, `dosis=Dosis ficticia QA`, `observaciones=Sin uso clínico.`. Los otros campos opcionales son nulos.
- **QA-ECL-005:** `motivo=Control de prueba QA`, `diagnostico=Resultado ficticio QA`, `observaciones=Registro de consulta para filtrado y exportación.`. `nombre`, `dosis` y `valorPeso` son nulos.

Estos textos son datos de prueba, no indicaciones veterinarias.

## Resultados de referencia

| Cuenta | Eventos totales | PESO | Otros eventos |
|---|---:|---:|---:|
| QA-USR-001 — Free vacío | 0 | 0 | 0 |
| QA-USR-002 — Free habitual | 7 | 4 | 3 |
| QA-USR-003 — Free al límite | 5 | 5 | 0 |
| QA-USR-004 — Premium | 6 | 6 | 0 |
| QA-USR-005 — Propietario A | 1 | 1 | 0 |
| QA-USR-006 — Propietario B | 1 | 1 | 0 |
| Total | 20 | 17 | 3 |

Toby habitual debe presentar la secuencia 19,00 → 19,80 → 18,50 kg y peso actual 18,50 kg. Las otras 14 mascotas tienen un solo punto en el gráfico, por el peso de su ficha. Los historiales de las mascotas homónimas permanecen separados por ID y propietario.

## Datos adicionales preparados por caso

Estos escenarios se crean de forma aislada y no cambian el catálogo base.

| Escenario | Preparación | Resultado a comprobar |
|---|---|---|
| Peso histórico cargado tarde | Agregar a QA-MAS-001 una medición de 19,40 kg en D0 − 45 días, cargada después de restaurar | Se agrega al historial y se conserva 18,50 kg como peso actual por su fecha de medición más reciente |
| Peso nuevo inferior | Agregar a QA-MAS-001 18,10 kg con fecha T0 menos un minuto | El peso actual cambia a 18,10 kg y permanecen las mediciones anteriores |
| Eliminar última medición | El propietario elimina QA-ECL-006 mediante el flujo permitido | El peso actual se recalcula con QA-ECL-004: 19,80 kg |
| Exportación extensa | Agregar 120 consultas ficticias a QA-MAS-001, una por día entre D0 − 120 y D0 − 1, con índice en el motivo | El PDF contiene todos los registros sin duplicación, omisión ni recortes; la prueba de descarga requiere la funcionalidad implementada |
| Campos inválidos y fronteras | Enviar datos específicos por API para cada caso de validación | Rechazo sin persistir registros inválidos; no se insertan para simular un seed correcto |

Nala habitual conserva únicamente QA-ECL-007 en el estado base. El escenario de una mascota sin ningún evento clínico se prepara por separado, sin quitarle silenciosamente su peso inicial.

## Alcance de la verificación

Precargar un evento no prueba su endpoint de alta ni la escritura automática de auditoría. Para comprobar esos comportamientos se ejecuta la operación real mediante la API. Los permisos de edición y eliminación por colaboradores siguen pendientes de definición; los escenarios anteriores usan al propietario, sin atribuir permisos nuevos a Familia o Cuidador.
