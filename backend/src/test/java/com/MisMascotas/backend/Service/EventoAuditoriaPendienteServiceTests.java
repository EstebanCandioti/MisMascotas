package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.MisMascotas.backend.Entity.EventoAuditoriaPendiente;
import com.MisMascotas.backend.Entity.TipoAccionAuditoria;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Repository.EventoAuditoriaPendienteRepository;
import com.MisMascotas.backend.Repository.UsuarioRepository;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class EventoAuditoriaPendienteServiceTests {

    @Mock
    private EventoAuditoriaPendienteRepository eventoRepository;
    @Mock
    private UsuarioRepository usuarioRepository;

    private EventoAuditoriaPendienteService service;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        service = new EventoAuditoriaPendienteService(eventoRepository, usuarioRepository, jsonMapper);
    }

    @Test
    void encolarRecibeActorYSnapshotsExplicitamente() {
        UUID actorId = UUID.randomUUID();
        UUID mascotaId = UUID.randomUUID();
        UUID eventoId = UUID.randomUUID();
        Usuario actor = new Usuario();
        actor.setIdUsuario(actorId);
        when(usuarioRepository.getReferenceById(actorId)).thenReturn(actor);
        when(eventoRepository.save(org.mockito.ArgumentMatchers.any(EventoAuditoriaPendiente.class)))
                .thenAnswer(invocacion -> {
                    EventoAuditoriaPendiente evento = invocacion.getArgument(0);
                    evento.setIdEvento(eventoId);
                    return evento;
                });

        UUID resultado = service.encolar(
                actorId,
                "mascota",
                TipoAccionAuditoria.UPDATE,
                mascotaId,
                new SnapshotMascota("Antes", LocalDate.of(2020, 1, 1)),
                new SnapshotMascota("Despues", LocalDate.of(2020, 1, 1)));

        ArgumentCaptor<EventoAuditoriaPendiente> captor = ArgumentCaptor.forClass(EventoAuditoriaPendiente.class);
        org.mockito.Mockito.verify(eventoRepository).save(captor.capture());
        EventoAuditoriaPendiente guardado = captor.getValue();
        assertThat(resultado).isEqualTo(eventoId);
        assertThat(guardado.getActor()).isSameAs(actor);
        assertThat(guardado.getIdEntidad()).isEqualTo(mascotaId);
        assertThat(guardado.getValorAnterior()).contains("Antes", "2020-01-01");
        assertThat(guardado.getValorNuevo()).contains("Despues", "2020-01-01");
    }

    @Test
    void encolarExigeUnaTransaccionActiva() throws Exception {
        Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(
                EventoAuditoriaPendienteService.class.getMethod(
                        "encolar",
                        UUID.class,
                        String.class,
                        TipoAccionAuditoria.class,
                        UUID.class,
                        Object.class,
                        Object.class),
                Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.MANDATORY);
    }

    @Test
    void unDatoNoSerializableImpideEncolar() {
        assertThrows(IllegalStateException.class, () -> service.encolar(
                null,
                "mascota",
                TipoAccionAuditoria.UPDATE,
                UUID.randomUUID(),
                new Object() {
                    public Object getRecursivo() {
                        return this;
                    }
                },
                null));
    }

    private record SnapshotMascota(String nombre, LocalDate fechaNacimiento) {
    }
}
