package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.MisMascotas.backend.Entity.Estado;
import com.MisMascotas.backend.Entity.Suscripcion;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Repository.EstadoRepository;
import com.MisMascotas.backend.Repository.SuscripcionRepository;
import com.MisMascotas.backend.Repository.UsuarioRepository;
import com.MisMascotas.backend.Service.SuscripcionReservaService.EstadoReservaReemplazo;

@ExtendWith(MockitoExtension.class)
class SuscripcionReservaServiceTests {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private EstadoRepository estadoRepository;
    @Mock
    private SuscripcionRepository suscripcionRepository;

    private SuscripcionReservaService service;
    private Usuario usuario;
    private Estado pendiente;
    private Estado cancelada;
    private UUID usuarioId;

    @BeforeEach
    void configurar() {
        service = new SuscripcionReservaService(usuarioRepository, estadoRepository, suscripcionRepository);
        usuarioId = UUID.randomUUID();
        usuario = new Usuario();
        usuario.setIdUsuario(usuarioId);
        usuario.setEmail("qa@example.test");
        pendiente = new Estado();
        pendiente.setIdEstado(41);
        pendiente.setEntidad("SUSCRIPCION");
        pendiente.setNombre("PENDIENTE");
        cancelada = new Estado();
        cancelada.setIdEstado(42);
        cancelada.setEntidad("SUSCRIPCION");
        cancelada.setNombre("CANCELADA");
    }

