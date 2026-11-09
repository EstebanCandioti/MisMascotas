# Tandas de Mascotas: MAS-API y MAS-WEB

## Objetivo y entrada

Ejecutar los 19 casos existentes del módulo. Requiere backend, PostgreSQL y Mailpit
QA saludables; para la tanda Web también frontend saludable, Playwright y Edge.
No reconstruir ni cambiar imágenes durante una tanda. No requiere seed limpio para
Free: usa actores y mascotas propios. Premium requiere la cuenta QA conocida con
seis mascotas activas. Conservar el estado original de sus mascotas.

## Orden

1. Ejecutar la tanda API: 16 casos, con los seis impedimentos de acceso compartido
   explícitamente bloqueados.
2. Revisar registro, snapshots SQL y defectos antes de repetir un caso.
3. Ejecutar la tanda Web: MAS-LIS-002, MAS-ELI-001 y MAS-ELI-002.
4. Comprobar enlaces de evidencia, estados, ausencia de IDs repetidos y conservación
   del historial previo en `ejecuciones_pruebas.csv`.
5. Documentar el cierre con cantidades, imágenes, defectos abiertos y bloqueos.

## Criterios de salida

Todos los casos seleccionados tienen una fila real y evidencia o un impedimento
comprobable. Ningún caso con variantes fallidas se aprueba. Las funciones ausentes
no se sustituyen por comportamientos parecidos. Los defectos observados están en
`defectos_mascotas.md`, con el ID del caso y enlace a su intento. No exige corregir
el producto: la tarea es verificarlo y dejar sus resultados trazables.

Procedimiento, cobertura por caso y comandos: [mascotas_postman.md](../mascotas_postman.md).
