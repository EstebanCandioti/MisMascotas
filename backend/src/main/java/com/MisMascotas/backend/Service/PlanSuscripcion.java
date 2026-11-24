package com.MisMascotas.backend.Service;

import java.math.BigDecimal;

public record PlanSuscripcion(
        String codigo,
        String motivo,
        BigDecimal importe,
        String moneda,
        int frecuencia,
        String tipoFrecuencia) {
}
