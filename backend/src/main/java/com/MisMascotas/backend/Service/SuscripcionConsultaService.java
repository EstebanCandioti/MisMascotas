package com.MisMascotas.backend.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.MisMascotas.backend.DTO.SuscripcionPropiaResponseDTO;
import com.MisMascotas.backend.DTO.SuscripcionPropiaResponseDTO.Detalle;
import com.MisMascotas.backend.Entity.Suscripcion;
import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Repository.PagoRepository;
import com.MisMascotas.backend.Repository.SuscripcionRepository;
import com.MisMascotas.backend.Repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SuscripcionConsultaService {
    private final UsuarioRepository usuarios;
    private final SuscripcionRepository suscripciones;
    private final PagoRepository pagos;
    private final Clock reloj;

    // Desempate explícito: el resultado no depende del orden de las filas de PostgreSQL.
    private static final Comparator<Suscripcion> MAS_RECIENTE = Comparator
            .comparing(Suscripcion::getFechaInicio, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(s -> s.getIdSuscripcion().toString());

    @Transactional(readOnly = true)
    public SuscripcionPropiaResponseDTO consultar(UUID usuarioId) {
        var usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario autenticado no encontrado"));
        var propias = suscripciones.findAllByUsuario_IdUsuario(usuarioId);
        Set<UUID> aprobadas = pagos.buscarSuscripcionesConPagoAprobado(usuarioId);
        Instant ahora = reloj.instant();

        // La cobertura ya pagada no se pierde por cancelación o reemplazo del mandato.
        var cobertura = propias.stream().filter(s -> vigente(s, aprobadas, ahora))
                .max(Comparator.comparing(Suscripcion::getFechaVencimiento).thenComparing(MAS_RECIENTE))
                .orElse(null);
        var principal = cobertura != null ? cobertura : propias.stream()
                .filter(s -> !s.isReemplazada() && !pendiente(s))
                .max(MAS_RECIENTE).orElse(null);
        var intento = propias.stream().filter(s -> !s.isReemplazada() && pendiente(s))
                .max(MAS_RECIENTE).orElse(null);

        return new SuscripcionPropiaResponseDTO(usuario.isEsPremium(), cobertura != null,
                cobertura != null ? cobertura.getFechaVencimiento() : null,
                detalle(principal, aprobadas, ahora), detalle(intento, aprobadas, ahora));
    }

    private boolean pendiente(Suscripcion suscripcion) {
        return suscripcion.getEstadoCatalogo() != null
                && "PENDIENTE".equals(suscripcion.getEstadoCatalogo().getNombre());
    }

    private boolean vigente(Suscripcion suscripcion, Set<UUID> aprobadas, Instant ahora) {
        return aprobadas.contains(suscripcion.getIdSuscripcion())
                && suscripcion.getFechaVencimiento() != null
                && suscripcion.getFechaVencimiento().isAfter(ahora);
    }

    private Detalle detalle(Suscripcion suscripcion, Set<UUID> aprobadas, Instant ahora) {
        if (suscripcion == null) return null;
        boolean pagada = aprobadas.contains(suscripcion.getIdSuscripcion());
        boolean sinIdRemoto = suscripcion.getIdSuscripcionMercadopago() == null
                || suscripcion.getIdSuscripcionMercadopago().isBlank();
        return new Detalle(suscripcion.getIdSuscripcion(), suscripcion.getTipoPlan(),
                suscripcion.getEstadoCatalogo() != null ? suscripcion.getEstadoCatalogo().getNombre() : null,
                suscripcion.getEstadoMercadopago(), suscripcion.getFechaInicio(),
                suscripcion.getFechaVencimiento(), suscripcion.getProximoRecargo(), suscripcion.getFechaCancelacion(),
                pagada, vigente(suscripcion, aprobadas, ahora),
                (pendiente(suscripcion) && sinIdRemoto) || (pagada && suscripcion.getFechaVencimiento() == null));
    }
}
