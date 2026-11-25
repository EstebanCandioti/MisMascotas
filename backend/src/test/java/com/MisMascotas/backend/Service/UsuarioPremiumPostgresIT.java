package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.MisMascotas.backend.Entity.Pago;
import com.MisMascotas.backend.Entity.Suscripcion;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Integration.MercadoPagoGateway;
import com.MisMascotas.backend.Repository.*;
import tools.jackson.databind.json.JsonMapper;

/** Sesiones y perfil reales con PostgreSQL de CI; correo y Mercado Pago simulados. */
@SpringBootTest
@ActiveProfiles("ci")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_PREMIUM_IT", matches = "true")
class UsuarioPremiumPostgresIT {
    @Autowired UsuarioRepository usuarios;
    @Autowired SuscripcionRepository suscripciones;
    @Autowired PagoRepository pagos;
    @Autowired EstadoRepository estados;
    @Autowired CodigoVerificacionRepository codigos;
    @Autowired PasswordEncoder codificador;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate sql;
    @MockitoBean Clock reloj;
    @MockitoBean EmailService correo;
    @MockitoBean MercadoPagoGateway proveedor;
    final Instant ahora = Instant.parse("2030-01-31T15:00:00Z");
    final List<UUID> cuentas = new ArrayList<>();
    final JsonMapper json = JsonMapper.builder().build();
    static final String CLAVE_PRUEBA = "ClaveFicticiaPerfil2026";

    @BeforeEach void preparar() {
        when(reloj.instant()).thenReturn(ahora);
    }

    @AfterEach void limpiar() {
        for (var id : cuentas) {
            sql.update("DELETE FROM log_auditoria WHERE actor_id = ?", id);
            sql.update("DELETE FROM evento_auditoria_pendiente WHERE actor_id = ?", id);
            sql.update("UPDATE refresh_token SET reemplazado_por = NULL WHERE usuario_id = ?", id);
            sql.update("DELETE FROM refresh_token WHERE usuario_id = ?", id);
            sql.update("DELETE FROM codigo_verificacion WHERE usuario_id = ?", id);
            sql.update("DELETE FROM pago WHERE suscripcion_id IN (SELECT id_suscripcion FROM suscripcion WHERE usuario_id = ?)", id);
            sql.update("DELETE FROM suscripcion WHERE usuario_id = ?", id);
            sql.update("DELETE FROM usuario WHERE id_usuario = ?", id);
        }
        verifyNoInteractions(proveedor);
    }

    @ParameterizedTest
    @CsvSource({
        "false,NINGUNA,PREMIUM_MENSUAL,false",
        "true,NINGUNA,PREMIUM_MENSUAL,true",
        "false,VIGENTE,PREMIUM_MENSUAL,true",
        "false,VIGENTE,PREMIUM_ANUAL,true",
        "false,CANCELADA,PREMIUM_MENSUAL,true",
        "false,PAUSADA,PREMIUM_MENSUAL,true",
        "true,VENCIDA,PREMIUM_MENSUAL,false",
        "true,EXACTA,PREMIUM_MENSUAL,false",
        "false,RECHAZADA,PREMIUM_MENSUAL,false"
    })
    void perfilYSuscripcionTrasLoginYRefreshCoincidenSinEscribir(boolean indicador, String caso, String plan,
            boolean esperado) throws Exception {
        var usuario = usuario(indicador);
        if (!"NINGUNA".equals(caso)) cobertura(usuario, caso, plan);
        boolean coberturaVigente = Set.of("VIGENTE", "CANCELADA", "PAUSADA").contains(caso);
        var sesion = iniciarSesion(usuario);
        verificarConsultasSinEscrituras(usuario, sesion.accessToken(), esperado, coberturaVigente);

        var renovada = renovar(sesion);
        assertThat(renovada.refreshToken()).isNotEqualTo(sesion.refreshToken());
        verificarConsultasSinEscrituras(usuario, renovada.accessToken(), esperado, coberturaVigente);
        assertThat(usuarios.findById(usuario.getIdUsuario()).orElseThrow().isEsPremium()).isEqualTo(indicador);
    }

    @Test void elMismoTokenReflejaElVencimientoSinEsperarUnNuevoLogin() throws Exception {
        var usuario = usuario(false);
        cobertura(usuario, "VIGENTE", "PREMIUM_MENSUAL");
        var sesion = iniciarSesion(usuario);
        verificarConsultasSinEscrituras(usuario, sesion.accessToken(), true, true);

        when(reloj.instant()).thenReturn(ahora.plusSeconds(3600));
        verificarConsultasSinEscrituras(usuario, sesion.accessToken(), false, false);
        verificarConsultasSinEscrituras(usuario, renovar(sesion).accessToken(), false, false);
    }

