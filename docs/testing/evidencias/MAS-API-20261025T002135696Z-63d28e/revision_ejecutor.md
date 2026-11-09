# Revisión de la primera tanda

La comparación exacta de creadoEn produjo falsos positivos en MAS-REG-001, MAS-REG-005, MAS-DET-001 y MAS-EDI-003, y ocho comprobaciones de MAS-REG-003. Java emitió nanosegundos y PostgreSQL redondeó a microsegundos. Los datos de negocio coincidían.

Los IDs DEF-MAS-PERSISTENCIA, DEF-MAS-DETALLE y DEF-MAS-EDICION-AJENA quedan descartados como defectos del producto y se conservan para auditoría de la instrumentación. El comparador exige igualdad de todos los datos y acepta como máximo un milisegundo en creadoEn. La tanda se repite con una versión nueva del ejecutor; las filas e informes de este intento permanecen.

Los fallos de entradas inválidas y modificación de peso requieren una repetición independiente y no se descartan por esta revisión.
