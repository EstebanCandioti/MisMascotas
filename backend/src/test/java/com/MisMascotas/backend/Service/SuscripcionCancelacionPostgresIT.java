package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.MisMascotas.backend.Entity.*;
import com.MisMascotas.backend.Repository.*;
import com.MisMascotas.backend.Integration.MercadoPagoGateway;
import com.MisMascotas.backend.Integration.MercadoPagoGateway.PreapprovalConsultado;
import com.MisMascotas.backend.DTO.CancelarSuscripcionResponseDTO.Resultado;

@SpringBootTest(properties = "spring.jpa.open-in-view=true")
@ActiveProfiles("ci")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_CONSULTA_IT", matches = "true")
class SuscripcionCancelacionPostgresIT {
    @Autowired UsuarioRepository usuarios;
    @Autowired SuscripcionRepository suscripciones;
    @Autowired PagoRepository pagos;
    @Autowired EstadoRepository estados;
    @Autowired SuscripcionCancelacionService servicio;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate sql;
    @MockitoBean MercadoPagoGateway proveedor;
    @MockitoBean Clock reloj;
    @MockitoSpyBean EventoAuditoriaPendienteService auditoria;
    final Instant ahora = Instant.parse("2030-01-31T15:00:00Z");
    Usuario propietario; Suscripcion s;

    @BeforeEach void preparar() {
        when(reloj.instant()).thenReturn(ahora);
        propietario = new Usuario(); propietario.setNombre("Cancelacion QA");
        propietario.setEmail("cancelacion-" + UUID.randomUUID() + "@example.test");
        propietario.setPasswordHash("hash-ficticio-no-utilizable"); propietario.setEsPremium(true);
        propietario = usuarios.saveAndFlush(propietario);
        s = new Suscripcion(); s.setUsuario(propietario); s.setTipoPlan("PREMIUM_MENSUAL");
        s.setEstadoCatalogo(estados.findByEntidadAndNombre("SUSCRIPCION", "ACTIVA").orElseThrow());
        s.setEstadoMercadopago("authorized"); s.setIdSuscripcionMercadopago("cancelacion-" + UUID.randomUUID());
        s.setFechaVencimiento(ahora.plusSeconds(86400)); s = suscripciones.saveAndFlush(s);
        var p = new Pago(); p.setSuscripcion(s); p.setMonto(new BigDecimal("4000.00"));
        p.setFechaPago(ahora.minusSeconds(3600));
        p.setEstadoCatalogo(estados.findByEntidadAndNombre("PAGO", "APROBADO").orElseThrow());
        pagos.saveAndFlush(p);
    }

    @AfterEach void limpiar() {
        if (propietario == null || propietario.getIdUsuario() == null) return;
        var uid = propietario.getIdUsuario();
        sql.update("DELETE FROM evento_auditoria_pendiente WHERE actor_id = ?", uid);
        sql.update("DELETE FROM pago WHERE suscripcion_id IN (SELECT id_suscripcion FROM suscripcion WHERE usuario_id = ?)", uid);
        sql.update("DELETE FROM suscripcion WHERE usuario_id = ?", uid);
        sql.update("DELETE FROM usuario WHERE id_usuario = ?", uid);
    }

