package com.MisMascotas.backend.Service;

import java.util.Comparator;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.MisMascotas.backend.DTO.SuscripcionPropiaResponseDTO;
import com.MisMascotas.backend.DTO.SuscripcionPropiaResponseDTO.Detalle;
import com.MisMascotas.backend.Entity.Suscripcion;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SuscripcionConsultaService {
    private final PremiumService premiumService;

    // Desempate explícito: el resultado no depende del orden de las filas de PostgreSQL.
    private static final Comparator<Suscripcion> MAS_RECIENTE = Comparator
            .comparing(Suscripcion::getFechaInicio, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(s -> s.getIdSuscripcion().toString());

    @Transactional(readOnly = true)
    public SuscripcionPropiaResponseDTO consultar(UUID usuarioId) {
        var consulta = premiumService.consultarSuscripciones(usuarioId);
        var estado = consulta.estado();
        var propias = consulta.suscripciones();
        var aprobadas = consulta.conPagoAprobado();
        var vigentes = consulta.conCoberturaVigente();

        // La cobertura ya pagada no se pierde por cancelación o reemplazo del mandato.
        var cobertura = propias.stream().filter(s -> vigentes.contains(s.getIdSuscripcion()))
                .max(Comparator.comparing(Suscripcion::getFechaVencimiento).thenComparing(MAS_RECIENTE))
                .orElse(null);
        var principal = cobertura != null ? cobertura : propias.stream()
                .filter(s -> !s.isReemplazada() && !pendiente(s))
                .max(MAS_RECIENTE).orElse(null);
        var intento = propias.stream().filter(s -> !s.isReemplazada() && pendiente(s))
                .max(MAS_RECIENTE).orElse(null);

        return new SuscripcionPropiaResponseDTO(estado.esPremium(), estado.coberturaPagadaVigente(),
                estado.fechaFinCobertura(), detalle(principal, aprobadas, vigentes), detalle(intento, aprobadas, vigentes));
    }

    private boolean pendiente(Suscripcion suscripcion) {
        return suscripcion.getEstadoCatalogo() != null
                && "PENDIENTE".equals(suscripcion.getEstadoCatalogo().getNombre());
    }

    private Detalle detalle(Suscripcion suscripcion, Set<UUID> aprobadas, Set<UUID> vigentes) {
        if (suscripcion == null) return null;
        boolean pagada = aprobadas.contains(suscripcion.getIdSuscripcion());
        boolean sinIdRemoto = suscripcion.getIdSuscripcionMercadopago() == null
                || suscripcion.getIdSuscripcionMercadopago().isBlank();
        return new Detalle(suscripcion.getIdSuscripcion(), suscripcion.getTipoPlan(),
                suscripcion.getEstadoCatalogo() != null ? suscripcion.getEstadoCatalogo().getNombre() : null,
                suscripcion.getEstadoMercadopago(), suscripcion.getFechaInicio(),
                suscripcion.getFechaVencimiento(), suscripcion.getProximoRecargo(), suscripcion.getFechaCancelacion(),
                pagada, vigentes.contains(suscripcion.getIdSuscripcion()),
                (pendiente(suscripcion) && sinIdRemoto) || (pagada && suscripcion.getFechaVencimiento() == null));
    }
}
