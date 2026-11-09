# Incorporación al historial de ejecuciones

Se incorporaron a `docs/testing/ejecuciones_pruebas.csv` los resultados de las cuatro
corridas Auth conservadas: un intento bloqueado por el ejecutor, una prueba individual
de registro y dos tandas de 35 casos. Son **72 filas nuevas**: 57 Aprobada, 4 Fallida
y 11 Bloqueada. Se conserva sin cambios la ejecución piloto previa: el total es 73.

Cada fila representa un resultado real de un caso en una corrida. Las repeticiones
conservan sus resultados anteriores; no se cuenta cada aserción como otra ejecución.
La copia del informe final en dos ubicaciones se registró una sola vez.

El historial mantiene los cuatro estados existentes. Los resultados PARCIAL del
ejecutor se registraron como Bloqueada, indicando qué comprobación obligatoria falta.
El SyntaxError del intento inicial se registra como bloqueo del ejecutor, no como
defecto del backend. Las aprobaciones corresponden a la capa API; UI sin probar.

Los informes originales guardaron inicio y fin de corrida, sin hora por caso. Por eso
`fecha_hora` utiliza el cierre real de cada corrida con offset argentino `-03:00` y
lo aclara en observaciones. No se inventaron marcas individuales.

No se conservaron IDs históricos de imágenes ni emails y UUID de las cuentas nuevas
en esos informes. Se documenta la ausencia en las fichas retrospectivas; no se usan
las imágenes actuales ni el commit del checkout para atribuir versiones históricas.
Se conserva la evidencia disponible y se documenta cada defecto con una incidencia
local. No se volvieron a ejecutar los casos para completar el historial.

Para las próximas pruebas, capturar la ficha y los datos saneados antes/durante la
ejecución y agregar una fila al historial por cada resultado, incluidos bloqueos y
reintentos. Postman no tiene acceso directo al sistema de archivos: ejecutar desde
su interfaz no modifica por sí solo el CSV. El agente debe registrar el resultado
al terminar cada ejecución.
