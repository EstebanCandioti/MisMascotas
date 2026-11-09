# Interrupción del ejecutor Web

Se corrigió un doble paso del contexto a los ayudantes HTTP y de aserciones. Produjo una referencia circular al intentar serializar el resultado, por lo que no se llegó a agregar una fila en ese momento.

Se concilia el intento de MAS-LIS-002 como Bloqueada por instrumentación y los otros dos casos como No ejecutada. Se conserva la captura original. La siguiente tanda Web ejecutó y aprobó los tres casos. No es un defecto de las funciones de Mascotas.
