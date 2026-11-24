package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.MisMascotas.backend.Entity.EventoAuditoriaPendiente;
import com.MisMascotas.backend.Entity.LogAuditoria;
import com.MisMascotas.backend.Entity.TipoAccionAuditoria;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Repository.EventoAuditoriaPendienteRepository;
import com.MisMascotas.backend.Repository.LogAuditoriaRepository;

@ExtendWith(MockitoExtension.class)
class ProcesadorEventoAuditoriaServiceTests {

    @Mock
    private EventoAuditoriaPendienteRepository eventoRepository;
    @Mock
    private LogAuditoriaRepository logAuditoriaRepository;

    private ProcesadorEventoAuditoriaService service;
    private UUID eventoId;
    private EventoAuditoriaPendiente evento;

    @BeforeEach
    void setUp() {
        service = new ProcesadorEventoAuditoriaService(eventoRepository, logAuditoriaRepository);
        eventoId = UUID.randomUUID();
        Usuario actor = new Usuario();
        actor.setIdUsuario(UUID.randomUUID());
        evento = EventoAuditoriaPendiente.builder()
                .idEvento(eventoId)
                .actor(actor)
                .entidadAfectada("mascota")
                .tipoAccion(TipoAccionAuditoria.UPDATE)
                .idEntidad(UUID.randomUUID())
                .valorAnterior("{\"nombre\":\"Antes\"}")
                .valorNuevo("{\"nombre\":\"Despues\"}")
                .fechaEvento(Instant.parse("2026-11-12T04:00:00Z"))
                .build();
        when(eventoRepository.buscarPendienteParaProcesar(eventoId)).thenReturn(Optional.of(evento));
    }

    @Test
    void creaUnLogConElIdDelEventoYMarcaProcesado() {
        when(logAuditoriaRepository.existsByIdEventoPendiente(eventoId)).thenReturn(false);

        var resultado = service.procesar(eventoId);

        ArgumentCaptor<LogAuditoria> captor = ArgumentCaptor.forClass(LogAuditoria.class);
        verify(logAuditoriaRepository).saveAndFlush(captor.capture());
        LogAuditoria log = captor.getValue();
        assertThat(resultado).isEqualTo(ProcesadorEventoAuditoriaService.ResultadoProcesamiento.PROCESADO);
        assertThat(log.getIdEventoPendiente()).isEqualTo(eventoId);
        assertThat(log.getActor()).isSameAs(evento.getActor());
        assertThat(log.getFechaHora()).isEqualTo(evento.getFechaEvento());
        assertThat(log.getValorAnterior()).isEqualTo(evento.getValorAnterior());
        assertThat(evento.getProcesadoEn()).isNotNull();
        assertThat(evento.getIntentosProcesamiento()).isEqualTo(1);
        verify(eventoRepository).saveAndFlush(evento);
    }

    @Test
    void unLogYaExistenteSoloCompletaLaMarcaSinDuplicarlo() {
        when(logAuditoriaRepository.existsByIdEventoPendiente(eventoId)).thenReturn(true);

        service.procesar(eventoId);

        verify(logAuditoriaRepository, never()).saveAndFlush(any());
        assertThat(evento.getProcesadoEn()).isNotNull();
        verify(eventoRepository).saveAndFlush(evento);
    }

    @Test
    void siFallaLaEscrituraNoMarcaElEventoComoProcesado() {
        when(logAuditoriaRepository.existsByIdEventoPendiente(eventoId)).thenReturn(false);
        when(logAuditoriaRepository.saveAndFlush(any())).thenThrow(new IllegalStateException("base no disponible"));

        assertThrows(IllegalStateException.class, () -> service.procesar(eventoId));

        assertThat(evento.getProcesadoEn()).isNull();
        assertThat(evento.getIntentosProcesamiento()).isZero();
        verify(eventoRepository, never()).saveAndFlush(any());
    }

    @Test
    void registraElFalloParaRevisionYPermiteReintentar() {
        int intentos = service.registrarFallo(eventoId, new IllegalStateException("base no disponible"));

        assertThat(intentos).isEqualTo(1);
        assertThat(evento.getProcesadoEn()).isNull();
        assertThat(evento.getUltimoIntentoEn()).isNotNull();
        assertThat(evento.getUltimoError()).contains("IllegalStateException", "base no disponible");
        verify(eventoRepository).saveAndFlush(evento);
    }
}
