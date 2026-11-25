package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import com.MisMascotas.backend.Entity.*;
import com.MisMascotas.backend.Integration.MercadoPagoGateway;
import com.MisMascotas.backend.Repository.*;

/** HTTP y PostgreSQL real de CI. Nunca usa Supabase ni pagos reales. */
@SpringBootTest(properties = "spring.datasource.hikari.connection-init-sql=SET lock_timeout = '5s'")
@ActiveProfiles("ci")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_PREMIUM_IT", matches = "true")
class MascotaPremiumPostgresIT {
    @Autowired UsuarioRepository usuarios;
    @Autowired MascotaRepository mascotas;
    @Autowired SuscripcionRepository suscripciones;
    @Autowired PagoRepository pagos;
    @Autowired EstadoRepository estados;
    @Autowired JdbcTemplate sql;
    @Autowired MockMvc mvc;
    @MockitoBean Clock reloj;
    @MockitoBean MercadoPagoGateway proveedor;
    final Instant ahora = Instant.parse("2030-01-31T15:00:00Z");
    final List<UUID> cuentas = new ArrayList<>();
    Usuario propietario;

    @BeforeEach void preparar() {
        when(reloj.instant()).thenReturn(ahora);
        propietario = usuario(false);
    }

    @AfterEach void limpiar() {
        for (var uid : cuentas) {
            sql.update("DELETE FROM log_auditoria WHERE actor_id = ?", uid);
            sql.update("DELETE FROM evento_auditoria_pendiente WHERE actor_id = ?", uid);
            sql.update("DELETE FROM mascota WHERE propietario_id = ?", uid);
            sql.update("DELETE FROM pago WHERE suscripcion_id IN (SELECT id_suscripcion FROM suscripcion WHERE usuario_id = ?)", uid);
            sql.update("DELETE FROM suscripcion WHERE usuario_id = ?", uid);
            sql.update("DELETE FROM usuario WHERE id_usuario = ?", uid);
        }
        verifyNoInteractions(proveedor);
    }

