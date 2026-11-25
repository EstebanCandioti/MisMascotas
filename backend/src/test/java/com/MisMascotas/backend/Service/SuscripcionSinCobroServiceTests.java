package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.MisMascotas.backend.Entity.Estado;
import com.MisMascotas.backend.Entity.Suscripcion;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Integration.MercadoPagoGateway;
import com.MisMascotas.backend.Integration.MercadoPagoGateway.CuotaConsultada;
import com.MisMascotas.backend.Integration.MercadoPagoGateway.PagoConsultado;
import com.MisMascotas.backend.Integration.MercadoPagoGateway.PreapprovalConsultado;
import com.MisMascotas.backend.Repository.EstadoRepository;
import com.MisMascotas.backend.Repository.PagoRepository;
import com.MisMascotas.backend.Repository.SuscripcionRepository;
import com.MisMascotas.backend.Repository.UsuarioRepository;
import com.MisMascotas.backend.Service.SuscripcionCobroTransaccionalService.EstadoCobro;

@ExtendWith(MockitoExtension.class)
class SuscripcionSinCobroServiceTests {

    @Mock private UsuarioRepository usuarios;
    @Mock private SuscripcionRepository suscripciones;
    @Mock private PagoRepository pagos;
    @Mock private EstadoRepository estados;
    @Mock private EventoAuditoriaPendienteService auditoria;
    @Mock private MercadoPagoGateway proveedor;

    @ParameterizedTest
    @CsvSource({
            "false, pending", "true, pending",
            "false, authorized", "true, authorized",
            "false, rejected", "true, rejected"
    })
    void sinCobroAprobadoConservaElPlanPrevioSinCrearPagosNiAuditoria(boolean premiumPrevio, String escenario) {
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(UUID.randomUUID());
        usuario.setEsPremium(premiumPrevio);
        Suscripcion pendiente = suscripcion(usuario, "PENDIENTE");
        Suscripcion vigente = null;
        Instant vencimientoPrevio = Instant.parse("2099-06-30T15:00:00Z");
        if (premiumPrevio) {
            vigente = suscripcion(usuario, "ACTIVA");
            vigente.setFechaVencimiento(vencimientoPrevio);
            vigente.setProximoRecargo(vencimientoPrevio);
            usuario.getSuscripciones().add(vigente);
        }
        usuario.getSuscripciones().add(pendiente);
        when(suscripciones.findByIdSuscripcionAndUsuario_IdUsuario(pendiente.getIdSuscripcion(), usuario.getIdUsuario()))
                .thenReturn(Optional.of(pendiente));
        when(proveedor.consultarPreapproval("preapproval-pendiente")).thenReturn(new PreapprovalConsultado(
                "preapproval-pendiente", pendiente.getIdSuscripcion().toString(),
                "pending".equals(escenario) ? "pending" : "authorized", null,
                new BigDecimal("2500"), "ARS", 1, "months"));
        if ("authorized".equals(escenario)) {
            when(proveedor.buscarCuotas("preapproval-pendiente")).thenReturn(List.of());
        } else if ("rejected".equals(escenario)) {
            when(proveedor.buscarCuotas("preapproval-pendiente"))
                    .thenReturn(List.of(new CuotaConsultada("preapproval-pendiente", "999")));
            when(proveedor.consultarPago("999"))
                    .thenReturn(new PagoConsultado("999", "rejected", new BigDecimal("2500"), "ARS", null));
        }
        var persistencia = new SuscripcionCobroTransaccionalService(usuarios, suscripciones, pagos, estados, auditoria,
                java.time.Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), java.time.ZoneOffset.UTC));
        var verificacion = new SuscripcionPrimerCobroService(persistencia,
                new CatalogoPlanesSuscripcion("2500", "ARS", 1, "months", "Premium QA", "40000", "ARS", 12, "months", "Plan Premium anual MisMascotas"), proveedor);

        var resultado = verificacion.confirmar(usuario.getIdUsuario(), pendiente.getIdSuscripcion());

        assertThat(resultado.estado()).isEqualTo(EstadoCobro.SIN_COBRO_APROBADO);
        assertThat(resultado.idPago()).isNull();
        assertThat(resultado.idPagoMercadoPago()).isNull();
        assertThat(resultado.fechaPago()).isNull();
        assertThat(resultado.fechaVencimiento()).isNull();
        assertThat(usuario.isEsPremium()).isEqualTo(premiumPrevio);
        assertThat(pendiente.getEstadoCatalogo().getNombre()).isEqualTo("PENDIENTE");
        assertThat(pendiente.getFechaVencimiento()).isNull();
        assertThat(pendiente.getPagos()).isEmpty();
        if (vigente != null) {
            assertThat(vigente.getEstadoCatalogo().getNombre()).isEqualTo("ACTIVA");
            assertThat(vigente.getFechaVencimiento()).isEqualTo(vencimientoPrevio);
            assertThat(vigente.getProximoRecargo()).isEqualTo(vencimientoPrevio);
        }
        verifyNoInteractions(pagos, usuarios, auditoria, estados);
        verify(suscripciones).findByIdSuscripcionAndUsuario_IdUsuario(pendiente.getIdSuscripcion(), usuario.getIdUsuario());
        verifyNoMoreInteractions(suscripciones);
        verify(proveedor, never()).crearPreapproval(any());
        verify(proveedor, never()).cancelarPreapproval(any());
    }

    private Suscripcion suscripcion(Usuario usuario, String nombreEstado) {
        Estado estado = new Estado();
        estado.setEntidad("SUSCRIPCION");
        estado.setNombre(nombreEstado);
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setIdSuscripcion(UUID.randomUUID());
        suscripcion.setUsuario(usuario);
        suscripcion.setTipoPlan("PREMIUM_MENSUAL");
        suscripcion.setIdSuscripcionMercadopago(
                "PENDIENTE".equals(nombreEstado) ? "preapproval-pendiente" : "preapproval-vigente");
        suscripcion.setEstadoCatalogo(estado);
        return suscripcion;
    }
}