    @Test void cancelacionHttpYRepeticionConservanCoberturaPagoYAuditoriaUnica() throws Exception {
        when(proveedor.consultarPreapproval(s.getIdSuscripcionMercadopago())).thenAnswer(invocacion -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return remoto("cancelled", ahora);
        });
        mvc.perform(post("/suscripciones/{id}/cancelar", s.getIdSuscripcion()).with(user(propietario.getEmail())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultado").value("CANCELADA"))
                .andExpect(jsonPath("$.fechaVencimiento").value("2030-02-01T15:00:00Z"));
        mvc.perform(post("/suscripciones/{id}/cancelar", s.getIdSuscripcion()).with(user(propietario.getEmail())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultado").value("YA_CANCELADA"));
        assertThat(usuarios.findById(propietario.getIdUsuario()).orElseThrow().isEsPremium()).isTrue();
        assertThat(pagos.findBySuscripcion_IdSuscripcionOrderByFechaPagoAsc(s.getIdSuscripcion())).hasSize(1);
        assertThat(actual().getFechaVencimiento()).isEqualTo(s.getFechaVencimiento());
        assertThat(eventos()).isEqualTo(1);
        assertThat(sql.queryForObject("SELECT valor_nuevo FROM evento_auditoria_pendiente WHERE actor_id = ?", String.class,
                propietario.getIdUsuario())).contains("CANCELACION_USUARIO");
        verify(proveedor, never()).cancelarPreapproval(any());
    }

    @Test void putYGetSeEjecutanSinTransaccionYLaConfirmacionEsLocal() {
        var consultas = new java.util.concurrent.atomic.AtomicInteger();
        when(proveedor.consultarPreapproval(s.getIdSuscripcionMercadopago())).thenAnswer(i -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return remoto(consultas.getAndIncrement() == 0 ? "authorized" : "cancelled", ahora);
        });
        doAnswer(i -> { assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); return null; })
                .when(proveedor).cancelarPreapproval(s.getIdSuscripcionMercadopago());
        assertThat(servicio.cancelar(propietario.getIdUsuario(), s.getIdSuscripcion()).resultado()).isEqualTo(Resultado.CANCELADA);
        assertThat(estadoActual()).isEqualTo("CANCELADA");
        verify(proveedor).cancelarPreapproval(s.getIdSuscripcionMercadopago());
    }

    @Test void falloDespuesDeEncolarRevierteCancelacionLocalYPermiteReintentar() {
        when(proveedor.consultarPreapproval(any())).thenReturn(remoto("cancelled", ahora));
        EventoAuditoriaPendienteService destino = AopTestUtils.getUltimateTargetObject(auditoria);
        doAnswer(i -> { i.callRealMethod(); throw new IllegalStateException("Fallo QA despues de encolar"); })
                .when(destino).encolar(any(), any(), any(), any(), any(), any());
        assertThat(servicio.cancelar(propietario.getIdUsuario(), s.getIdSuscripcion()).resultado())
                .isEqualTo(Resultado.REQUIERE_CONCILIACION);
        assertThat(estadoActual()).isEqualTo("ACTIVA");
        assertThat(actual().getFechaCancelacion()).isNull();
        assertThat(eventos()).isZero();
        doCallRealMethod().when(destino).encolar(any(), any(), any(), any(), any(), any());
        assertThat(servicio.cancelar(propietario.getIdUsuario(), s.getIdSuscripcion()).resultado()).isEqualTo(Resultado.CANCELADA);
        assertThat(eventos()).isEqualTo(1);
    }

    @Test void respuestaRemotaViejaNoSobrescribeUnCambioMasReciente() {
        when(proveedor.consultarPreapproval(any())).thenAnswer(i -> {
            var modificada = actual(); modificada.setActualizadaMercadopagoEn(ahora.plusSeconds(60));
            suscripciones.saveAndFlush(modificada);
            return remoto("cancelled", ahora);
        });
        assertThat(servicio.cancelar(propietario.getIdUsuario(), s.getIdSuscripcion()).resultado())
                .isEqualTo(Resultado.REQUIERE_CONCILIACION);
        assertThat(estadoActual()).isEqualTo("ACTIVA");
        assertThat(eventos()).isZero();
    }

    @Test void confirmacionesConcurrentesProducenUnSoloEvento() throws Exception {
        when(proveedor.consultarPreapproval(any())).thenReturn(remoto("cancelled", ahora));
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var inicio = new CountDownLatch(1);
            Callable<Resultado> trabajo = () -> { inicio.await(5, TimeUnit.SECONDS);
                return servicio.cancelar(propietario.getIdUsuario(), s.getIdSuscripcion()).resultado(); };
            var a = ejecutor.submit(trabajo); var b = ejecutor.submit(trabajo); inicio.countDown();
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(Resultado.CANCELADA, Resultado.YA_CANCELADA);
        }
        assertThat(eventos()).isEqualTo(1);
    }

    @Test void conservaUnaVigenciaExtendidaMientrasSeConsultabaAlProveedor() {
        var nuevaVigencia = ahora.plusSeconds(2 * 86400);
        when(proveedor.consultarPreapproval(any())).thenAnswer(i -> {
            var renovada = actual(); renovada.setFechaVencimiento(nuevaVigencia);
            suscripciones.saveAndFlush(renovada);
            return remoto("cancelled", ahora);
        });
        var resultado = servicio.cancelar(propietario.getIdUsuario(), s.getIdSuscripcion());
        assertThat(resultado.resultado()).isEqualTo(Resultado.CANCELADA);
        assertThat(resultado.fechaVencimiento()).isEqualTo(nuevaVigencia);
        assertThat(actual().getFechaVencimiento()).isEqualTo(nuevaVigencia);
    }