    @Test void freeConCuatroRegistraLaQuintaYConservaLaRespuestaExistente() throws Exception {
        mascotasIniciales(4);
        mvc.perform(alta()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.propietarioId").value(propietario.getIdUsuario().toString()))
                .andExpect(jsonPath("$.nombre").value("Nueva mascota"));
        assertThat(cantidad()).isEqualTo(5);
        assertThat(auditorias()).isEqualTo(1);
        verificarConsultasPremium(false);
    }

    @Test void freeConCincoNoRegistraSextaNiAuditaUnaCreacionFallida() throws Exception {
        mascotasIniciales(5);
        rechazarPorLimite();
        assertThat(cantidad()).isEqualTo(5);
        assertThat(auditorias()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"ACTIVA,authorized,PREMIUM_MENSUAL", "CANCELADA,cancelled,PREMIUM_MENSUAL", "ACTIVA,paused,PREMIUM_ANUAL"})
    void premiumPagadoPermiteSextaAunqueElIndicadorPersistidoSeaFalso(String estado, String remoto, String plan) throws Exception {
        mascotasIniciales(5);
        cobertura(propietario, estado, remoto, ahora.plusSeconds(3600), true, plan);
        mvc.perform(alta()).andExpect(status().isCreated());
        assertThat(cantidad()).isEqualTo(6);
        assertThat(usuarios.findById(propietario.getIdUsuario()).orElseThrow().isEsPremium()).isFalse();
        assertThat(auditorias()).isEqualTo(1);
        verificarConsultasPremium(true);
    }

    @Test void premiumManualSinPagosConservaLaExcepcionAcordada() throws Exception {
        indicador(true);
        mascotasIniciales(5);
        mvc.perform(alta()).andExpect(status().isCreated());
        assertThat(cantidad()).isEqualTo(6);
        verificarConsultasPremium(true);
    }

    @ParameterizedTest @ValueSource(ints = {5, 6})
    void pagoVencidoConIndicadorTrueNoPermiteOtraMascotaNiEliminaLasExistentes(int existentes) throws Exception {
        indicador(true);
        mascotasIniciales(existentes);
        cobertura(propietario, "ACTIVA", "authorized", ahora.minusSeconds(1), true, "PREMIUM_MENSUAL");
        rechazarPorLimite();
        assertThat(cantidad()).isEqualTo(existentes);
        mvc.perform(get("/mascotas").with(user(propietario.getEmail()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(existentes));
        assertThat(usuarios.findById(propietario.getIdUsuario()).orElseThrow().isEsPremium()).isTrue();
    }

    @Test void enElInstanteExactoDelVencimientoYaSeAplicaElCupoFree() throws Exception {
        indicador(true); mascotasIniciales(5);
        cobertura(propietario, "CANCELADA", "cancelled", ahora, true, "PREMIUM_MENSUAL");
        rechazarPorLimite();
        assertThat(cantidad()).isEqualTo(5);
    }

    @Test void pagoVencidoConCuatroPermiteLaQuintaComoFree() throws Exception {
        indicador(true); mascotasIniciales(4);
        cobertura(propietario, "ACTIVA", "authorized", ahora.minusSeconds(1), true, "PREMIUM_MENSUAL");
        mvc.perform(alta()).andExpect(status().isCreated());
        assertThat(cantidad()).isEqualTo(5);
    }

    @Test void fechaFuturaSinPagoAprobadoNoPermiteUnaSexta() throws Exception {
        mascotasIniciales(5);
        cobertura(propietario, "ACTIVA", "authorized", ahora.plusSeconds(3600), false, "PREMIUM_MENSUAL");
        rechazarPorLimite();
    }

    @Test void mascotaEliminadaLogicamenteNoConsumeCupo() throws Exception {
        mascotasIniciales(5);
        var eliminada = mascotas.findByPropietario_IdUsuarioAndFechaEliminacionIsNull(propietario.getIdUsuario()).get(0);
        eliminada.setFechaEliminacion(ahora.minusSeconds(60)); mascotas.saveAndFlush(eliminada);
        mvc.perform(alta()).andExpect(status().isCreated());
        assertThat(cantidad()).isEqualTo(5);
        assertThat(mascotas.findById(eliminada.getIdMascota()).orElseThrow().getFechaEliminacion()).isNotNull();
    }

    @Test void elPremiumDeOtraCuentaNoPermiteSuperarElLimite() throws Exception {
        mascotasIniciales(5);
        var otra = usuario(true);
        cobertura(otra, "ACTIVA", "authorized", ahora.plusSeconds(3600), true, "PREMIUM_ANUAL");
        mvc.perform(alta().param("propietarioId", otra.getIdUsuario().toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("LIMITE_MASCOTAS_ALCANZADO"));
        assertThat(cantidad()).isEqualTo(5);
        assertThat(mascotas.countByPropietario_IdUsuarioAndFechaEliminacionIsNull(otra.getIdUsuario())).isZero();
    }

    @Test void dosAltasConcurrentesConCuatroMascotasSoloPermitenUnaMas() throws Exception {
        mascotasIniciales(4);
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var inicio = new CountDownLatch(1);
            Callable<Integer> trabajo = () -> { inicio.await(5, TimeUnit.SECONDS);
                return mvc.perform(alta()).andReturn().getResponse().getStatus(); };
            var a = ejecutor.submit(trabajo); var b = ejecutor.submit(trabajo); inicio.countDown();
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(cantidad()).isEqualTo(5);
        assertThat(auditorias()).isEqualTo(1);
    }

    private MockHttpServletRequestBuilder alta() {
        return post("/mascotas").with(user(propietario.getEmail())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Nueva mascota\",\"especie\":\"Perro\",\"fechaNacimiento\":\"2020-01-01\",\"fechaAproximada\":false}");
    }
    private void rechazarPorLimite() throws Exception {
        verificarConsultasPremium(false);
        mvc.perform(alta()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("LIMITE_MASCOTAS_ALCANZADO"));
    }
    private void verificarConsultasPremium(boolean esperado) throws Exception {
        for (var ruta : List.of("/usuario/me", "/suscripciones/me")) {
            mvc.perform(get(ruta).with(user(propietario.getEmail())))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.esPremium").value(esperado));
        }
    }
    private long cantidad() {
        return mascotas.countByPropietario_IdUsuarioAndFechaEliminacionIsNull(propietario.getIdUsuario());
    }
    private int auditorias() {
        return sql.queryForObject("SELECT count(*) FROM log_auditoria WHERE actor_id = ? AND entidad_afectada = 'mascota'",
                Integer.class, propietario.getIdUsuario());
    }
    private Usuario usuario(boolean premium) {
        var u = new Usuario(); u.setNombre("Mascotas Premium QA");
        u.setEmail("mascotas-premium-" + UUID.randomUUID() + "@example.test");
        u.setPasswordHash("hash-ficticio-no-utilizable"); u.setEsPremium(premium);
        u = usuarios.saveAndFlush(u); cuentas.add(u.getIdUsuario()); return u;
    }
    private void indicador(boolean premium) {
        propietario.setEsPremium(premium); propietario = usuarios.saveAndFlush(propietario);
    }
    private void mascotasIniciales(int cantidad) {
        for (int i = 0; i < cantidad; i++) {
            var m = new Mascota(); m.setPropietario(propietario); m.setNombre("Mascota QA " + i);
            m.setEspecie("Perro"); m.setFechaNacimiento(LocalDate.of(2020, 1, 1));
            mascotas.saveAndFlush(m);
        }
    }
    private void cobertura(Usuario u, String estado, String remoto, Instant vencimiento, boolean aprobado, String plan) {
        var s = new Suscripcion(); s.setUsuario(u); s.setTipoPlan(plan);
        s.setEstadoCatalogo(estados.findByEntidadAndNombre("SUSCRIPCION", estado).orElseThrow());
        s.setEstadoMercadopago(remoto); s.setFechaVencimiento(vencimiento); s = suscripciones.saveAndFlush(s);
        var p = new Pago(); p.setSuscripcion(s); p.setMonto(new BigDecimal("PREMIUM_ANUAL".equals(plan) ? "40000" : "4000"));
        p.setFechaPago(ahora.minusSeconds(3600));
        p.setEstadoCatalogo(estados.findByEntidadAndNombre("PAGO", aprobado ? "APROBADO" : "RECHAZADO").orElseThrow());
        pagos.saveAndFlush(p);
    }
}
