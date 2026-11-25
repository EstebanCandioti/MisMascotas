package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.MisMascotas.backend.Entity.*;
import com.MisMascotas.backend.Integration.MercadoPagoGateway.PagoConsultado;
import com.MisMascotas.backend.Repository.*;
import com.MisMascotas.backend.Service.SuscripcionCobroTransaccionalService.*;

@ExtendWith(MockitoExtension.class)
class SuscripcionCobroTransaccionalServiceTests {

    @Mock private UsuarioRepository usuarios;
    @Mock private SuscripcionRepository suscripciones;
    @Mock private PagoRepository pagos;
    @Mock private EstadoRepository estados;
    @Mock private EventoAuditoriaPendienteService auditoria;
    private SuscripcionCobroTransaccionalService service;
    private Usuario usuario;
    private Suscripcion suscripcion;
    private ContextoCobro contexto;
    private PagoConsultado pago;
    private Pago pagoGuardado;
    private final Instant fecha = Instant.parse("2026-01-10T12:00:00Z");
    private final Instant vencimiento = Instant.parse("2026-02-10T12:00:00Z");

    @BeforeEach
    void preparar() {
        service = new SuscripcionCobroTransaccionalService(usuarios, suscripciones, pagos, estados, auditoria,
                Clock.fixed(fecha, ZoneOffset.UTC));
        usuario = new Usuario();
        usuario.setIdUsuario(UUID.randomUUID());
        suscripcion = new Suscripcion();
        suscripcion.setIdSuscripcion(UUID.randomUUID());
        suscripcion.setUsuario(usuario);
        suscripcion.setTipoPlan("PREMIUM_MENSUAL");
        suscripcion.setIdSuscripcionMercadopago("remoto");
        suscripcion.setEstadoCatalogo(estado("SUSCRIPCION", "PENDIENTE"));
        suscripcion.setFechaInicio(fecha.minusSeconds(1000));
        contexto = new ContextoCobro(suscripcion.getIdSuscripcion(), "PREMIUM_MENSUAL", "remoto", true, null);
        pago = new PagoConsultado("999", "approved", new BigDecimal("2500.00"), "ARS", fecha);
        when(usuarios.findByIdConBloqueo(usuario.getIdUsuario())).thenReturn(Optional.of(usuario));
        when(suscripciones.findByIdSuscripcionAndUsuario_IdUsuario(suscripcion.getIdSuscripcion(), usuario.getIdUsuario()))
                .thenReturn(Optional.of(suscripcion));
    }

    @Test
    void guardaPagoActivaPremiumYEncolaDosSnapshotsAcotados() {
        prepararAlta();
        var resultado = confirmar();

        assertThat(resultado.estado()).isEqualTo(EstadoCobro.ACTIVADA);
        assertThat(resultado.idPagoMercadoPago()).isEqualTo("999");
        assertThat(resultado.fechaPago()).isEqualTo(fecha);
        assertThat(suscripcion.getEstadoCatalogo().getNombre()).isEqualTo("ACTIVA");
        assertThat(suscripcion.getFechaInicio()).isEqualTo(fecha.minusSeconds(1000));
        assertThat(suscripcion.getFechaVencimiento()).isEqualTo(vencimiento);
        assertThat(usuario.isEsPremium()).isTrue();
        var orden = inOrder(usuarios, suscripciones, pagos, auditoria);
        orden.verify(usuarios).findByIdConBloqueo(usuario.getIdUsuario());
        orden.verify(suscripciones).findByIdSuscripcionAndUsuario_IdUsuario(suscripcion.getIdSuscripcion(), usuario.getIdUsuario());
        orden.verify(pagos).saveAndFlush(any());
        orden.verify(auditoria).encolar(eq(usuario.getIdUsuario()), eq("pago"), eq(TipoAccionAuditoria.CREATE),
                eq(resultado.idPago()), isNull(), any(SnapshotPago.class));
        orden.verify(auditoria).encolar(eq(usuario.getIdUsuario()), eq("suscripcion"), eq(TipoAccionAuditoria.ACTIVATE),
                eq(suscripcion.getIdSuscripcion()), any(SnapshotSuscripcion.class), any(SnapshotSuscripcion.class));
    }

    @Test
    void repeticionLuegoDeLaPrimeraTransaccionDevuelveElMismoPagoYVencimiento() {
        prepararAlta();
        var primera = confirmar();
        var repetida = confirmar();

        assertThat(repetida.estado()).isEqualTo(EstadoCobro.YA_CONFIRMADA);
        assertThat(repetida.idPago()).isEqualTo(primera.idPago());
        assertThat(repetida.fechaVencimiento()).isEqualTo(primera.fechaVencimiento());
        verify(pagos, times(1)).saveAndFlush(any());
        verify(auditoria, times(2)).encolar(any(), any(), any(), any(), any(), any());
    }

