# Mascotas del seed QA

## Estado

Catálogo completo de **15 mascotas activas** para las seis cuentas QA, implementado y cargado el **2026-10-22** mediante `qa-base-v1`. Se conservan Toby y Nala, acordados para Free habitual, y se completan las otras 13 mascotas con la variedad delegada al criterio QA. Todos los datos son ficticios. El procedimiento y los UUID estables se documentan en `seed_qa.md`.

Este documento complementa `cuentas_qa.md` en el repositorio `D:\AAAIES\MisMascotas-front`. El detalle de los eventos está en `eventos_clinicos_qa.md` y el catálogo de recordatorios en `recordatorios_qa.md`. Los restantes módulos se definirán por separado.

## Distribución por cuenta

| Cuenta | Mascotas activas | Propósito principal |
|---|---:|---|
| QA-USR-001 — Free vacío | 0 | Listado vacío y primera alta |
| QA-USR-002 — Free habitual | 2 | Historial completo y mínimo |
| QA-USR-003 — Free al límite | 5 | Cupo Free y variedad de edades |
| QA-USR-004 — Premium | 6 | Listado por encima del límite Free y fichas variadas |
| QA-USR-005 — Propietario A | 1 | Mascota homónima de otro dueño |
| QA-USR-006 — Propietario B | 1 | Segunda identidad para verificar aislamiento |

## Cuenta Free habitual

Propietario de ambas mascotas: `qa.free.habitual@mismascotas.test`.

| ID de fixture | Nombre | Especie | Edad al restaurar | Dato de edad | Raza | Peso actual |
|---|---|---|---|---|---|---:|
| QA-MAS-001 | Toby | Perro | 4 años | Fecha de nacimiento conocida | Mestizo | 18,50 kg |
| QA-MAS-002 | Nala | Gato | 8 meses | Edad aproximada | Sin especificar | 3,20 kg |

Para Toby se usa fecha de nacimiento conocida y no se carga simultáneamente una edad manual contradictoria. Para Nala se usa la alternativa de edad aproximada: `fechaAproximada=true`, `edadValor=8`, `edadUnidad=meses`, sin inventar una fecha de nacimiento exacta. Esta combinación permite comprobar ambas formas de informar la edad.

## Cuenta Free al límite

Propietario: `qa.free.limite@mismascotas.test`. Las cinco mascotas están activas; una nueva alta debe encontrar el cupo completo sin depender de una prueba anterior.

| ID de fixture | Nombre | Especie | Edad al restaurar | Dato de edad | Raza | Peso actual |
|---|---|---|---|---|---|---:|
| QA-MAS-003 | Luna | Perro | 2 años | Fecha de nacimiento conocida | Mestizo | 12,40 kg |
| QA-MAS-004 | Milo | Gato | Sin informar | Sin fecha ni edad manual | Sin especificar | 4,60 kg |
| QA-MAS-005 | Chispa | Perro | 10 semanas | Edad aproximada | Sin especificar | 2,10 kg |
| QA-MAS-006 | Mora | Gato | 12 años | Fecha de nacimiento conocida | Sin especificar | 4,20 kg |
| QA-MAS-007 | Coco | Conejo | 14 meses | Fecha de nacimiento aproximada | Sin especificar | 1,80 kg |

Milo permite comprobar que la ausencia de edad no se presenta como cero ni como una fecha ficticia. Coco usa una fecha aproximada en el campo de nacimiento y no una edad manual; así se cubre una alternativa distinta de la edad aproximada de Nala y Chispa. El contrato actual recibe especie como texto libre, por lo que Conejo ejercita la presentación de una especie distinta de Perro y Gato.

## Cuenta Premium

Propietario: `qa.premium@mismascotas.test`. Sus seis mascotas activas superan intencionalmente el cupo de cinco de Free.

