package com.MisMascotas.backend.DTO;

import java.time.Instant;
import java.util.UUID;

/** Consulta local: el estado del mandato y la cobertura pagada son independientes. */
public record SuscripcionPropiaResponseDTO(
        boolean esPremium,
        boolean coberturaPagadaVigente,
        Instant fechaFinCobertura,
        Detalle suscripcion,
        Detalle intentoPendiente) {

    public record Detalle(
            UUID idSuscripcion,
            String tipoPlan,
            String estado,
            String estadoMercadoPago,
            Instant fechaInicio,
            Instant fechaVencimiento,
            Instant proximoRecargo,
            Instant fechaCancelacion,
            boolean tienePagoAprobado,
            boolean coberturaVigente,
            boolean requiereConciliacion) {}
}
