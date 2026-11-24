package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.MisMascotas.backend.Entity.*;
import com.MisMascotas.backend.Repository.*;

/** Solo PostgreSQL descartable de CI; los datos del caso se revierten al finalizar. */
@SpringBootTest
@ActiveProfiles("ci")
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_CONSULTA_IT", matches = "true")
class SuscripcionConsultaPostgresIT {
    @Autowired UsuarioRepository usuarios;
    @Autowired SuscripcionRepository suscripciones;
    @Autowired PagoRepository pagos;
    @Autowired EstadoRepository estados;
    @Autowired MockMvc mvc;
    @MockitoBean Clock reloj;

    @Test void catalogoRealExponeLosValoresPredeterminados() throws Exception {
        mvc.perform(get("/suscripciones/planes").with(user("catalogo@example.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].importe").value(4000))
                .andExpect(jsonPath("$[1].importe").value(40000))
                .andExpect(jsonPath("$[1].frecuencia").value(12));
    }

    @Test void consultaHttpConPostgresAislaUsuariosYConservaCancelacionConCobertura() throws Exception {
        var ahora = Instant.parse("2030-01-31T15:00:00Z");
        when(reloj.instant()).thenReturn(ahora);
        var propietario = usuario(true);
        var ajeno = usuario(false);
        var cancelada = suscripcion(propietario, "CANCELADA", ahora);
        cancelada.setEstadoMercadopago("cancelled");
        cancelada.setFechaCancelacion(ahora.minusSeconds(60));
        suscripciones.saveAndFlush(cancelada);
        var pago = new Pago(); pago.setSuscripcion(cancelada);
        pago.setMonto(new BigDecimal("4000.00")); pago.setFechaPago(ahora.minusSeconds(3600));
        pago.setEstadoCatalogo(estados.findByEntidadAndNombre("PAGO", "APROBADO").orElseThrow());
        pagos.saveAndFlush(pago);
        var pendiente = suscripcion(propietario, "PENDIENTE", ahora);
        pendiente.setFechaVencimiento(null); suscripciones.saveAndFlush(pendiente);
        var sinCobro = suscripcion(ajeno, "ACTIVA", ahora);
        long cantidadPagos = pagos.count();

        assertThat(pagos.buscarSuscripcionesConPagoAprobado(propietario.getIdUsuario()))
                .containsExactly(cancelada.getIdSuscripcion());
        assertThat(pagos.buscarSuscripcionesConPagoAprobado(ajeno.getIdUsuario())).isEmpty();
        mvc.perform(get("/suscripciones/me").with(user(propietario.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esPremium").value(true))
                .andExpect(jsonPath("$.coberturaPagadaVigente").value(true))
                .andExpect(jsonPath("$.suscripcion.idSuscripcion").value(cancelada.getIdSuscripcion().toString()))
                .andExpect(jsonPath("$.suscripcion.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.suscripcion.estadoMercadoPago").value("cancelled"))
                .andExpect(jsonPath("$.suscripcion.fechaVencimiento").value("2030-02-01T15:00:00Z"))
                .andExpect(jsonPath("$.intentoPendiente.idSuscripcion").value(pendiente.getIdSuscripcion().toString()));
        mvc.perform(get("/suscripciones/me").with(user(ajeno.getEmail()))
                        .param("usuarioId", propietario.getIdUsuario().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esPremium").value(false))
                .andExpect(jsonPath("$.coberturaPagadaVigente").value(false))
                .andExpect(jsonPath("$.suscripcion.idSuscripcion").value(sinCobro.getIdSuscripcion().toString()))
                .andExpect(jsonPath("$.suscripcion.tienePagoAprobado").value(false))
                .andExpect(jsonPath("$.intentoPendiente").value(org.hamcrest.Matchers.nullValue()));
        assertThat(pagos.count()).isEqualTo(cantidadPagos);
        assertThat(suscripciones.findById(cancelada.getIdSuscripcion()).orElseThrow().getEstadoCatalogo().getNombre())
                .isEqualTo("CANCELADA");
    }

    private Usuario usuario(boolean premium) {
        var usuario = new Usuario(); usuario.setNombre("Consulta QA");
        usuario.setEmail("consulta-" + UUID.randomUUID() + "@example.test");
        usuario.setPasswordHash("hash-ficticio-no-utilizable"); usuario.setEsPremium(premium);
        return usuarios.saveAndFlush(usuario);
    }

    private Suscripcion suscripcion(Usuario usuario, String estado, Instant ahora) {
        var s = new Suscripcion(); s.setUsuario(usuario); s.setTipoPlan("PREMIUM_MENSUAL");
        s.setEstadoCatalogo(estados.findByEntidadAndNombre("SUSCRIPCION", estado).orElseThrow());
        s.setFechaVencimiento(ahora.plusSeconds(86400));
        return suscripciones.saveAndFlush(s);
    }
}