| ID de fixture | Nombre | Especie | Edad al restaurar | Dato de edad | Raza | Peso actual |
|---|---|---|---|---|---|---:|
| QA-MAS-008 | Bruno | Perro | 8 años | Fecha de nacimiento conocida | Labrador | 32,75 kg |
| QA-MAS-009 | Ámbar | Gato | 4 años | Edad aproximada | Sin especificar | 4,35 kg |
| QA-MAS-010 | Pipa | Hámster | 8 semanas | Edad aproximada | Sin especificar | 0,08 kg |
| QA-MAS-011 | Simón | Gato | 18 meses | Fecha de nacimiento conocida | Sin especificar | 5,10 kg |
| QA-MAS-012 | Lola | Perro | 21 días | Edad aproximada | Mestizo | 0,65 kg |
| QA-MAS-013 | León | Perro | 14 años | Edad aproximada | Sin especificar | 8,90 kg |

Ámbar, Simón y León cubren caracteres con tilde. Pipa permite revisar pesos menores de un kilogramo sin redondearlos a cero; Lola cubre la unidad de edad en días. La cuenta Premium también debe poder usar una ficha sin foto: las fotografías exclusivas Premium se prepararán como datos específicos de esos casos, con archivos locales versionados cuando se implemente su carga.

## Propietarios adicionales

| Cuenta y propietario | ID de fixture | Nombre | Especie | Edad al restaurar | Dato de edad | Raza | Peso actual |
|---|---|---|---|---|---|---|---:|
| QA-USR-005 — `qa.propietario.a@mismascotas.test` | QA-MAS-014 | Toby | Perro | 7 años | Fecha de nacimiento conocida | Sin especificar | 25,20 kg |
| QA-USR-006 — `qa.propietario.b@mismascotas.test` | QA-MAS-015 | Nala | Gato | 2 años | Edad aproximada | Sin especificar | 4,90 kg |

Los nombres repetidos entre propietarios son intencionales. QA-MAS-014 no es QA-MAS-001 y QA-MAS-015 no es QA-MAS-002. Las consultas, las ediciones y las eliminaciones deben identificar cada mascota por su ID y verificar el propietario, nunca resolverla solo por nombre.

## Representación de edades y campos opcionales

Se usa una sola representación de edad por mascota, salvo la marca que aclara si una fecha es aproximada.

| Forma de carga | Mascotas | Campos objetivo |
|---|---|---|
| Fecha conocida | Toby QA-MAS-001, Luna, Mora, Bruno, Simón, Toby QA-MAS-014 | `fechaNacimiento` calculada desde D0, `fechaAproximada=false`, edad manual nula |
| Fecha aproximada | Coco | `fechaNacimiento=D0 menos 14 meses`, `fechaAproximada=true`, edad manual nula |
| Edad manual aproximada | Nala QA-MAS-002, Chispa, Ámbar, Pipa, Lola, León, Nala QA-MAS-015 | Fecha nula, `fechaAproximada=true`, valor y unidad de la tabla |
| Edad desconocida | Milo | Fecha y edad manual nulas, `fechaAproximada=false` |

Las unidades de edad manual usan los literales actuales `dias`, `semanas`, `meses` y `anios`. Las fechas conocidas se calculan restando a D0 los años o meses de la tabla, sin guardar también una edad manual que pueda quedar desactualizada.

Donde la raza diga «Sin especificar», se guarda `null`, no esa frase como raza. Todas las mascotas comienzan con `fotoPerfil=null`, sin URLs externas. Las notas son nulas salvo las siguientes:

- **Chispa:** `Edad estimada en semanas. Ficha de prueba QA.`
- **Coco:** `Fecha de nacimiento aproximada. Ficha de prueba QA.`
- **Ámbar:** `Observación QA: nombre con tilde, signos (¿?) y número 123.`
- **León:** `Ficha senior QA. Mantener la autoría de sus registros.`

## Historial inicial

Las fechas se calculan respecto de **D0**, la fecha de restauración del seed en Argentina. Las horas locales se convierten a instantes con zona horaria al cargar; PostgreSQL conserva el instante y la evidencia registra D0 y T0. Así se mantienen las edades y la cronología previstas al recrear el escenario.

