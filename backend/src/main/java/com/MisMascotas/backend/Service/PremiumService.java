package com.MisMascotas.backend.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.MisMascotas.backend.Entity.Suscripcion;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Exception.PremiumRequeridoException;
import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Repository.PagoRepository;
import com.MisMascotas.backend.Repository.SuscripcionRepository;
import com.MisMascotas.backend.Repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;

/** Política local de beneficios; no modifica pagos, suscripciones ni usuarios. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PremiumService {
    private final UsuarioRepository usuarios;
    private final SuscripcionRepository suscripciones;
    private final PagoRepository pagos;
    private final Clock reloj;

    public boolean esPremium(UUID propietarioId) {
        return consultar(propietarioId).esPremium();
    }

    public void exigirPremium(UUID propietarioId) {
        if (!esPremium(propietarioId)) throw new PremiumRequeridoException();
    }

    public EstadoPremium consultar(UUID propietarioId) {
        return consultar(propietarioId, false).estado();
    }

    // Vista interna para mapear detalles sin repetir consultas ni evaluar otro instante.
    ConsultaPremium consultarSuscripciones(UUID propietarioId) {
        return consultar(propietarioId, true);
    }

    private ConsultaPremium consultar(UUID propietarioId, boolean incluirSuscripciones) {
        var propietario = usuarios.findById(propietarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Propietario no encontrado"));
        var aprobadas = pagos.buscarSuscripcionesConPagoAprobado(propietarioId);
        var propias = incluirSuscripciones || !aprobadas.isEmpty()
                ? suscripciones.findAllByUsuario_IdUsuario(propietarioId) : List.<Suscripcion>of();
        var evaluacion = evaluar(propietario, propias, aprobadas, reloj.instant());
        return new ConsultaPremium(evaluacion.estado(), List.copyOf(propias), Set.copyOf(aprobadas),
                evaluacion.conCoberturaVigente());
    }

    /** Evaluación pura: permite reutilizar las entidades ya cargadas bajo bloqueo transaccional. */
    static EvaluacionPremium evaluar(Usuario propietario, List<Suscripcion> propias, Set<UUID> aprobadas, Instant ahora) {
        var vigentes = propias.stream()
                .filter(s -> tieneCoberturaVigente(s, aprobadas.contains(s.getIdSuscripcion()), ahora))
                .map(Suscripcion::getIdSuscripcion).collect(Collectors.toUnmodifiableSet());
        var sinVencimiento = propias.stream()
                .filter(s -> aprobadas.contains(s.getIdSuscripcion()) && s.getFechaVencimiento() == null)
                .map(Suscripcion::getIdSuscripcion).collect(Collectors.toUnmodifiableSet());
        Instant vencimiento = propias.stream()
                .filter(s -> vigentes.contains(s.getIdSuscripcion()))
                .map(s -> s.getFechaVencimiento())
                .max(Instant::compareTo).orElse(null);
        // Compatibilidad temporal acordada solo si no existen pagos aprobados históricos.
        boolean manual = aprobadas.isEmpty() && propietario.isEsPremium();
        var estado = new EstadoPremium(manual || vencimiento != null, vencimiento != null, vencimiento, manual);
        return new EvaluacionPremium(estado, vigentes, sinVencimiento);
    }

    static boolean tieneCoberturaVigente(Suscripcion suscripcion, boolean pagoAprobado, Instant ahora) {
        return pagoAprobado && suscripcion.getFechaVencimiento() != null
                && suscripcion.getFechaVencimiento().isAfter(ahora);
    }

    public record EstadoPremium(boolean esPremium, boolean coberturaPagadaVigente,
            Instant fechaFinCobertura, boolean premiumManual) {}

    record ConsultaPremium(EstadoPremium estado, List<Suscripcion> suscripciones,
            Set<UUID> conPagoAprobado, Set<UUID> conCoberturaVigente) {}

    record EvaluacionPremium(EstadoPremium estado, Set<UUID> conCoberturaVigente,
            Set<UUID> pagadasSinVencimiento) {}
}
