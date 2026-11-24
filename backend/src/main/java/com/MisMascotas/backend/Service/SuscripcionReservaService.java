package com.MisMascotas.backend.Service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.MisMascotas.backend.Entity.Estado;
import com.MisMascotas.backend.Entity.Suscripcion;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Exception.EstadoNoConfiguradoException;
import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Exception.ReglaNegocioException;
import com.MisMascotas.backend.Repository.EstadoRepository;
import com.MisMascotas.backend.Repository.SuscripcionRepository;
import com.MisMascotas.backend.Repository.UsuarioRepository;

@Service
public class SuscripcionReservaService {

    private static final String ENTIDAD_SUSCRIPCION = "SUSCRIPCION";
    private static final String ESTADO_PENDIENTE = "PENDIENTE";

    private final UsuarioRepository usuarioRepository;
    private final EstadoRepository estadoRepository;
    private final SuscripcionRepository suscripcionRepository;

    public SuscripcionReservaService(
            UsuarioRepository usuarioRepository,
            EstadoRepository estadoRepository,
            SuscripcionRepository suscripcionRepository) {
        this.usuarioRepository = usuarioRepository;
        this.estadoRepository = estadoRepository;
        this.suscripcionRepository = suscripcionRepository;
    }

    @Transactional
    public ReservaSuscripcion reservar(UUID usuarioId, String tipoPlan) {
        Usuario usuario = usuarioRepository.findByIdConBloqueo(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Usuario no encontrado con ID: " + usuarioId));
        Estado pendiente = estadoRepository.findByEntidadAndNombre(
                        ENTIDAD_SUSCRIPCION,
                        ESTADO_PENDIENTE)
                .orElseThrow(() -> new EstadoNoConfiguradoException(
                        ENTIDAD_SUSCRIPCION,
                        ESTADO_PENDIENTE));

        var existente = suscripcionRepository
                .findFirstByUsuario_IdUsuarioAndTipoPlanAndEstadoCatalogo_IdEstadoOrderByFechaInicioDesc(
                        usuarioId,
                        tipoPlan,
                        pendiente.getIdEstado());
        if (existente.isPresent()) {
            return mapearReserva(existente.get(), false);
        }

        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setUsuario(usuario);
        suscripcion.setTipoPlan(tipoPlan);
        suscripcion.setEstadoCatalogo(pendiente);
        suscripcion.setFechaInicio(Instant.now());

        Suscripcion guardada = suscripcionRepository.saveAndFlush(suscripcion);
        return mapearReserva(guardada, true);
    }

    @Transactional
    public void confirmarIdMercadoPago(UUID suscripcionId, String idMercadoPago) {
        if (idMercadoPago == null || idMercadoPago.isBlank() || idMercadoPago.length() > 150) {
            throw new ReglaNegocioException("El identificador de Mercado Pago no es valido");
        }

        Suscripcion suscripcion = suscripcionRepository.findByIdConBloqueo(suscripcionId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Suscripcion no encontrada con ID: " + suscripcionId));

        if (suscripcion.getIdSuscripcionMercadopago() != null) {
            if (!suscripcion.getIdSuscripcionMercadopago().equals(idMercadoPago)) {
                throw new ReglaNegocioException(
                        "La suscripcion ya tiene otro identificador de Mercado Pago");
            }
            return;
        }

        suscripcion.setIdSuscripcionMercadopago(idMercadoPago);
        suscripcionRepository.saveAndFlush(suscripcion);
    }

    @Transactional(readOnly = true)
    public ReservaParaContinuar buscarParaContinuar(UUID usuarioId, UUID suscripcionId) {
        Suscripcion suscripcion = suscripcionRepository
                .findByIdSuscripcionAndUsuario_IdUsuario(suscripcionId, usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Suscripcion no encontrada con ID: " + suscripcionId));

        Estado estado = suscripcion.getEstadoCatalogo();
        boolean pendiente = estado != null
                && ENTIDAD_SUSCRIPCION.equals(estado.getEntidad())
                && ESTADO_PENDIENTE.equals(estado.getNombre());

        return new ReservaParaContinuar(
                suscripcion.getIdSuscripcion(),
                suscripcion.getTipoPlan(),
                suscripcion.getIdSuscripcionMercadopago(),
                pendiente);
    }

    @Transactional
    public ResultadoReservaReemplazo reemplazarTrasCancelacionConfirmada(
            UUID usuarioId,
            UUID suscripcionId,
            String idMercadoPagoEsperado) {
        Usuario usuario = usuarioRepository.findByIdConBloqueo(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Usuario no encontrado con ID: " + usuarioId));
        Suscripcion anterior = suscripcionRepository
                .findByIdSuscripcionAndUsuario_IdUsuario(suscripcionId, usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Suscripcion no encontrada con ID: " + suscripcionId));

        Estado pendiente = estadoRepository.findByEntidadAndNombre(
                        ENTIDAD_SUSCRIPCION,
                        ESTADO_PENDIENTE)
                .orElseThrow(() -> new EstadoNoConfiguradoException(
                        ENTIDAD_SUSCRIPCION,
                        ESTADO_PENDIENTE));
        Estado cancelada = estadoRepository.findByEntidadAndNombre(
                        ENTIDAD_SUSCRIPCION,
                        "CANCELADA")
                .orElseThrow(() -> new EstadoNoConfiguradoException(
                        ENTIDAD_SUSCRIPCION,
                        "CANCELADA"));

        if (!esEstado(anterior, pendiente)) {
            return new ResultadoReservaReemplazo(EstadoReservaReemplazo.YA_PROCESADO, null);
        }
        if (idMercadoPagoEsperado == null
                || !idMercadoPagoEsperado.equals(anterior.getIdSuscripcionMercadopago())) {
            throw new ReglaNegocioException(
                    "La suscripcion ya no conserva el identificador remoto verificado");
        }

        var otroPendiente = suscripcionRepository
                .findFirstByUsuario_IdUsuarioAndTipoPlanAndEstadoCatalogo_IdEstadoAndIdSuscripcionNotOrderByFechaInicioDesc(
                        usuarioId,
                        anterior.getTipoPlan(),
                        pendiente.getIdEstado(),
                        anterior.getIdSuscripcion());

        anterior.setReemplazada(true);
        anterior.setEstadoCatalogo(cancelada);
        anterior.setFechaCancelacion(Instant.now());
        if (otroPendiente.isPresent()) {
            suscripcionRepository.saveAndFlush(anterior);
            return new ResultadoReservaReemplazo(
                    EstadoReservaReemplazo.INTENTO_PENDIENTE,
                    mapearReserva(otroPendiente.get(), false));
        }

        Suscripcion reemplazo = new Suscripcion();
        reemplazo.setUsuario(usuario);
        reemplazo.setTipoPlan(anterior.getTipoPlan());
        reemplazo.setEstadoCatalogo(pendiente);
        reemplazo.setFechaInicio(Instant.now());

        suscripcionRepository.save(anterior);
        Suscripcion guardada = suscripcionRepository.saveAndFlush(reemplazo);
        return new ResultadoReservaReemplazo(
                EstadoReservaReemplazo.CREADA,
                mapearReserva(guardada, true));
    }

    private boolean esEstado(Suscripcion suscripcion, Estado estado) {
        return suscripcion.getEstadoCatalogo() != null
                && suscripcion.getEstadoCatalogo().getIdEstado().equals(estado.getIdEstado());
    }

    private ReservaSuscripcion mapearReserva(Suscripcion suscripcion, boolean nueva) {
        return new ReservaSuscripcion(
                suscripcion.getIdSuscripcion(),
                suscripcion.getUsuario().getEmail(),
                suscripcion.getTipoPlan(),
                suscripcion.getIdSuscripcionMercadopago(),
                nueva);
    }

    public record ReservaSuscripcion(
            UUID idSuscripcion,
            String emailUsuario,
            String tipoPlan,
            String idMercadoPago,
            boolean nueva) {
    }

    public record ReservaParaContinuar(
            UUID idSuscripcion,
            String tipoPlan,
            String idMercadoPago,
            boolean pendiente) {
    }

    public enum EstadoReservaReemplazo {
        CREADA,
        INTENTO_PENDIENTE,
        YA_PROCESADO
    }

    public record ResultadoReservaReemplazo(
            EstadoReservaReemplazo estado,
            ReservaSuscripcion reserva) {
    }
}