    @Test
    void reemplazoQueGanoLaCarreraImpideLaActivacion() {
        suscripcion.setEstadoCatalogo(estado("SUSCRIPCION", "CANCELADA"));
        assertConciliacion();
    }

    @Test
    void identidadRemotaQueCambioImpideLaActivacion() {
        suscripcion.setIdSuscripcionMercadopago("otro-remoto");
        assertConciliacion();
    }

    @Test
    void pagoAsociadoAOtraSuscripcionRequiereConciliacion() {
        Pago ajeno = new Pago();
        ajeno.setSuscripcion(new Suscripcion());
        when(pagos.findByIdTransaccionMercadopago("999")).thenReturn(Optional.of(ajeno));
        assertConciliacion();
    }

    @Test
    void falloDeAuditoriaSePropagaParaQueElInterceptorReviertaLaTransaccion() {
        prepararAlta();
        doAnswer(invocacion -> {
            if ("suscripcion".equals(invocacion.getArgument(1))) {
                throw new IllegalStateException("fallo al encolar");
            }
            return UUID.randomUUID();
        }).when(auditoria).encolar(any(), any(), any(), any(), any(), any());
        assertThatThrownBy(this::confirmar).isInstanceOf(IllegalStateException.class);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(longs = {0, 1})
    void primerCobroConfirmadoEnODespuesDelVencimientoNoConcedeUnaExcepcionManual(long segundos) {
        prepararAlta();
        usuario.setEsPremium(true);
        service = new SuscripcionCobroTransaccionalService(usuarios, suscripciones, pagos, estados, auditoria,
                Clock.fixed(vencimiento.plusSeconds(segundos), ZoneOffset.UTC));
        assertThat(confirmar().estado()).isEqualTo(EstadoCobro.ACTIVADA);
        assertThat(usuario.isEsPremium()).isFalse();
        assertThat(suscripcion.getFechaVencimiento()).isEqualTo(vencimiento);
        verify(auditoria, times(2)).encolar(any(), any(), any(), any(), any(), any());
    }

    @Test void otraCoberturaPagaConservaPremiumAlRegistrarUnPrimerCobroHistorico() {
        prepararAlta();
        var otra = new Suscripcion(); otra.setUsuario(usuario); otra.setIdSuscripcion(UUID.randomUUID());
        otra.setEstadoCatalogo(estado("SUSCRIPCION", "CANCELADA"));
        otra.setFechaVencimiento(vencimiento.plusSeconds(60));
        when(suscripciones.findAllByUsuario_IdUsuario(usuario.getIdUsuario())).thenReturn(List.of(suscripcion, otra));
        when(pagos.buscarSuscripcionesConPagoAprobado(usuario.getIdUsuario()))
                .thenReturn(Set.of(suscripcion.getIdSuscripcion(), otra.getIdSuscripcion()));
        service = new SuscripcionCobroTransaccionalService(usuarios, suscripciones, pagos, estados, auditoria,
                Clock.fixed(vencimiento, ZoneOffset.UTC));
        confirmar();
        assertThat(usuario.isEsPremium()).isTrue();
    }

    private void assertConciliacion() {
        assertThat(confirmar().estado()).isEqualTo(EstadoCobro.PENDIENTE_CONCILIACION);
        verify(pagos, never()).saveAndFlush(any());
        verifyNoInteractions(auditoria);
        assertThat(usuario.isEsPremium()).isFalse();
    }

    private ResultadoCobro confirmar() {
        return service.confirmar(usuario.getIdUsuario(), contexto, pago, vencimiento);
    }

    private void prepararAlta() {
        when(suscripciones.findAllByUsuario_IdUsuario(usuario.getIdUsuario())).thenReturn(List.of(suscripcion));
        when(pagos.buscarSuscripcionesConPagoAprobado(usuario.getIdUsuario())).thenReturn(Set.of(suscripcion.getIdSuscripcion()));
        when(pagos.findBySuscripcion_IdSuscripcionOrderByFechaPagoAsc(suscripcion.getIdSuscripcion()))
                .thenAnswer(invocacion -> pagoGuardado == null ? List.of() : List.of(pagoGuardado));
        when(estados.findByEntidadAndNombre("PAGO", "APROBADO"))
                .thenReturn(Optional.of(estado("PAGO", "APROBADO")));
        when(estados.findByEntidadAndNombre("SUSCRIPCION", "ACTIVA"))
                .thenReturn(Optional.of(estado("SUSCRIPCION", "ACTIVA")));
        when(pagos.saveAndFlush(any())).thenAnswer(invocacion -> {
            Pago guardado = invocacion.getArgument(0);
            guardado.setIdPago(UUID.randomUUID());
            pagoGuardado = guardado;
            return guardado;
        });
    }

    private Estado estado(String entidad, String nombre) {
        Estado estado = new Estado();
        estado.setEntidad(entidad);
        estado.setNombre(nombre);
        return estado;
    }
}
