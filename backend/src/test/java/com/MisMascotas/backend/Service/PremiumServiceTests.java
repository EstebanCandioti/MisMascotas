package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import com.MisMascotas.backend.Entity.*;
import com.MisMascotas.backend.Exception.*;
import com.MisMascotas.backend.Repository.*;

class PremiumServiceTests {
    final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    final SuscripcionRepository suscripciones = mock(SuscripcionRepository.class);
    final PagoRepository pagos = mock(PagoRepository.class);
    final Instant ahora = Instant.parse("2030-01-31T15:00:00Z");
    final UUID uid = UUID.randomUUID();
    final Usuario propietario = new Usuario();
    final PremiumService servicio = new PremiumService(usuarios, suscripciones, pagos,
            Clock.fixed(ahora, ZoneOffset.UTC));

    @BeforeEach void preparar() {
        propietario.setIdUsuario(uid);
        when(usuarios.findById(uid)).thenReturn(Optional.of(propietario));
        when(pagos.buscarSuscripcionesConPagoAprobado(uid)).thenReturn(Set.of());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void sinPagosAprobadosConservaSoloLaCompatibilidadManual(boolean manual) {
        propietario.setEsPremium(manual);
        var resultado = servicio.consultar(uid);
        assertThat(resultado.esPremium()).isEqualTo(manual);
        assertThat(resultado.premiumManual()).isEqualTo(manual);
        assertThat(resultado.coberturaPagadaVigente()).isFalse();
        assertThat(resultado.fechaFinCobertura()).isNull();
        verify(usuarios).findById(uid);
        verify(pagos).buscarSuscripcionesConPagoAprobado(uid);
        verifyNoMoreInteractions(usuarios, pagos);
        verifyNoInteractions(suscripciones);
    }

    @ParameterizedTest @ValueSource(strings = {"ACTIVA", "CANCELADA", "VENCIDA"})
    void laCoberturaPagadaDecideIndependientementeDelEstadoLocal(String estado) {
        var s = suscripcion(estado, ahora.plusSeconds(1));
        cargar(List.of(s), s);
        var resultado = servicio.consultar(uid);
        assertThat(resultado.esPremium()).isTrue();
        assertThat(resultado.coberturaPagadaVigente()).isTrue();
        assertThat(resultado.fechaFinCobertura()).isEqualTo(s.getFechaVencimiento());
        assertThat(resultado.premiumManual()).isFalse();
        // Un indicador pendiente de sincronizar no niega una cobertura válida ni se modifica por leer.
        assertThat(propietario.isEsPremium()).isFalse();
    }

    @ParameterizedTest @ValueSource(longs = {-1, 0})
    void vencimientoAlcanzadoNoSeConvierteEnPremiumManual(long diferenciaSegundos) {
        propietario.setEsPremium(true);
        var s = suscripcion("ACTIVA", ahora.plusSeconds(diferenciaSegundos));
        cargar(List.of(s), s);
        var resultado = servicio.consultar(uid);
        assertThat(resultado.esPremium()).isFalse();
        assertThat(resultado.premiumManual()).isFalse();
        assertThat(resultado.fechaFinCobertura()).isNull();
        assertThat(propietario.isEsPremium()).isTrue();
    }

    @Test void unaFechaFuturaSinPagoNoOtorgaPremium() {
        when(suscripciones.findAllByUsuario_IdUsuario(uid))
                .thenReturn(List.of(suscripcion("ACTIVA", ahora.plusSeconds(86400))));
        assertThat(servicio.esPremium(uid)).isFalse();
        verifyNoInteractions(suscripciones);
    }

    @Test void pagoSinVencimientoNoAutorizaNiSeConfundeConUnaAsignacionManual() {
        propietario.setEsPremium(true);
        var s = suscripcion("ACTIVA", null);
        cargar(List.of(s), s);
        assertThat(servicio.esPremium(uid)).isFalse();
    }

    @Test void entreVariasSuscripcionesEligeLaMayorCoberturaRespaldadaPorPagos() {
        var vencida = suscripcion("CANCELADA", ahora.minusSeconds(1));
        var corta = suscripcion("ACTIVA", ahora.plusSeconds(100));
        var larga = suscripcion("CANCELADA", ahora.plusSeconds(200));
        var noPagada = suscripcion("ACTIVA", ahora.plusSeconds(1000));
        cargar(List.of(noPagada, vencida, corta, larga), vencida, corta, larga);
        assertThat(servicio.consultar(uid).fechaFinCobertura()).isEqualTo(larga.getFechaVencimiento());
    }

    @Test void unaPausaRemotaNoEliminaLaCobertura() {
        var s = suscripcion("ACTIVA", ahora.plusSeconds(60));
        s.setEstadoMercadopago("paused");
        cargar(List.of(s), s);
        servicio.exigirPremium(uid);
        verify(usuarios, never()).save(any());
        verify(pagos, never()).save(any());
        verify(suscripciones, never()).save(any());
    }

    @Test void exigirPremiumRechazaFreeConLaExcepcionEspecifica() {
        assertThatThrownBy(() -> servicio.exigirPremium(uid)).isInstanceOf(PremiumRequeridoException.class);
    }

    @Test void exigirPremiumAceptaLaCompatibilidadManualAcordada() {
        propietario.setEsPremium(true);
        assertThatCode(() -> servicio.exigirPremium(uid)).doesNotThrowAnyException();
    }

    @Test void propietarioInexistenteNoSeTrataComoFreeNiConsultaPagos() {
        when(usuarios.findById(uid)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.esPremium(uid)).isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(pagos, suscripciones);
    }

    @Test void consultaPagosYSuscripcionesSoloDelPropietarioSolicitado() {
        var s = suscripcion("ACTIVA", ahora.plusSeconds(60));
        cargar(List.of(s), s);
        assertThat(servicio.esPremium(uid)).isTrue();
        verify(usuarios).findById(uid);
        verify(pagos).buscarSuscripcionesConPagoAprobado(uid);
        verify(suscripciones).findAllByUsuario_IdUsuario(uid);
        verifyNoMoreInteractions(usuarios, pagos, suscripciones);
    }

    @Test void unFalloDeBaseNoSeOcultaComoUsuarioFree() {
        when(pagos.buscarSuscripcionesConPagoAprobado(uid)).thenThrow(new DataAccessResourceFailureException("Fallo QA"));
        assertThatThrownBy(() -> servicio.esPremium(uid)).isInstanceOf(DataAccessResourceFailureException.class);
    }

    private Suscripcion suscripcion(String estado, Instant vencimiento) {
        var s = new Suscripcion(); s.setIdSuscripcion(UUID.randomUUID()); s.setUsuario(propietario);
        var e = new Estado(); e.setEntidad("SUSCRIPCION"); e.setNombre(estado);
        s.setEstadoCatalogo(e); s.setFechaVencimiento(vencimiento);
        return s;
    }
    private void cargar(List<Suscripcion> lista, Suscripcion... aprobadas) {
        when(suscripciones.findAllByUsuario_IdUsuario(uid)).thenReturn(lista);
        when(pagos.buscarSuscripcionesConPagoAprobado(uid)).thenReturn(Arrays.stream(aprobadas)
                .map(Suscripcion::getIdSuscripcion).collect(java.util.stream.Collectors.toSet()));
    }
}
