package com.MisMascotas.backend.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.MisMascotas.backend.Entity.EventoAuditoriaPendiente;
import com.MisMascotas.backend.Entity.LogAuditoria;
import com.MisMascotas.backend.Repository.EventoAuditoriaPendienteRepository;
import com.MisMascotas.backend.Repository.LogAuditoriaRepository;

@Service
public class ProcesadorEventoAuditoriaService {

    private static final int LONGITUD_MAXIMA_ERROR = 4000;

    private final EventoAuditoriaPendienteRepository eventoRepository;
    private final LogAuditoriaRepository logAuditoriaRepository;

    public ProcesadorEventoAuditoriaService(
            EventoAuditoriaPendienteRepository eventoRepository,
            LogAuditoriaRepository logAuditoriaRepository) {
        this.eventoRepository = eventoRepository;
        this.logAuditoriaRepository = logAuditoriaRepository;
    }

    @Transactional(readOnly = true)
    public List<UUID> buscarIdsPendientes(int limite) {
        return eventoRepository.buscarIdsPendientes(PageRequest.of(0, limite));
    }

    /**
     * El log y la marca de procesamiento se confirman juntos. SKIP LOCKED evita que
     * dos instancias procesen el mismo evento al mismo tiempo.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResultadoProcesamiento procesar(UUID idEvento) {
        EventoAuditoriaPendiente evento = eventoRepository.buscarPendienteParaProcesar(idEvento)
                .orElse(null);
        if (evento == null) {
            return ResultadoProcesamiento.OMITIDO;
        }

        if (!logAuditoriaRepository.existsByIdEventoPendiente(idEvento)) {
            LogAuditoria log = LogAuditoria.builder()
                    .actor(evento.getActor())
                    .tipoAccion(evento.getTipoAccion())
                    .entidadAfectada(evento.getEntidadAfectada())
                    .idEntidad(evento.getIdEntidad())
                    .valorAnterior(evento.getValorAnterior())
                    .valorNuevo(evento.getValorNuevo())
                    .fechaHora(evento.getFechaEvento())
                    .idEventoPendiente(idEvento)
                    .build();
            logAuditoriaRepository.saveAndFlush(log);
        }

        evento.marcarProcesado(Instant.now());
        eventoRepository.saveAndFlush(evento);
        return ResultadoProcesamiento.PROCESADO;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int registrarFallo(UUID idEvento, Throwable error) {
        EventoAuditoriaPendiente evento = eventoRepository.buscarPendienteParaProcesar(idEvento)
                .orElse(null);
        if (evento == null) {
            return 0;
        }

        evento.registrarFallo(Instant.now(), resumir(error));
        eventoRepository.saveAndFlush(evento);
        return evento.getIntentosProcesamiento();
    }

    private String resumir(Throwable error) {
        String tipo = error == null ? "Error desconocido" : error.getClass().getSimpleName();
        String mensaje = error == null ? null : error.getMessage();
        String detalle = mensaje == null || mensaje.isBlank() ? tipo : tipo + ": " + mensaje;
        return detalle.length() <= LONGITUD_MAXIMA_ERROR
                ? detalle
                : detalle.substring(0, LONGITUD_MAXIMA_ERROR);
    }

    public enum ResultadoProcesamiento {
        PROCESADO,
        OMITIDO
    }
}
