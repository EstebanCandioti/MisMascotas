package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;import java.util.*;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;import org.mockito.junit.jupiter.MockitoExtension;
import com.MisMascotas.backend.Entity.*;import com.MisMascotas.backend.Repository.*;

@ExtendWith(MockitoExtension.class)
class SuscripcionVencimientoServiceTests {
    @Mock UsuarioRepository usuarios;@Mock SuscripcionRepository suscripciones;@Mock PagoRepository pagos;
    @Mock EstadoRepository estados;@Mock EventoAuditoriaPendienteService auditoria;
    final Instant ahora=Instant.parse("2026-11-18T20:00:00Z"); final UUID uid=UUID.randomUUID();
    Usuario u;Suscripcion s;SuscripcionVencimientoService service;
    @BeforeEach void preparar(){
        service=new SuscripcionVencimientoService(usuarios,suscripciones,pagos,estados,auditoria,Clock.fixed(ahora,ZoneOffset.UTC));
        u=new Usuario();u.setIdUsuario(uid);u.setEsPremium(true);s=nueva(ahora,"ACTIVA");
        when(usuarios.findByIdConBloqueo(uid)).thenReturn(Optional.of(u));
        when(suscripciones.findAllByUsuario_IdUsuario(uid)).thenReturn(List.of(s));
        lenient().when(estados.findByEntidadAndNombre("SUSCRIPCION","VENCIDA")).thenReturn(Optional.of(SuscripcionCicloTransaccionalServiceTests.estado("VENCIDA")));
        when(pagos.existePagoAprobado(s.getIdSuscripcion())).thenReturn(true);
    }
    @Test void venceEnElInstanteExactoYNoDuplicaEventos(){service.vencer(uid);service.vencer(uid);
        assertThat(u.isEsPremium()).isFalse();assertThat(s.getEstadoCatalogo().getNombre()).isEqualTo("VENCIDA");
        verify(auditoria,times(2)).encolar(isNull(),anyString(),any(),any(),any(),any());}
    @Test void antesDeVencerConservaPremium(){s.setFechaVencimiento(ahora.plusSeconds(1));service.vencer(uid);assertThat(u.isEsPremium()).isTrue();verifyNoInteractions(auditoria);}
    @Test void otraSuscripcionPagaVigenteConservaPremium(){var otra=nueva(ahora.plusSeconds(86400),"CANCELADA");when(suscripciones.findAllByUsuario_IdUsuario(uid)).thenReturn(List.of(s,otra));when(pagos.existePagoAprobado(otra.getIdSuscripcion())).thenReturn(true);service.vencer(uid);assertThat(u.isEsPremium()).isTrue();assertThat(s.getEstadoCatalogo().getNombre()).isEqualTo("VENCIDA");verify(usuarios,never()).saveAndFlush(any());}
    @Test void canceladaVencidaConservaSuCancelacion(){s.setEstadoCatalogo(SuscripcionCicloTransaccionalServiceTests.estado("CANCELADA"));service.vencer(uid);assertThat(s.getEstadoCatalogo().getNombre()).isEqualTo("CANCELADA");assertThat(u.isEsPremium()).isFalse();}
    @Test void cuentaSinPagosNoPierdePremiumManual(){when(pagos.existePagoAprobado(s.getIdSuscripcion())).thenReturn(false);service.vencer(uid);assertThat(u.isEsPremium()).isTrue();verifyNoInteractions(auditoria);}
    @Test void pagoSinVencimientoExigeRevisionAntesDeQuitarAcceso(){s.setFechaVencimiento(null);assertThatThrownBy(()->service.vencer(uid)).isInstanceOf(IllegalStateException.class);assertThat(u.isEsPremium()).isTrue();verifyNoInteractions(auditoria);}
    Suscripcion nueva(Instant fecha,String estado){var a=new Suscripcion();a.setIdSuscripcion(UUID.randomUUID());a.setUsuario(u);a.setFechaVencimiento(fecha);a.setEstadoCatalogo(SuscripcionCicloTransaccionalServiceTests.estado(estado));return a;}
}