    @Test void uuidAjenoOInexistenteNoConsultaAlProveedor() throws Exception {
        var ajeno = new Usuario(); ajeno.setNombre("Otra cuenta");
        ajeno.setEmail("ajeno-" + UUID.randomUUID() + "@example.test"); ajeno.setPasswordHash("hash-ficticio");
        ajeno = usuarios.saveAndFlush(ajeno);
        try {
            mvc.perform(post("/suscripciones/{id}/cancelar", s.getIdSuscripcion()).with(user(ajeno.getEmail())))
                    .andExpect(status().isNotFound());
            mvc.perform(post("/suscripciones/{id}/cancelar", UUID.randomUUID()).with(user(propietario.getEmail())))
                    .andExpect(status().isNotFound());
            verifyNoInteractions(proveedor);
        } finally { usuarios.deleteById(ajeno.getIdUsuario()); }
    }

    @Test void cancelacionHttpConservaRenovacionConfirmadaDuranteLaConsultaRemota() throws Exception {
        var extendida = ahora.plusSeconds(2 * 86400);
        when(proveedor.consultarPreapproval(any())).thenAnswer(i -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            // Otro flujo confirma en su propia conexión, fuera del EntityManager de la petición.
            CompletableFuture.runAsync(() -> sql.update(
                    "UPDATE suscripcion SET fecha_vencimiento=? WHERE id_suscripcion=?",
                    Timestamp.from(extendida), s.getIdSuscripcion())).get(5, TimeUnit.SECONDS);
            return remoto("cancelled", ahora);
        });

        mvc.perform(post("/suscripciones/{id}/cancelar", s.getIdSuscripcion()).with(user(propietario.getEmail())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultado").value("CANCELADA"))
                .andExpect(jsonPath("$.fechaVencimiento").value(extendida.toString()));

        assertThat(actual().getFechaVencimiento()).isEqualTo(extendida);
        assertThat(estadoActual()).isEqualTo("CANCELADA");
        assertThat(eventos()).isEqualTo(1);
        assertThat(sql.queryForObject("SELECT valor_anterior FROM evento_auditoria_pendiente WHERE actor_id = ?",
                String.class, propietario.getIdUsuario())).contains(extendida.toString());
        assertThat(usuarios.findById(propietario.getIdUsuario()).orElseThrow().isEsPremium()).isTrue();
        assertThat(pagos.findBySuscripcion_IdSuscripcionOrderByFechaPagoAsc(s.getIdSuscripcion())).hasSize(1);
    }

    @Test void cancelacionHttpRechazaRespuestaAnteriorACambioConfirmadoDuranteLaConsultaRemota() throws Exception {
        var actualizacionReciente = ahora.plusSeconds(60);
        when(proveedor.consultarPreapproval(any())).thenAnswer(i -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            CompletableFuture.runAsync(() -> sql.update(
                    "UPDATE suscripcion SET actualizada_mercadopago_en=? WHERE id_suscripcion=?",
                    Timestamp.from(actualizacionReciente), s.getIdSuscripcion())).get(5, TimeUnit.SECONDS);
            return remoto("cancelled", ahora);
        });

        mvc.perform(post("/suscripciones/{id}/cancelar", s.getIdSuscripcion()).with(user(propietario.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("REQUIERE_CONCILIACION"));

        var persistida = actual();
        assertThat(persistida.getActualizadaMercadopagoEn()).isEqualTo(actualizacionReciente);
        assertThat(persistida.getEstadoMercadopago()).isEqualTo("authorized");
        assertThat(persistida.getFechaCancelacion()).isNull();
        assertThat(estadoActual()).isEqualTo("ACTIVA");
        assertThat(eventos()).isZero();
    }

    private Suscripcion actual() {
        return suscripciones.findByIdSuscripcionAndUsuario_IdUsuario(s.getIdSuscripcion(), propietario.getIdUsuario()).orElseThrow();
    }
    private String estadoActual() {
        return sql.queryForObject("SELECT e.nombre FROM suscripcion s JOIN estados e ON e.id_estado=s.estado_id WHERE s.id_suscripcion=?",
                String.class, s.getIdSuscripcion());
    }
    private int eventos() {
        return sql.queryForObject("SELECT count(*) FROM evento_auditoria_pendiente WHERE actor_id = ?", Integer.class,
                propietario.getIdUsuario());
    }
    private PreapprovalConsultado remoto(String estado, Instant actualizacion) {
        return new PreapprovalConsultado(s.getIdSuscripcionMercadopago(), s.getIdSuscripcion().toString(), estado,
                null, new BigDecimal("4000"), "ARS", 1, "months", null, actualizacion);
    }
}
