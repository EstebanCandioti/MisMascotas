package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import com.MisMascotas.backend.Entity.*;
import com.MisMascotas.backend.Repository.*;
import com.MisMascotas.backend.Integration.MercadoPagoGateway.*;

@ExtendWith(MockitoExtension.class)
class SuscripcionCicloTransaccionalServiceTests {
    @Mock UsuarioRepository usuarios; @Mock SuscripcionRepository suscripciones; @Mock PagoRepository pagos;
    @Mock EstadoRepository estados; @Mock EventoAuditoriaPendienteService auditoria;
    SuscripcionCicloTransaccionalService service;
    final UUID uid=UUID.randomUUID(), sid=UUID.randomUUID();
    final Instant aprobacion=Instant.parse("2026-01-31T20:00:00Z");
    Suscripcion s; Usuario u;
    @BeforeEach void preparar() {
        service=new SuscripcionCicloTransaccionalService(usuarios,suscripciones,pagos,estados,auditoria,Clock.fixed(aprobacion,ZoneOffset.UTC));
        u=new Usuario();u.setIdUsuario(uid);
        s=new Suscripcion();s.setIdSuscripcion(sid);s.setUsuario(u);s.setTipoPlan("PREMIUM_MENSUAL");s.setIdSuscripcionMercadopago("mp");s.setEstadoCatalogo(estado("PENDIENTE"));
        when(usuarios.findByIdConBloqueo(uid)).thenReturn(Optional.of(u));
        when(suscripciones.findByIdSuscripcionAndUsuario_IdUsuario(sid,uid)).thenReturn(Optional.of(s));
        lenient().when(estados.findByEntidadAndNombre(anyString(),anyString())).thenAnswer(i->{var e=estado(i.getArgument(1)); e.setEntidad(i.getArgument(0));return Optional.of(e);});
        lenient().when(pagos.saveAndFlush(any())).thenAnswer(i->{Pago p=i.getArgument(0);if(p.getIdPago()==null)p.setIdPago(UUID.randomUUID());return p;});
        lenient().when(suscripciones.findAllByUsuario_IdUsuario(uid)).thenReturn(List.of(s));
        lenient().when(pagos.buscarSuscripcionesConPagoAprobado(uid)).thenReturn(Set.of(sid));
    }
    @Test void primerCobroGuardaFacturaPagoYMesCalendarioConDosEventos() {
        assertThat(registrar("f","p",aprobacion).motivo()).isEqualTo("ACTIVADA");
        assertThat(s.getFechaVencimiento()).isEqualTo(Instant.parse("2026-02-28T20:00:00Z"));
        assertThat(s.getProximoRecargo()).isEqualTo(Instant.parse("2026-03-01T20:00:00Z"));
        assertThat(u.isEsPremium()).isTrue();
        var c=ArgumentCaptor.forClass(Pago.class);verify(pagos).saveAndFlush(c.capture());assertThat(c.getValue().getIdFacturaMercadopago()).isEqualTo("f");
        verify(auditoria,times(2)).encolar(isNull(),anyString(),any(),any(),any(),any());
        var orden=inOrder(usuarios,suscripciones);orden.verify(usuarios).findByIdConBloqueo(uid);orden.verify(suscripciones).findByIdSuscripcionAndUsuario_IdUsuario(sid,uid);
    }
    @Test void renovarAntesDeVencerConservaTodosLosDiasPagados() {
        when(pagos.existePagoAprobado(sid)).thenReturn(true);
        s.setEstadoCatalogo(estado("ACTIVA"));s.setFechaVencimiento(Instant.parse("2026-02-20T20:00:00Z"));
        assertThat(registrar("f","p",Instant.parse("2026-02-18T20:00:00Z")).motivo()).isEqualTo("RENOVADA");
        assertThat(s.getFechaVencimiento()).isEqualTo(Instant.parse("2026-03-20T20:00:00Z"));
    }
    @Test void renovacionDespuesDelVencimientoParteDeLaAprobacion() {
        when(pagos.existePagoAprobado(sid)).thenReturn(true);
        s.setEstadoCatalogo(estado("VENCIDA"));s.setFechaVencimiento(aprobacion);
        registrar("f","p",Instant.parse("2026-02-03T20:00:00Z"));
        assertThat(s.getFechaVencimiento()).isEqualTo(Instant.parse("2026-03-03T20:00:00Z"));
    }
    @Test void repeticionNoSumaVigenciaNiAuditoriaYVinculaElPagoManualHistorico() {
        s.setEstadoCatalogo(estado("ACTIVA"));s.setFechaVencimiento(aprobacion.plusSeconds(86400));
        Pago p=previo("p");when(pagos.findByIdTransaccionMercadopago("p")).thenReturn(Optional.of(p));
        assertThat(registrar("f","p",aprobacion).motivo()).isEqualTo("YA_CONFIRMADA");
        assertThat(p.getIdFacturaMercadopago()).isEqualTo("f");
        assertThat(s.getFechaVencimiento()).isEqualTo(aprobacion.plusSeconds(86400));verifyNoInteractions(auditoria);
    }
    @Test void dosPagosDistintosParaUnaFacturaPasanARevision() {
        Pago p=previo("otro");p.setIdFacturaMercadopago("f");when(pagos.findByIdFacturaMercadopago("f")).thenReturn(Optional.of(p));
        assertThat(registrar("f","p",aprobacion).motivo()).isEqualTo("PAGO_O_FACTURA_YA_ASOCIADOS");verifyNoInteractions(auditoria);
    }
    @Test void pagoDeOtraSuscripcionNoSeAcredita() {
        Pago p=previo("p");var otra=new Suscripcion();otra.setIdSuscripcion(UUID.randomUUID());p.setSuscripcion(otra);
        when(pagos.findByIdTransaccionMercadopago("p")).thenReturn(Optional.of(p));
        assertThat(registrar("f","p",aprobacion).estado()).isEqualTo(ProcesadorNotificacionMercadoPagoService.Estado.REVISION);
        verify(pagos,never()).saveAndFlush(any());verifyNoInteractions(auditoria);
    }
    @Test void reservaReemplazadaDuranteHttpNoSeActiva() {
        s.setReemplazada(true);
        assertThat(registrar("f","p",aprobacion).motivo()).isEqualTo("INTENTO_REEMPLAZADO_O_INCONSISTENTE");
        verify(pagos,never()).saveAndFlush(any());
    }
    @Test void cancelacionConservaLaVigenciaYEsIdempotente() {
        s.setEstadoCatalogo(estado("ACTIVA"));s.setFechaVencimiento(aprobacion.plusSeconds(86400));u.setEsPremium(true);
        service.sincronizar(uid,sid,s.getTipoPlan(),remoto("cancelled"));
        assertThat(s.getEstadoCatalogo().getNombre()).isEqualTo("CANCELADA");assertThat(u.isEsPremium()).isTrue();
        assertThat(s.getFechaVencimiento()).isEqualTo(aprobacion.plusSeconds(86400));
        service.sincronizar(uid,sid,s.getTipoPlan(),remoto("cancelled"));
        verify(auditoria,times(1)).encolar(isNull(),eq("suscripcion"),any(),eq(sid),any(),any());
    }
    @Test void pausaYReactivacionNoConcedenUnMesAdicional() {
        s.setEstadoCatalogo(estado("ACTIVA"));s.setFechaVencimiento(aprobacion.plusSeconds(86400));
        assertThat(service.sincronizar(uid,sid,s.getTipoPlan(),remoto("paused")).motivo()).isEqualTo("SUSCRIPCION_PAUSADA");
        assertThat(service.sincronizar(uid,sid,s.getTipoPlan(),remoto("authorized")).motivo()).isEqualTo("SUSCRIPCION_REACTIVADA");
        assertThat(s.getFechaVencimiento()).isEqualTo(aprobacion.plusSeconds(86400));verify(pagos,never()).saveAndFlush(any());
    }
    @Test void autorizarSinPagoMantienePendienteYGratis() {
        service.sincronizar(uid,sid,s.getTipoPlan(),remoto("authorized"));
        assertThat(s.getEstadoCatalogo().getNombre()).isEqualTo("PENDIENTE");assertThat(u.isEsPremium()).isFalse();
    }
    @Test void respuestaRemotaAnteriorNoRevierteCancelacionMasReciente() {
        s.setEstadoCatalogo(estado("CANCELADA"));s.setEstadoMercadopago("cancelled");s.setActualizadaMercadopagoEn(aprobacion.plusSeconds(60));
        assertThat(service.sincronizar(uid,sid,s.getTipoPlan(),remoto("authorized")).motivo()).isEqualTo("ESTADO_REMOTO_YA_ACTUALIZADO");
        assertThat(s.getEstadoMercadopago()).isEqualTo("cancelled");verifyNoInteractions(auditoria);
    }
    @Test void pagoValidoDeCanceladaSeRegistraSinReactivarSuMandato() {
        s.setEstadoCatalogo(estado("CANCELADA"));s.setEstadoMercadopago("cancelled");
        var f=factura("f","p");
        assertThat(service.registrar(uid,sid,s.getTipoPlan(),f,pago("p",aprobacion),remoto("cancelled"),1).motivo()).isEqualTo("ACTIVADA");
        assertThat(s.getEstadoCatalogo().getNombre()).isEqualTo("CANCELADA");assertThat(u.isEsPremium()).isTrue();
    }
    @Test void cobroHistoricoVencidoNoActivaPremiumAunqueElIndicadorSeaVerdadero() {
        u.setEsPremium(true);
        service = new SuscripcionCicloTransaccionalService(usuarios,suscripciones,pagos,estados,auditoria,
                Clock.fixed(aprobacion.atZone(ZoneOffset.UTC).plusMonths(2).toInstant(), ZoneOffset.UTC));
        registrar("f", "p", aprobacion);
        assertThat(u.isEsPremium()).isFalse();
    }
    @Test void reautorizarConFechaFuturaSinPagoNoActivaLaSuscripcion() {
        s.setEstadoCatalogo(estado("CANCELADA")); s.setEstadoMercadopago("cancelled");
        s.setFechaVencimiento(aprobacion.plusSeconds(3600));
        service.sincronizar(uid,sid,s.getTipoPlan(),remoto("authorized"));
        assertThat(s.getEstadoCatalogo().getNombre()).isEqualTo("PENDIENTE");
        assertThat(u.isEsPremium()).isFalse();
        verify(pagos, never()).saveAndFlush(any());
    }
    Pago previo(String id) {var p=new Pago();p.setSuscripcion(s);p.setIdTransaccionMercadopago(id);p.setMonto(new BigDecimal("2500"));p.setFechaPago(aprobacion);var e=estado("APROBADO");e.setEntidad("PAGO");p.setEstadoCatalogo(e);return p;}
    ProcesadorNotificacionMercadoPagoService.ResultadoProcesamiento registrar(String f,String p,Instant fecha){return service.registrar(uid,sid,s.getTipoPlan(),factura(f,p),pago(p,fecha),remoto("authorized"),1);}
    FacturaAutorizada factura(String f,String p){return new FacturaAutorizada(f,"mp",sid.toString(),p,"processed",1,aprobacion,null,new BigDecimal("2500"),"ARS");}
    PagoConsultado pago(String p,Instant fecha){return new PagoConsultado(p,"approved",new BigDecimal("2500"),"ARS",fecha);}
    PreapprovalConsultado remoto(String estado){return new PreapprovalConsultado("mp",sid.toString(),estado,null,new BigDecimal("2500"),"ARS",1,"months",Instant.parse("2026-03-01T20:00:00Z"),aprobacion);}
    static Estado estado(String nombre){var e=new Estado();e.setNombre(nombre);e.setEntidad("SUSCRIPCION");return e;}
}
