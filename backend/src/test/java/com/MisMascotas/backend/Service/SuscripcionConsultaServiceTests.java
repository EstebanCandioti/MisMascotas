package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.MisMascotas.backend.Entity.*;
import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Repository.*;

class SuscripcionConsultaServiceTests {
    final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    final SuscripcionRepository suscripciones = mock(SuscripcionRepository.class);
    final PagoRepository pagos = mock(PagoRepository.class);
    final UUID uid = UUID.randomUUID();
    final Instant ahora = Instant.parse("2030-01-31T15:00:00Z");
    final Usuario usuario = new Usuario();
    final SuscripcionConsultaService servicio = new SuscripcionConsultaService(
            new PremiumService(usuarios, suscripciones, pagos, Clock.fixed(ahora, ZoneOffset.UTC)));

    @BeforeEach void preparar() {
        usuario.setIdUsuario(uid);
        when(usuarios.findById(uid)).thenReturn(Optional.of(usuario));
        when(suscripciones.findAllByUsuario_IdUsuario(uid)).thenReturn(List.of());
        when(pagos.buscarSuscripcionesConPagoAprobado(uid)).thenReturn(Set.of());
    }

    @Test void freeSinSuscripcionesDevuelveAusenciaNormalSinEscrituras() {
        var resultado = servicio.consultar(uid);
        assertThat(resultado.esPremium()).isFalse();
        assertThat(resultado.coberturaPagadaVigente()).isFalse();
        assertThat(resultado.fechaFinCobertura()).isNull();
        assertThat(resultado.suscripcion()).isNull();
        assertThat(resultado.intentoPendiente()).isNull();
        verify(usuarios).findById(uid);
        verify(suscripciones).findAllByUsuario_IdUsuario(uid);
        verify(pagos).buscarSuscripcionesConPagoAprobado(uid);
        verifyNoMoreInteractions(usuarios, suscripciones, pagos);
    }

    @Test void premiumManualSeDistingueDeCoberturaPaga() {
        usuario.setEsPremium(true);
        var resultado = servicio.consultar(uid);
        assertThat(resultado.esPremium()).isTrue();
        assertThat(resultado.coberturaPagadaVigente()).isFalse();
        assertThat(resultado.suscripcion()).isNull();
    }

    @ParameterizedTest @ValueSource(strings = {"ACTIVA", "CANCELADA"})
    void mandatoActivoOCanceladoConservaLaCoberturaPagada(String estado) {
        var s = nueva(estado, 1);
        s.setFechaVencimiento(ahora.plusSeconds(3600));
        s.setEstadoMercadopago("CANCELADA".equals(estado) ? "cancelled" : "authorized");
        usuario.setEsPremium(false);
        cargar(List.of(s), s);
        var resultado = servicio.consultar(uid);
        assertThat(resultado.esPremium()).isTrue();
        assertThat(resultado.coberturaPagadaVigente()).isTrue();
        assertThat(resultado.fechaFinCobertura()).isEqualTo(s.getFechaVencimiento());
        assertThat(resultado.suscripcion().estado()).isEqualTo(estado);
        assertThat(resultado.suscripcion().coberturaVigente()).isTrue();
        assertThat(resultado.suscripcion().requiereConciliacion()).isFalse();
    }

    @ParameterizedTest @ValueSource(strings = {"ACTIVA", "VENCIDA", "CANCELADA"})
    void enElInstanteDelVencimientoYaNoHayCoberturaSinMutarEstado(String estado) {
        var s = nueva(estado, 1);
        s.setFechaVencimiento(ahora);
        usuario.setEsPremium(true);
        cargar(List.of(s), s);
        var resultado = servicio.consultar(uid);
        assertThat(resultado.esPremium()).isFalse();
        assertThat(usuario.isEsPremium()).isTrue();
        assertThat(resultado.coberturaPagadaVigente()).isFalse();
        assertThat(resultado.fechaFinCobertura()).isNull();
        assertThat(resultado.suscripcion().estado()).isEqualTo(estado);
        assertThat(s.getEstadoCatalogo().getNombre()).isEqualTo(estado);
    }

