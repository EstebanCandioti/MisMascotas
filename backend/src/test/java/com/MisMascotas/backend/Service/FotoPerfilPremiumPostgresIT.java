package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.MisMascotas.backend.Entity.*;
import com.MisMascotas.backend.Integration.MercadoPagoGateway;
import com.MisMascotas.backend.Repository.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Contrato de presencia del JSON y política Premium con HTTP y PostgreSQL reales. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("ci")
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_PREMIUM_IT", matches = "true")
class FotoPerfilPremiumPostgresIT {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate sql;
    @Autowired UsuarioRepository usuarios;
    @Autowired MascotaRepository mascotas;
    @Autowired AccesoCompartidoRepository accesos;
    @Autowired SuscripcionRepository suscripciones;
    @Autowired PagoRepository pagos;
    @Autowired EstadoRepository estados;
    @MockitoBean Clock reloj;
    @MockitoBean EmailService correo;
    @MockitoBean MercadoPagoGateway proveedor;
    final JsonMapper json = JsonMapper.builder().build();
    final Instant ahora = Instant.parse("2030-01-31T15:00:00Z");
    final String fotoAnterior = "https://example.test/anterior.jpg";
    final String fotoNueva = "https://example.test/nueva.jpg";
    final List<UUID> cuentas = new ArrayList<>();
    Usuario propietario, familia, cuidador;
    Mascota mascota;

    @BeforeEach void preparar() {
        when(reloj.instant()).thenReturn(ahora);
        propietario = usuario(false); familia = usuario(false); cuidador = usuario(true);
        acceso(familia, PerfilCompartido.Familia); acceso(cuidador, PerfilCompartido.Cuidador);
        mascota = new Mascota(); mascota.setPropietario(propietario); mascota.setNombre("Nombre previo");
        mascota.setEspecie("Perro"); mascota.setFotoPerfil(fotoAnterior); mascota.setNotas("Notas previas");
        mascota.setPesoActual(new BigDecimal("10.00")); mascota.setFechaNacimiento(LocalDate.of(2020, 1, 1));
        mascota = mascotas.saveAndFlush(mascota);
    }

    @AfterEach void limpiar() {
        for (var id : cuentas) {
            sql.update("DELETE FROM evento_auditoria_pendiente WHERE actor_id=?", id);
            sql.update("DELETE FROM log_auditoria WHERE actor_id=?", id);
            sql.update("DELETE FROM acceso_compartido WHERE propietario_id=? OR invitado_id=?", id, id);
            sql.update("DELETE FROM mascota WHERE propietario_id=?", id);
            sql.update("DELETE FROM pago WHERE suscripcion_id IN (SELECT id_suscripcion FROM suscripcion WHERE usuario_id=?)", id);
            sql.update("DELETE FROM suscripcion WHERE usuario_id=?", id);
            sql.update("DELETE FROM usuario WHERE id_usuario=?", id);
        }
        verifyNoInteractions(proveedor, correo);
    }

    @ParameterizedTest @ValueSource(strings = {"free", "vencido", "exacto", "sin_pago", "sin_fecha"})
    void sinPremiumNoAsignaNiReemplazaFotoYElRechazoEsAtomico(String caso) throws Exception {
        if (!"free".equals(caso)) cobertura(caso);
        var antes = estadoDatos();
        assertThat(llamar(propietario, "POST", "/mascotas", conFoto(fotoNueva), 403).get("codigo").asText()).isEqualTo("PREMIUM_REQUERIDO");
        assertThat(llamar(propietario, "PUT", ruta(), conFoto(fotoNueva), 403).get("codigo").asText()).isEqualTo("PREMIUM_REQUERIDO");
        assertThat(estadoDatos()).isEqualTo(antes);
        // Tampoco se permite la primera foto en una mascota existente sin imagen.
        sql.update("UPDATE mascota SET foto_perfil=NULL WHERE id_mascota=?", mascota.getIdMascota());
        antes = estadoDatos();
        llamar(propietario, "PUT", ruta(), conFoto(fotoNueva), 403);
        assertThat(estadoDatos()).isEqualTo(antes);
    }

    @ParameterizedTest @ValueSource(strings = {"free", "vencido", "exacto"})
    void sinPremiumPuedeConservarPorAusenciaOPorMismaUrlYRetirarConNull(String caso) throws Exception {
        if (!"free".equals(caso)) cobertura(caso);
        var ausente = llamar(propietario, "PUT", ruta(), datos("Ausencia"), 200);
        assertThat(ausente.get("fotoPerfil").asText()).isEqualTo(fotoAnterior);
        assertThat(ausente.get("nombre").asText()).isEqualTo("Ausencia");
        var misma = llamar(propietario, "PUT", ruta(), conFoto(fotoAnterior), 200);
        assertThat(misma.get("fotoPerfil").asText()).isEqualTo(fotoAnterior);
        var retirada = llamar(propietario, "PUT", ruta(), conFoto(null), 200);
        assertThat(retirada.get("fotoPerfil").isNull()).isTrue();
        assertThat(mascotas.findById(mascota.getIdMascota()).orElseThrow().getFotoPerfil()).isNull();
        assertThat(llamar(propietario, "PUT", ruta(), datos("Sigue sin foto"), 200).get("fotoPerfil").isNull()).isTrue();
        assertThat(sql.queryForObject("SELECT count(*) FROM evento_auditoria_pendiente WHERE actor_id=? AND entidad_afectada='mascota'", Integer.class, propietario.getIdUsuario())).isEqualTo(4);
    }