    @Test void tokenDeUnaCuentaNoExponeElPerfilNiElPremiumDeOtra() throws Exception {
        var free = usuario(false);
        var premium = usuario(true);
        cobertura(premium, "VIGENTE", "PREMIUM_ANUAL");
        var sesion = iniciarSesion(free);
        mvc.perform(get("/usuario/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + sesion.accessToken())
                        .param("usuarioId", premium.getIdUsuario().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idUsuario").value(free.getIdUsuario().toString()))
                .andExpect(jsonPath("$.email").value(free.getEmail()))
                .andExpect(jsonPath("$.esPremium").value(false));
        mvc.perform(get("/suscripciones/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + sesion.accessToken())
                        .param("usuarioId", premium.getIdUsuario().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esPremium").value(false))
                .andExpect(jsonPath("$.coberturaPagadaVigente").value(false))
                .andExpect(jsonPath("$.suscripcion").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test void perfilSinSesionContinuaProtegido() throws Exception {
        mvc.perform(get("/usuario/me")).andExpect(status().isUnauthorized());
    }

    private void verificarConsultasSinEscrituras(Usuario usuario, String token, boolean esperado,
            boolean coberturaVigente) throws Exception {
        var antes = estadoPersistido(usuario);
        mvc.perform(get("/usuario/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(4)))
                .andExpect(jsonPath("$.idUsuario").value(usuario.getIdUsuario().toString()))
                .andExpect(jsonPath("$.nombre").value(usuario.getNombre()))
                .andExpect(jsonPath("$.email").value(usuario.getEmail()))
                .andExpect(jsonPath("$.esPremium").value(esperado));
        var consulta = mvc.perform(get("/suscripciones/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(5)))
                .andExpect(jsonPath("$.esPremium").value(esperado))
                .andExpect(jsonPath("$.coberturaPagadaVigente").value(coberturaVigente));
        if (coberturaVigente) {
            consulta.andExpect(jsonPath("$.fechaFinCobertura").value(ahora.plusSeconds(3600).toString()))
                    .andExpect(jsonPath("$.suscripcion.coberturaVigente").value(true));
        } else {
            consulta.andExpect(jsonPath("$.fechaFinCobertura").value(org.hamcrest.Matchers.nullValue()));
        }
        assertThat(estadoPersistido(usuario)).isEqualTo(antes);
    }

    private List<?> estadoPersistido(Usuario usuario) {
        var id = usuario.getIdUsuario();
        return List.of(
                sql.queryForList("SELECT * FROM usuario WHERE id_usuario = ?", id),
                sql.queryForList("SELECT * FROM suscripcion WHERE usuario_id = ? ORDER BY id_suscripcion", id),
                sql.queryForList("SELECT * FROM pago WHERE suscripcion_id IN (SELECT id_suscripcion FROM suscripcion WHERE usuario_id = ?) ORDER BY id_pago", id),
                sql.queryForList("SELECT * FROM log_auditoria WHERE actor_id = ? ORDER BY id_log", id));
    }

    private Sesion iniciarSesion(Usuario usuario) throws Exception {
        // El filtro JWT distingue las rutas públicas por servletPath, como en el servidor real.
        var login = respuesta(mvc.perform(post("/auth/login").servletPath("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", usuario.getEmail(), "password", CLAVE_PRUEBA))))
                .andExpect(status().isOk()).andReturn());
        var codigo = codigos.findFirstByUsuario_IdUsuarioAndUsadoFalseOrderByCreadoEnDesc(usuario.getIdUsuario()).orElseThrow();
        var resultado = mvc.perform(post("/auth/verificar-codigo").servletPath("/auth/verificar-codigo")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.get("token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("codigo", codigo.getCodigo()))))
                .andExpect(status().isOk()).andReturn();
        return sesion(resultado);
    }

    private Sesion renovar(Sesion anterior) throws Exception {
        return sesion(mvc.perform(post("/auth/refresh").servletPath("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("refreshToken", anterior.refreshToken()))))
                .andExpect(status().isOk()).andReturn());
    }

    private Sesion sesion(MvcResult resultado) throws Exception {
        var datos = respuesta(resultado);
        assertThat(datos.keySet().stream().map(Object::toString).toList())
                .containsExactlyInAnyOrder("accessToken", "refreshToken", "tipo", "accessTokenExpiraEn");
        assertThat(datos.get("tipo")).isEqualTo("Bearer");
        return new Sesion((String) datos.get("accessToken"), (String) datos.get("refreshToken"));
    }

    private Map<?, ?> respuesta(MvcResult resultado) throws Exception {
        return json.readValue(resultado.getResponse().getContentAsString(), Map.class);
    }

    private Usuario usuario(boolean premium) {
        var usuario = new Usuario();
        usuario.setNombre("Perfil Premium QA");
        usuario.setEmail("perfil-premium-" + UUID.randomUUID() + "@example.test");
        usuario.setPasswordHash(codificador.encode(CLAVE_PRUEBA));
        usuario.setEsPremium(premium);
        usuario = usuarios.saveAndFlush(usuario);
        cuentas.add(usuario.getIdUsuario());
        return usuario;
    }

    private void cobertura(Usuario usuario, String caso, String plan) {
        var suscripcion = new Suscripcion();
        suscripcion.setUsuario(usuario);
        suscripcion.setTipoPlan(plan);
        suscripcion.setEstadoCatalogo(estados.findByEntidadAndNombre("SUSCRIPCION",
                "CANCELADA".equals(caso) ? "CANCELADA" : "ACTIVA").orElseThrow());
        suscripcion.setEstadoMercadopago(switch (caso) {
            case "CANCELADA" -> "cancelled";
            case "PAUSADA" -> "paused";
            default -> "authorized";
        });
        suscripcion.setFechaVencimiento(switch (caso) {
            case "VENCIDA" -> ahora.minusSeconds(1);
            case "EXACTA" -> ahora;
            default -> ahora.plusSeconds(3600);
        });
        suscripcion = suscripciones.saveAndFlush(suscripcion);
        var pago = new Pago();
        pago.setSuscripcion(suscripcion);
        pago.setMonto(new BigDecimal("PREMIUM_ANUAL".equals(plan) ? "40000" : "4000"));
        pago.setFechaPago(ahora.minusSeconds(3600));
        pago.setEstadoCatalogo(estados.findByEntidadAndNombre("PAGO",
                "RECHAZADA".equals(caso) ? "RECHAZADO" : "APROBADO").orElseThrow());
        pagos.saveAndFlush(pago);
    }

    private record Sesion(String accessToken, String refreshToken) {}
}