    @Test void unaFechaFuturaSinPagoAprobadoNoEsCobertura() {
        var s = nueva("ACTIVA", 1);
        s.setFechaVencimiento(ahora.plusSeconds(3600));
        cargar(List.of(s));
        assertThat(servicio.consultar(uid).coberturaPagadaVigente()).isFalse();
    }

    @Test void altaPendienteConYSinIdRemotoSePuedeRecuperarSinActivar() {
        var s = nueva("PENDIENTE", 1);
        cargar(List.of(s));
        var resultado = servicio.consultar(uid);
        assertThat(resultado.suscripcion()).isNull();
        assertThat(resultado.intentoPendiente().idSuscripcion()).isEqualTo(s.getIdSuscripcion());
        assertThat(resultado.intentoPendiente().requiereConciliacion()).isTrue();
        s.setIdSuscripcionMercadopago("preapproval-qa");
        assertThat(servicio.consultar(uid).intentoPendiente().requiereConciliacion()).isFalse();
    }

    @Test void intentoNuevoNoOcultaCoberturaAnteriorYNingunoReemplazadoSeOfreceComoIntento() {
        var cubierta = nueva("CANCELADA", 1);
        cubierta.setReemplazada(true);
        cubierta.setFechaVencimiento(ahora.plusSeconds(3600));
        var pendiente = nueva("PENDIENTE", 2);
        var reemplazada = nueva("PENDIENTE", 3);
        reemplazada.setReemplazada(true);
        cargar(List.of(reemplazada, pendiente, cubierta), cubierta);
        var resultado = servicio.consultar(uid);
        assertThat(resultado.suscripcion().idSuscripcion()).isEqualTo(cubierta.getIdSuscripcion());
        assertThat(resultado.intentoPendiente().idSuscripcion()).isEqualTo(pendiente.getIdSuscripcion());
    }

    @Test void seleccionaLaMayorCoberturaPagadaAunqueLaOtraSeaMasReciente() {
        var larga = nueva("CANCELADA", 1);
        larga.setFechaVencimiento(ahora.plusSeconds(7200));
        var corta = nueva("ACTIVA", 2);
        corta.setFechaVencimiento(ahora.plusSeconds(3600));
        cargar(List.of(corta, larga), corta, larga);
        assertThat(servicio.consultar(uid).suscripcion().idSuscripcion()).isEqualTo(larga.getIdSuscripcion());
    }

    @Test void sinCoberturaMuestraLaUltimaSuscripcionNoReemplazada() {
        var anterior = nueva("VENCIDA", 1);
        var ultima = nueva("CANCELADA", 2);
        var reemplazada = nueva("CANCELADA", 3);
        reemplazada.setReemplazada(true);
        cargar(List.of(ultima, reemplazada, anterior));
        assertThat(servicio.consultar(uid).suscripcion().idSuscripcion()).isEqualTo(ultima.getIdSuscripcion());
    }

    @Test void pagoSinVencimientoRequiereRevisionYNoInventaUnaVigencia() {
        var s = nueva("ACTIVA", 1);
        usuario.setEsPremium(true);
        cargar(List.of(s), s);
        var resultado = servicio.consultar(uid);
        assertThat(resultado.esPremium()).isFalse();
        assertThat(resultado.coberturaPagadaVigente()).isFalse();
        assertThat(resultado.suscripcion().requiereConciliacion()).isTrue();
    }

    @Test void usuarioInexistenteNoConsultaSuscripcionesNiPagos() {
        when(usuarios.findById(uid)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.consultar(uid)).isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(suscripciones, pagos);
    }