    @ParameterizedTest @ValueSource(strings = {"free", "vencido"})
    void altaGeneralSinFotoAusenteONullSiguePermitidaEnFree(String caso) throws Exception {
        if (!"free".equals(caso)) cobertura(caso);
        for (var solicitud : List.of(datos("Sin campo"), conFoto(null))) {
            var nueva = llamar(propietario, "POST", "/mascotas", solicitud, 201);
            assertThat(nueva.get("fotoPerfil").isNull()).isTrue();
            assertThat(nueva.get("propietarioId").asText()).isEqualTo(propietario.getIdUsuario().toString());
            assertThat(nueva.has("fotoPerfilInformada")).isFalse();
        }
        assertThat(mascotas.countByPropietario_IdUsuarioAndFechaEliminacionIsNull(propietario.getIdUsuario())).isEqualTo(3);
    }

    @ParameterizedTest @ValueSource(strings = {"manual", "mensual", "anual", "cancelada", "pausada"})
    void premiumAsignaReemplazaConservaYRetiraConElDtoActual(String caso) throws Exception {
        cobertura(caso);
        var alta = llamar(propietario, "POST", "/mascotas", conFoto(fotoNueva), 201);
        assertThat(alta.get("fotoPerfil").asText()).isEqualTo(fotoNueva);
        assertThat(llamar(propietario, "PUT", ruta(), conFoto(fotoNueva), 200).get("fotoPerfil").asText()).isEqualTo(fotoNueva);
        assertThat(llamar(propietario, "PUT", ruta(), datos("Solo nombre"), 200).get("fotoPerfil").asText()).isEqualTo(fotoNueva);
        assertThat(llamar(propietario, "PUT", ruta(), conFoto(null), 200).get("fotoPerfil").isNull()).isTrue();
    }

    @Test void elVencimientoNoBorraLaFotoYSeReflejaSinCambiarLaSesion() throws Exception {
        cobertura("mensual");
        llamar(propietario, "PUT", ruta(), conFoto(fotoNueva), 200);
        when(reloj.instant()).thenReturn(ahora.plusSeconds(3600));
        var antes = estadoDatos();
        llamar(propietario, "PUT", ruta(), conFoto(fotoAnterior), 403);
        assertThat(estadoDatos()).isEqualTo(antes);
        for (var actor : List.of(propietario, familia, cuidador))
            assertThat(llamar(actor, "GET", ruta(), null, 200).get("fotoPerfil").asText()).isEqualTo(fotoNueva);
        assertThat(llamar(propietario, "PUT", ruta(), datos("Edición Free"), 200).get("fotoPerfil").asText()).isEqualTo(fotoNueva);
        assertThat(llamar(propietario, "PUT", ruta(), conFoto(null), 200).get("fotoPerfil").isNull()).isTrue();
    }

    @ParameterizedTest @ValueSource(strings = {"free", "manual"})
    void invitadosLeenSinImportarSuPlanPeroNoEditanNiRetiranLaFoto(String caso) throws Exception {
        if ("manual".equals(caso)) cobertura(caso);
        var antes = estadoDatos();
        for (var actor : List.of(familia, cuidador)) {
            assertThat(llamar(actor, "GET", ruta(), null, 200).get("fotoPerfil").asText()).isEqualTo(fotoAnterior);
            for (var solicitud : List.of(datos("No guardar"), conFoto(null), conFoto(fotoNueva)))
                assertThat(llamar(actor, "PUT", ruta(), solicitud, 403).get("codigo").asText()).isEqualTo("ACCESO_DENEGADO");
        }
        assertThat(estadoDatos()).isEqualTo(antes);
    }

    @Test void elIndicadorInternoNoPermiteSaltarElControlNiRetirarPorAusencia() throws Exception {
        var evasion = conFoto(fotoNueva); evasion.put("fotoPerfilInformada", false);
        var antes = estadoDatos();
        llamar(propietario, "PUT", ruta(), evasion, 403);
        llamar(propietario, "POST", "/mascotas", evasion, 403);
        assertThat(estadoDatos()).isEqualTo(antes);
        var ausente = datos("No retirar"); ausente.put("fotoPerfilInformada", true);
        assertThat(llamar(propietario, "PUT", ruta(), ausente, 200).get("fotoPerfil").asText()).isEqualTo(fotoAnterior);
        var retirada = conFoto(null); retirada.put("fotoPerfilInformada", false);
        assertThat(llamar(propietario, "PUT", ruta(), retirada, 200).get("fotoPerfil").isNull()).isTrue();
    }