    @Test
    void bloqueaUsuarioAntesDeBuscarYCrearReserva() {
        when(usuarioRepository.findByIdConBloqueo(usuarioId)).thenReturn(Optional.of(usuario));
        when(estadoRepository.findByEntidadAndNombre("SUSCRIPCION", "PENDIENTE"))
                .thenReturn(Optional.of(pendiente));
        when(suscripcionRepository
                .findFirstByUsuario_IdUsuarioAndTipoPlanAndEstadoCatalogo_IdEstadoOrderByFechaInicioDesc(
                        usuarioId,
                        CatalogoPlanesSuscripcion.PREMIUM_MENSUAL,
                        41))
                .thenReturn(Optional.empty());
        when(suscripcionRepository.saveAndFlush(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocacion -> {
                    Suscripcion suscripcion = invocacion.getArgument(0);
                    suscripcion.setIdSuscripcion(UUID.randomUUID());
                    return suscripcion;
                });

        var reserva = service.reservar(usuarioId, CatalogoPlanesSuscripcion.PREMIUM_MENSUAL);

        assertThat(reserva.nueva()).isTrue();
        assertThat(reserva.idSuscripcion()).isNotNull();
        InOrder orden = inOrder(usuarioRepository, estadoRepository, suscripcionRepository);
        orden.verify(usuarioRepository).findByIdConBloqueo(usuarioId);
        orden.verify(estadoRepository).findByEntidadAndNombre("SUSCRIPCION", "PENDIENTE");
        orden.verify(suscripcionRepository)
                .findFirstByUsuario_IdUsuarioAndTipoPlanAndEstadoCatalogo_IdEstadoOrderByFechaInicioDesc(
                        usuarioId,
                        CatalogoPlanesSuscripcion.PREMIUM_MENSUAL,
                        41);
        orden.verify(suscripcionRepository).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void devuelveReservaExistenteSinCrearOtra() {
        Suscripcion existente = suscripcion(UUID.randomUUID(), null);
        when(usuarioRepository.findByIdConBloqueo(usuarioId)).thenReturn(Optional.of(usuario));
        when(estadoRepository.findByEntidadAndNombre("SUSCRIPCION", "PENDIENTE"))
                .thenReturn(Optional.of(pendiente));
        when(suscripcionRepository
                .findFirstByUsuario_IdUsuarioAndTipoPlanAndEstadoCatalogo_IdEstadoOrderByFechaInicioDesc(
                        usuarioId,
                        CatalogoPlanesSuscripcion.PREMIUM_MENSUAL,
                        41))
                .thenReturn(Optional.of(existente));

        var reserva = service.reservar(usuarioId, CatalogoPlanesSuscripcion.PREMIUM_MENSUAL);

        assertThat(reserva.nueva()).isFalse();
        assertThat(reserva.idSuscripcion()).isEqualTo(existente.getIdSuscripcion());
        verify(suscripcionRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void confirmacionDelIdRemotoEsIdempotente() {
        Suscripcion existente = suscripcion(UUID.randomUUID(), "preapproval-1");
        when(suscripcionRepository.findByIdConBloqueo(existente.getIdSuscripcion()))
                .thenReturn(Optional.of(existente));

        service.confirmarIdMercadoPago(existente.getIdSuscripcion(), "preapproval-1");

        verify(suscripcionRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void buscaReservaPendientePorIdYPropietario() {
        Suscripcion existente = suscripcion(UUID.randomUUID(), "preapproval-1");
        when(suscripcionRepository.findByIdSuscripcionAndUsuario_IdUsuario(
                existente.getIdSuscripcion(),
                usuarioId)).thenReturn(Optional.of(existente));

        var reserva = service.buscarParaContinuar(usuarioId, existente.getIdSuscripcion());

        assertThat(reserva.idSuscripcion()).isEqualTo(existente.getIdSuscripcion());
        assertThat(reserva.idMercadoPago()).isEqualTo("preapproval-1");
        assertThat(reserva.tipoPlan()).isEqualTo(CatalogoPlanesSuscripcion.PREMIUM_MENSUAL);
        assertThat(reserva.pendiente()).isTrue();
    }

    @Test
    void reservaAjenaONoExistenteNoSeExpone() {
        UUID suscripcionId = UUID.randomUUID();
        when(suscripcionRepository.findByIdSuscripcionAndUsuario_IdUsuario(suscripcionId, usuarioId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarParaContinuar(usuarioId, suscripcionId))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void reemplazoCancelaAnteriorYCreaUnaNuevaReservaEnLaMismaTransaccion() {
        Suscripcion anterior = suscripcion(UUID.randomUUID(), "preapproval-anterior");
        prepararReemplazo(anterior);
        when(suscripcionRepository
                .findFirstByUsuario_IdUsuarioAndTipoPlanAndEstadoCatalogo_IdEstadoAndIdSuscripcionNotOrderByFechaInicioDesc(
                        usuarioId,
                        CatalogoPlanesSuscripcion.PREMIUM_MENSUAL,
                        pendiente.getIdEstado(),
                        anterior.getIdSuscripcion()))
                .thenReturn(Optional.empty());
        when(suscripcionRepository.saveAndFlush(any())).thenAnswer(invocacion -> {
            Suscripcion guardada = invocacion.getArgument(0);
            if (guardada.getIdSuscripcion() == null) {
                guardada.setIdSuscripcion(UUID.randomUUID());
            }
            return guardada;
        });

        var resultado = service.reemplazarTrasCancelacionConfirmada(
                usuarioId,
                anterior.getIdSuscripcion(),
                "preapproval-anterior");

        assertThat(resultado.estado()).isEqualTo(EstadoReservaReemplazo.CREADA);
        assertThat(resultado.reserva().nueva()).isTrue();
        assertThat(resultado.reserva().idSuscripcion()).isNotEqualTo(anterior.getIdSuscripcion());
        assertThat(anterior.getEstadoCatalogo()).isSameAs(cancelada);
        assertThat(anterior.getFechaCancelacion()).isNotNull();
        InOrder orden = inOrder(usuarioRepository, suscripcionRepository);
        orden.verify(usuarioRepository).findByIdConBloqueo(usuarioId);
        orden.verify(suscripcionRepository)
                .findByIdSuscripcionAndUsuario_IdUsuario(anterior.getIdSuscripcion(), usuarioId);
        verify(suscripcionRepository).save(same(anterior));
    }

    @Test
    void reemplazoNoCreaOtraReservaSiYaHayUnaPendiente() {
        Suscripcion anterior = suscripcion(UUID.randomUUID(), "preapproval-anterior");
        Suscripcion existente = suscripcion(UUID.randomUUID(), "preapproval-reemplazo");
        prepararReemplazo(anterior);
        when(suscripcionRepository
                .findFirstByUsuario_IdUsuarioAndTipoPlanAndEstadoCatalogo_IdEstadoAndIdSuscripcionNotOrderByFechaInicioDesc(
                        usuarioId,
                        CatalogoPlanesSuscripcion.PREMIUM_MENSUAL,
                        pendiente.getIdEstado(),
                        anterior.getIdSuscripcion()))
                .thenReturn(Optional.of(existente));

        var resultado = service.reemplazarTrasCancelacionConfirmada(
                usuarioId,
                anterior.getIdSuscripcion(),
                "preapproval-anterior");

        assertThat(resultado.estado()).isEqualTo(EstadoReservaReemplazo.INTENTO_PENDIENTE);
        assertThat(resultado.reserva().idSuscripcion()).isEqualTo(existente.getIdSuscripcion());
        assertThat(anterior.getEstadoCatalogo()).isSameAs(cancelada);
        verify(suscripcionRepository).saveAndFlush(same(anterior));
        verify(suscripcionRepository, never()).save(any());
    }

    @Test
    void reemplazoRepetidoSobreIntentoCanceladoNoCreaOtraReserva() {
        Suscripcion anterior = suscripcion(UUID.randomUUID(), "preapproval-anterior");
        anterior.setEstadoCatalogo(cancelada);
        prepararReemplazo(anterior);

        var resultado = service.reemplazarTrasCancelacionConfirmada(
                usuarioId,
                anterior.getIdSuscripcion(),
                "preapproval-anterior");

        assertThat(resultado.estado()).isEqualTo(EstadoReservaReemplazo.YA_PROCESADO);
        assertThat(resultado.reserva()).isNull();
        verify(suscripcionRepository, never()).save(any());
        verify(suscripcionRepository, never()).saveAndFlush(any());
    }

    private void prepararReemplazo(Suscripcion anterior) {
        when(usuarioRepository.findByIdConBloqueo(usuarioId)).thenReturn(Optional.of(usuario));
        when(suscripcionRepository.findByIdSuscripcionAndUsuario_IdUsuario(
                anterior.getIdSuscripcion(),
                usuarioId)).thenReturn(Optional.of(anterior));
        when(estadoRepository.findByEntidadAndNombre("SUSCRIPCION", "PENDIENTE"))
                .thenReturn(Optional.of(pendiente));
        when(estadoRepository.findByEntidadAndNombre("SUSCRIPCION", "CANCELADA"))
                .thenReturn(Optional.of(cancelada));
    }

    private Suscripcion suscripcion(UUID id, String idMercadoPago) {
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setIdSuscripcion(id);
        suscripcion.setUsuario(usuario);
        suscripcion.setTipoPlan(CatalogoPlanesSuscripcion.PREMIUM_MENSUAL);
        suscripcion.setEstadoCatalogo(pendiente);
        suscripcion.setIdSuscripcionMercadopago(idMercadoPago);
        return suscripcion;
    }
}