    @Test void utilizaElResultadoCentralSinVolverAEvaluarElIndicadorNiLasFechas() {
        var premium = mock(PremiumService.class);
        var s = nueva("ACTIVA", 1);
        s.setFechaVencimiento(ahora.plusSeconds(60));
        // Esta prueba verifica delegación: el consumidor no reinterpreta la evidencia recibida.
        when(premium.consultarSuscripciones(uid)).thenReturn(new PremiumService.ConsultaPremium(
                new PremiumService.EstadoPremium(true, true, ahora.plusSeconds(60), false),
                List.of(s), Set.of(s.getIdSuscripcion()), Set.of(s.getIdSuscripcion())));
        var resultado = new SuscripcionConsultaService(premium).consultar(uid);
        assertThat(resultado.esPremium()).isTrue();
        assertThat(resultado.coberturaPagadaVigente()).isTrue();
        assertThat(resultado.fechaFinCobertura()).isEqualTo(ahora.plusSeconds(60));
        assertThat(resultado.suscripcion().coberturaVigente()).isTrue();
        verify(premium).consultarSuscripciones(uid);
        verifyNoMoreInteractions(premium);
    }

    @Test void raizYDetallesUtilizanUnSoloInstanteYSoloUnaLecturaDeCadaRepositorio() {
        var reloj = mock(Clock.class);
        when(reloj.instant()).thenReturn(ahora, ahora.plusSeconds(2));
        var s = nueva("ACTIVA", 1);
        s.setFechaVencimiento(ahora.plusSeconds(1));
        cargar(List.of(s), s);
        var consulta = new SuscripcionConsultaService(new PremiumService(usuarios, suscripciones, pagos, reloj));
        var resultado = consulta.consultar(uid);
        assertThat(resultado.esPremium()).isTrue();
        assertThat(resultado.coberturaPagadaVigente()).isTrue();
        assertThat(resultado.fechaFinCobertura()).isEqualTo(s.getFechaVencimiento());
        assertThat(resultado.suscripcion().coberturaVigente()).isTrue();
        verify(reloj).instant();
        verify(usuarios).findById(uid);
        verify(suscripciones).findAllByUsuario_IdUsuario(uid);
        verify(pagos).buscarSuscripcionesConPagoAprobado(uid);
        verifyNoMoreInteractions(reloj, usuarios, suscripciones, pagos);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void coberturasConLaMismaFechaConservanElDesempateAunqueCambieElOrden(boolean invertir) {
        var anterior = nueva("CANCELADA", 1);
        var reciente = nueva("ACTIVA", 2);
        anterior.setFechaVencimiento(ahora.plusSeconds(60));
        reciente.setFechaVencimiento(anterior.getFechaVencimiento());
        reciente.setFechaInicio(anterior.getFechaInicio());
        cargar(invertir ? List.of(reciente, anterior) : List.of(anterior, reciente), anterior, reciente);
        assertThat(servicio.consultar(uid).suscripcion().idSuscripcion()).isEqualTo(reciente.getIdSuscripcion());
    }

    @Test void errorAlConsultarPagosNoSeConvierteEnUnaRespuestaFree() {
        var error = new org.springframework.dao.DataAccessResourceFailureException("Fallo simulado");
        when(pagos.buscarSuscripcionesConPagoAprobado(uid)).thenThrow(error);
        assertThatThrownBy(() -> servicio.consultar(uid)).isSameAs(error);
        verifyNoInteractions(suscripciones);
    }

    private Suscripcion nueva(String nombre, int orden) {
        var s = new Suscripcion();
        s.setIdSuscripcion(new UUID(0, orden));
        s.setUsuario(usuario);
        s.setTipoPlan("PREMIUM_MENSUAL");
        s.setFechaInicio(ahora.minusSeconds(100 - orden));
        var e = new Estado(); e.setEntidad("SUSCRIPCION"); e.setNombre(nombre);
        s.setEstadoCatalogo(e);
        return s;
    }

    private void cargar(List<Suscripcion> lista, Suscripcion... aprobadas) {
        when(suscripciones.findAllByUsuario_IdUsuario(uid)).thenReturn(lista);
        when(pagos.buscarSuscripcionesConPagoAprobado(uid)).thenReturn(
                Arrays.stream(aprobadas).map(Suscripcion::getIdSuscripcion).collect(java.util.stream.Collectors.toSet()));
    }
}