    @Test void noCambiaValidacionesNiReviveMascotasEliminadas() throws Exception {
        var invalida = conFoto(fotoNueva); invalida.put("nombre", " ");
        var antes = estadoDatos();
        llamar(propietario, "PUT", ruta(), invalida, 400);
        assertThat(estadoDatos()).isEqualTo(antes);
        llamar(propietario, "DELETE", ruta(), null, 204);
        llamar(propietario, "PUT", ruta(), conFoto(null), 404);
        llamar(familia, "GET", ruta(), null, 404);
    }

    private Map<String, Object> estadoDatos() {
        var r = new HashMap<String, Object>();
        r.put("mascotas", sql.queryForList("SELECT * FROM mascota WHERE propietario_id=? ORDER BY id_mascota", propietario.getIdUsuario()));
        r.put("propietario", sql.queryForList("SELECT * FROM usuario WHERE id_usuario=?", propietario.getIdUsuario()));
        r.put("suscripciones", sql.queryForList("SELECT * FROM suscripcion WHERE usuario_id=? ORDER BY id_suscripcion", propietario.getIdUsuario()));
        r.put("pagos", sql.queryForList("SELECT p.* FROM pago p JOIN suscripcion s ON p.suscripcion_id=s.id_suscripcion WHERE s.usuario_id=? ORDER BY p.id_pago", propietario.getIdUsuario()));
        for (var uid : cuentas) {
            r.put("auditoria-"+uid, sql.queryForList("SELECT * FROM log_auditoria WHERE actor_id=? ORDER BY id_log", uid));
            r.put("pendientes-"+uid, sql.queryForList("SELECT * FROM evento_auditoria_pendiente WHERE actor_id=? ORDER BY id_evento", uid));
        }
        return r;
    }

    private void cobertura(String caso) {
        if (List.of("manual", "vencido", "exacto", "sin_fecha").contains(caso)) {
            propietario.setEsPremium(true); propietario = usuarios.saveAndFlush(propietario);
        }
        if ("manual".equals(caso)) return;
        var s = new Suscripcion(); s.setUsuario(propietario); s.setTipoPlan("anual".equals(caso) ? "PREMIUM_ANUAL" : "PREMIUM_MENSUAL");
        s.setEstadoCatalogo(estados.findByEntidadAndNombre("SUSCRIPCION", "cancelada".equals(caso) ? "CANCELADA" : "ACTIVA").orElseThrow());
        s.setEstadoMercadopago("cancelada".equals(caso) ? "cancelled" : "pausada".equals(caso) ? "paused" : "authorized");
        s.setFechaVencimiento("sin_fecha".equals(caso) ? null : "exacto".equals(caso) ? ahora : "vencido".equals(caso) ? ahora.minusSeconds(1) : ahora.plusSeconds(3600));
        s = suscripciones.saveAndFlush(s);
        var p = new Pago(); p.setSuscripcion(s); p.setMonto(new BigDecimal("anual".equals(caso) ? "40000" : "4000"));
        p.setFechaPago(ahora.minusSeconds(3600));
        p.setEstadoCatalogo(estados.findByEntidadAndNombre("PAGO", "sin_pago".equals(caso) ? "RECHAZADO" : "APROBADO").orElseThrow());
        pagos.saveAndFlush(p);
    }

    private Usuario usuario(boolean premium) {
        var u = new Usuario(); u.setNombre("Foto de perfil QA"); u.setEmail(UUID.randomUUID()+"@example.test");
        u.setPasswordHash("hash-ficticio"); u.setEsPremium(premium); u = usuarios.saveAndFlush(u); cuentas.add(u.getIdUsuario()); return u;
    }
    private void acceso(Usuario invitado, PerfilCompartido perfil) { accesos.saveAndFlush(AccesoCompartido.builder().propietario(propietario).invitado(invitado).perfil(perfil).fechaIngreso(ahora).build()); }
    private String ruta() { return "/mascotas/" + mascota.getIdMascota(); }
    private Map<String, Object> datos(String nombre) { return new LinkedHashMap<>(Map.of("nombre", nombre, "especie", "Perro", "fechaNacimiento", "2020-01-01", "fechaAproximada", false, "pesoActual", 12.5, "notas", "Notas nuevas")); }
    private Map<String, Object> conFoto(String foto) { var r = datos("Nombre nuevo"); r.put("fotoPerfil", foto); return r; }
    private JsonNode llamar(Usuario actor, String metodo, String ruta, Object cuerpo, int esperado) throws Exception {
        var peticion = request(HttpMethod.valueOf(metodo), ruta).with(user(actor.getEmail()));
        if (cuerpo != null) peticion.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo));
        var respuesta = mvc.perform(peticion).andReturn().getResponse();
        assertThat(respuesta.getStatus()).as(metodo+" "+ruta+": "+respuesta.getContentAsString()).isEqualTo(esperado);
        return respuesta.getContentAsString().isEmpty() ? null : json.readTree(respuesta.getContentAsString());
    }
}