| Mascota | Fecha relativa | Tipo del backend | Datos |
|---|---|---|---|
| Toby | D0 − 90 días | PESO | Peso inicial de 19,00 kg |
| Toby | D0 − 60 días | VACUNA | Nombre ficticio: Vacuna de prueba QA |
| Toby | D0 − 45 días | MEDICACION | Nombre y dosis explícitamente ficticios de QA |
| Toby | D0 − 30 días | PESO | Medición de 19,80 kg |
| Toby | D0 − 14 días | CONSULTA_VETERINARIA | Motivo: Control de prueba QA |
| Toby | D0 − 7 días | PESO | Medición de 18,50 kg |
| Nala | D0 − 7 días | PESO | Único registro: peso inicial de 3,20 kg |

La dosis de la medicación es texto de prueba para validar el campo y no una indicación veterinaria. No se agregan a Nala QA-MAS-002 vacunas, medicaciones ni consultas como parte de este escenario.

Cada una de las otras 13 mascotas, QA-MAS-003 a QA-MAS-015, tiene un único evento `PESO` en D0 − 7 días por el valor de su columna «Peso actual». No se les precargan otros eventos clínicos. Cada evento se atribuye al propietario de su mascota, sin cruzar cuentas.

Se usa como hora base las 12:00 de Argentina para estos eventos, convertida a UTC al cargar. El punto de partida queda así: **15 mascotas, 17 registros PESO y 3 eventos de otros tipos, 20 eventos clínicos en total**. La última medición de cada mascota coincide con su peso actual.

Los IDs de cada evento y los valores de todos sus campos se detallan en `eventos_clinicos_qa.md`, que conserva esta distribución y evita que el cargador tenga que inventar contenido. El catálogo de `recordatorios_qa.md` define 15 recordatorios independientes; no se cuentan como eventos clínicos.

## Comprobaciones que habilita

- Listado de varias mascotas de un mismo propietario y apertura del detalle correcto.
- Presentación de fecha de nacimiento conocida y de edad aproximada.
- Historial con los cuatro tipos clínicos admitidos actualmente y filtrado por tipo.
- Gráfico de Toby con aumento y descenso de peso. El valor actual es 18,50 kg por ser la medición más reciente, sin borrar las anteriores.
- Historial mínimo de Nala con un único peso inicial, que no debe confundirse con historial completamente vacío.
- Comprobación de acceso denegado desde las cuentas Propietario A y Propietario B.
- Cupo de cinco mascotas activas Free y seis activas Premium, con listado sin truncado por el límite Free.
- Cuatro unidades de edad manual, fecha exacta, fecha aproximada y ausencia de edad.
- Especies diferentes, datos opcionales ausentes, pesos decimales y texto con caracteres españoles.
- Homónimos entre cuentas que deben mantener IDs, historiales y permisos separados.

Los escenarios que necesiten una mascota sin ningún evento clínico se prepararán como fixtures propios de esos casos. No se elimina el peso inicial de Nala para convertirla en otro escenario.

## Reglas de carga implementadas

- Las 15 mascotas están activas y pertenecen únicamente a la cuenta asignada en las tablas; no tienen accesos compartidos en el estado base.
- El peso actual y los eventos PESO deben quedar consistentes. No basta con completar únicamente `mascota.peso_actual`.
- Los datos Free no incorporan fotografías exclusivas Premium. La representación del avatar se ajustará al contrato implementado de la interfaz.
- Los datos de este catálogo son el estado inicial restaurable. Una edición o eliminación durante una prueba requiere aislamiento o restauración antes de reutilizarlos.
- Los IDs de fixture QA-MAS identifican escenarios. Su correspondencia con los UUID de la base está en `seed_qa.md`; no se reutiliza un UUID para dos mascotas homónimas.
- Mascotas eliminadas lógicamente, nombres en longitudes límite, pesos inválidos y otros datos de frontera se preparan dentro de sus pruebas. No se incluyen como datos normales del catálogo base.

## Desalineación actual que debe verificarse

El formulario web permite pesos hasta 9999,99 kg, mientras que el registro clínico `PESO` valida entre 0,01 y 300,00 kg. Además, el alta de mascota asigna directamente `pesoActual`. Este catálogo usa valores compatibles con el rango clínico y siempre incluye el evento de peso correspondiente. Al implementar el cargador y automatizar el alta se debe comprobar la consistencia entre ambos flujos; la existencia de estos fixtures no aprueba ni corrige esa diferencia.
