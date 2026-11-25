package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.MisMascotas.backend.Entity.*;
import com.MisMascotas.backend.Repository.*;
import com.MisMascotas.backend.Integration.MercadoPagoGateway;
import com.MisMascotas.backend.Security.JwtService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Matriz real por HTTP: cada caso confirma sus transacciones y limpia solo sus cuentas. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("ci")
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_CUIDADO_IT", matches = "true")
class CuidadoPermisosPostgresIT {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired MascotaRepository mascotas;
    @Autowired AccesoCompartidoRepository accesos;
    @Autowired AlbumMascotaRepository relaciones;
    @Autowired AlbumRepository albumes;
    @Autowired JdbcTemplate sql;
    @Autowired JwtService jwt;
    @MockitoBean Clock reloj;
    @MockitoBean EmailService correo;
    @MockitoBean MercadoPagoGateway proveedor;
    final JsonMapper json = JsonMapper.builder().build();
    // Todavía es 1 de enero en Argentina, aunque UTC ya marca el 2.
    final Instant ahora = Instant.parse("2030-01-02T02:30:00Z");
    final List<UUID> cuentas = new ArrayList<>();
    Usuario propietario, familia, cuidador, ajeno;
    Mascota mascota, propiaFamilia, otra;
    String evento, recordatorio, album, foto, badge;

    @BeforeEach void preparar() throws Exception {
        when(reloj.instant()).thenReturn(ahora);
        propietario = usuario(); familia = usuario(); cuidador = usuario(); ajeno = usuario();
        mascota = mascota(propietario); propiaFamilia = mascota(familia); otra = mascota(ajeno);
        acceso(propietario, familia, PerfilCompartido.Familia);
        acceso(propietario, cuidador, PerfilCompartido.Cuidador);
        evento = llamar(propietario, "POST", rutaMascota("eventos-clinicos"), clinico(), 201).get("idEvento").asText();
        recordatorio = nuevoRecordatorio(ahora);
        album = llamar(propietario, "POST", rutaMascota("albumes"), datosAlbum(), 201).get("idAlbum").asText();
        foto = llamar(propietario, "POST", "/albumes/" + album + "/fotos", datosFoto(), 201).get("idFoto").asText();
        badge = llamar(propietario, "POST", rutaMascota("badges"), datosBadge(), 201).get("idBadge").asText();
    }

    @AfterEach void limpiar() {
        for (var id : cuentas) {
            sql.update("DELETE FROM evento_auditoria_pendiente WHERE actor_id=?", id);
            sql.update("DELETE FROM log_auditoria WHERE actor_id=?", id);
            sql.update("DELETE FROM invitacion WHERE propietario_id=?", id);
        }
        for (var id : cuentas) {
            sql.update("DELETE FROM foto WHERE album_id IN (SELECT id_album FROM album WHERE creado_por_id=?)", id);
            sql.update("DELETE FROM album_mascota WHERE id_album IN (SELECT id_album FROM album WHERE creado_por_id=?)", id);
            sql.update("DELETE FROM album WHERE creado_por_id=?", id);
            sql.update("DELETE FROM badge WHERE mascota_id IN (SELECT id_mascota FROM mascota WHERE propietario_id=?)", id);
            sql.update("DELETE FROM evento_clinico WHERE mascota_id IN (SELECT id_mascota FROM mascota WHERE propietario_id=?)", id);
            sql.update("DELETE FROM recordatorio WHERE mascota_id IN (SELECT id_mascota FROM mascota WHERE propietario_id=?)", id);
            sql.update("DELETE FROM acceso_compartido WHERE propietario_id=? OR invitado_id=?", id, id);
        }
        for (var id : cuentas) sql.update("DELETE FROM mascota WHERE propietario_id=?", id);
        for (var id : cuentas) sql.update("DELETE FROM usuario WHERE id_usuario=?", id);
        verifyNoInteractions(proveedor, correo);
    }

    @Test void mascotasPropiasCompartidasFuturasYEliminadasSinAccesoTransitivo() throws Exception {
        var futura = mascota(propietario);
        var eliminada = mascota(propietario); eliminada.setFechaEliminacion(ahora); mascotas.saveAndFlush(eliminada);
        acceso(familia, ajeno, PerfilCompartido.Familia);
        assertThat(ids(llamar(familia, "GET", "/mascotas", null, 200), "idMascota"))
                .containsExactlyInAnyOrder(mascota.getIdMascota().toString(), propiaFamilia.getIdMascota().toString(), futura.getIdMascota().toString());
        assertThat(ids(llamar(ajeno, "GET", "/mascotas", null, 200), "idMascota"))
                .containsExactlyInAnyOrder(otra.getIdMascota().toString(), propiaFamilia.getIdMascota().toString());
        llamar(ajeno, "GET", "/mascotas/" + mascota.getIdMascota(), null, 403);
        llamar(familia, "GET", "/mascotas/" + eliminada.getIdMascota(), null, 404);
    }

    @ParameterizedTest @ValueSource(strings = {"Familia", "Cuidador"})
    void ambosPerfilesLeenTodosLosRecursosPeroNoGestionanMascotaNiMultimedia(String perfil) throws Exception {
        var actor = actor(perfil);
        for (String ruta : List.of("/mascotas/" + mascota.getIdMascota(), rutaMascota("eventos-clinicos"),
                "/eventos-clinicos/" + evento, rutaMascota("albumes"), "/albumes/" + album,
                "/albumes/" + album + "/fotos", "/fotos/" + foto, rutaMascota("badges"), "/badges/" + badge))
            llamar(actor, "GET", ruta, null, 200);
        llamar(actor, "PUT", "/mascotas/" + mascota.getIdMascota(), Map.of("nombre", "Cambio", "especie", "Perro", "fechaNacimiento", "2020-01-01", "fechaAproximada", false), 403);
        llamar(actor, "DELETE", "/mascotas/" + mascota.getIdMascota(), null, 403);
        llamar(actor, "POST", rutaMascota("albumes"), datosAlbum(), 403);
        llamar(actor, "PUT", "/albumes/" + album, datosAlbum(), 403);
        llamar(actor, "DELETE", "/albumes/" + album, null, 403);
        llamar(actor, "POST", "/albumes/" + album + "/fotos", datosFoto(), 403);
        llamar(actor, "DELETE", "/fotos/" + foto, null, 403);
        llamar(actor, "POST", rutaMascota("badges"), datosBadge(), 403);
        llamar(actor, "DELETE", "/badges/" + badge, null, 403);
    }

    @Test void unAjenoNoConsultaRecursosPorIdNiPorListados() throws Exception {
        for (String ruta : List.of(rutaMascota("eventos-clinicos"), "/eventos-clinicos/" + evento,
                rutaMascota("recordatorios"), "/recordatorios/" + recordatorio, rutaMascota("albumes"), "/albumes/" + album,
                "/albumes/" + album + "/fotos", "/fotos/" + foto, rutaMascota("badges"), "/badges/" + badge))
            llamar(ajeno, "GET", ruta, null, 403);
    }

    @Test void familiaGestionaClinicaYAuditaAlActorRealSinCambiarLaMascota() throws Exception {
        String nuevo = llamar(familia, "POST", rutaMascota("eventos-clinicos"), clinico(), 201).get("idEvento").asText();
        assertThat(llamar(cuidador, "GET", "/eventos-clinicos/" + nuevo, null, 200).get("registradoPorId").asText()).isEqualTo(familia.getIdUsuario().toString());
        var edicion = new HashMap<>(clinico()); edicion.put("mascotaId", otra.getIdMascota().toString());
        assertThat(llamar(familia, "PUT", "/eventos-clinicos/" + nuevo, edicion, 200).get("mascotaId").asText()).isEqualTo(mascota.getIdMascota().toString());
        llamar(familia, "DELETE", "/eventos-clinicos/" + nuevo, null, 204);
        assertThat(sql.queryForObject("SELECT count(*) FROM log_auditoria WHERE actor_id=? AND entidad_afectada='evento_clinico'", Long.class, familia.getIdUsuario())).isEqualTo(3);
        llamar(familia, "GET", "/eventos-clinicos/" + nuevo, null, 404);
    }

    @Test void cuidadorNoGestionaClinica() throws Exception {
        llamar(cuidador, "POST", rutaMascota("eventos-clinicos"), clinico(), 403);
        llamar(cuidador, "PUT", "/eventos-clinicos/" + evento, clinico(), 403);
        llamar(cuidador, "DELETE", "/eventos-clinicos/" + evento, null, 403);
    }

    @Test void familiaGestionaRecordatoriosYComparteConfirmacionConPropietario() throws Exception {
        var nuevo = llamar(familia, "POST", rutaMascota("recordatorios"), datosRecordatorio(ahora), 201).get("idRecordatorio").asText();
        llamar(familia, "PUT", "/recordatorios/" + nuevo, datosRecordatorio(ahora.plusSeconds(60)), 200);
        llamar(familia, "PATCH", "/recordatorios/" + nuevo + "/confirmacion", null, 200);
        assertThat(llamar(propietario, "GET", "/recordatorios/" + nuevo, null, 200).get("confirmadoPorId").asText()).isEqualTo(familia.getIdUsuario().toString());
        llamar(familia, "DELETE", "/recordatorios/" + nuevo + "/confirmacion", null, 200);
        llamar(familia, "PATCH", "/recordatorios/" + nuevo + "/estado?nuevoEstado=INACTIVO", null, 200);
        llamar(familia, "DELETE", "/recordatorios/" + nuevo, null, 204);
    }

    @Test void cuidadorSoloConfirmaHoyYTodosVenLaMismaConfirmacion() throws Exception {
        var resultado = llamar(cuidador, "PATCH", "/recordatorios/" + recordatorio + "/confirmacion", null, 200);
        assertThat(resultado.get("estado").asText()).isEqualTo("COMPLETADO");
        for (var actor : List.of(propietario, familia, cuidador))
            assertThat(llamar(actor, "GET", "/recordatorios/" + recordatorio, null, 200).get("confirmadoPorId").asText()).isEqualTo(cuidador.getIdUsuario().toString());
        llamar(cuidador, "POST", rutaMascota("recordatorios"), datosRecordatorio(ahora), 403);
        llamar(cuidador, "PUT", "/recordatorios/" + recordatorio, datosRecordatorio(ahora), 403);
        llamar(cuidador, "PATCH", "/recordatorios/" + recordatorio + "/estado?nuevoEstado=INACTIVO", null, 403);
        llamar(cuidador, "DELETE", "/recordatorios/" + recordatorio + "/confirmacion", null, 403);
        llamar(cuidador, "DELETE", "/recordatorios/" + recordatorio, null, 403);
    }

    @Test void diaArgentinoIncluyeInicioYExcluyeMedianocheSiguientePasadosInactivosYEliminados() throws Exception {
        llamar(ajeno, "POST", "/mascotas/" + otra.getIdMascota() + "/recordatorios", datosRecordatorio(ahora), 201);
        var mascotaEliminada = mascota(propietario);
        llamar(propietario, "POST", "/mascotas/" + mascotaEliminada.getIdMascota() + "/recordatorios", datosRecordatorio(ahora), 201);
        mascotaEliminada.setFechaEliminacion(ahora); mascotas.saveAndFlush(mascotaEliminada);
        String inicio = nuevoRecordatorio(Instant.parse("2030-01-01T03:00:00Z"));
        String anterior = nuevoRecordatorio(Instant.parse("2030-01-01T02:59:59Z"));
        String siguiente = nuevoRecordatorio(Instant.parse("2030-01-02T03:00:00Z"));
        String inactivo = nuevoRecordatorio(ahora);
        llamar(propietario, "PATCH", "/recordatorios/" + inactivo + "/estado?nuevoEstado=INACTIVO", null, 200);
        String eliminado = nuevoRecordatorio(ahora);
        llamar(propietario, "DELETE", "/recordatorios/" + eliminado, null, 204);
        for (var id : List.of(anterior, siguiente, inactivo)) {
            for (var actor : List.of(propietario, familia, cuidador)) {
                llamar(actor, "GET", "/recordatorios/" + id, null, 200);
                llamar(actor, "PATCH", "/recordatorios/" + id + "/confirmacion", null, 400);
            }
        }
        assertThat(ids(llamar(cuidador, "GET", rutaMascota("recordatorios"), null, 200), "idRecordatorio")).containsExactlyInAnyOrder(inicio, recordatorio, anterior, siguiente, inactivo);
        assertThat(llamar(cuidador, "GET", "/recordatorios/" + anterior, null, 200).get("situacionTemporal").asText()).isEqualTo("VENCIDO");
        assertThat(llamar(cuidador, "GET", "/recordatorios/" + siguiente, null, 200).get("situacionTemporal").asText()).isEqualTo("PROXIMO");
        assertThat(llamar(cuidador, "GET", "/recordatorios/" + inactivo, null, 200).get("situacionTemporal").asText()).isEqualTo("INACTIVO");
        assertThat(ids(llamar(cuidador, "GET", "/recordatorios/hoy", null, 200), "idRecordatorio")).containsExactly(inicio, recordatorio);
        assertThat(llamar(ajeno, "GET", "/recordatorios/hoy", null, 200).size()).isEqualTo(1);
        when(reloj.instant()).thenReturn(Instant.parse("2030-01-02T03:00:00Z"));
        var vencido = llamar(cuidador, "GET", "/recordatorios/" + recordatorio, null, 200);
        assertThat(vencido.get("situacionTemporal").asText()).isEqualTo("VENCIDO");
        assertThat(vencido.get("puedeConfirmar").asBoolean()).isFalse();
        llamar(cuidador, "PATCH", "/recordatorios/" + recordatorio + "/confirmacion", null, 400);
        assertThat(ids(llamar(cuidador, "GET", "/recordatorios/hoy", null, 200), "idRecordatorio")).containsExactly(siguiente);
    }

    @ParameterizedTest
    @CsvSource({"Propietario,2030-01-01T20:00:00Z", "Familia,2030-01-01T20:00:00Z", "Cuidador,2030-01-01T20:00:00Z",
            "Propietario,2030-01-02T02:59:59Z", "Familia,2030-01-02T02:59:59Z", "Cuidador,2030-01-02T02:59:59Z"})
    void todosConfirmanElDiaCorrespondienteSinLimitePorHora(String perfil, String fecha) throws Exception {
        var actor = actor(perfil);
        var id = nuevoRecordatorio(Instant.parse(fecha));
        var pendiente = llamar(actor, "GET", "/recordatorios/" + id, null, 200);
        assertThat(pendiente.get("puedeConfirmar").asBoolean()).isTrue();
        assertThat(pendiente.get("situacionTemporal").asText()).isEqualTo("PENDIENTE");
        var confirmado = llamar(actor, "PATCH", "/recordatorios/" + id + "/confirmacion", null, 200);
        assertThat(confirmado.get("confirmadoPorId").asText()).isEqualTo(actor.getIdUsuario().toString());
        assertThat(confirmado.get("puedeConfirmar").asBoolean()).isFalse();
        assertThat(confirmado.get("situacionTemporal").asText()).isEqualTo("COMPLETADO");
        for (var otro : List.of(propietario, familia, cuidador))
            llamar(otro, "PATCH", "/recordatorios/" + id + "/confirmacion", null, 400);
        when(reloj.instant()).thenReturn(Instant.parse("2030-01-02T03:00:00Z"));
        assertThat(llamar(actor, "GET", "/recordatorios/" + id, null, 200).get("situacionTemporal").asText()).isEqualTo("COMPLETADO");
    }

    @ParameterizedTest @ValueSource(strings = {"Propietario", "Familia"})
    void cambiarEstadoCompletadoAplicaLasMismasReglasYGuardaAutoria(String perfil) throws Exception {
        var actor = actor(perfil);
        for (var fecha : List.of(ahora.minusSeconds(86400), ahora.plusSeconds(86400))) {
            var id = nuevoRecordatorio(fecha);
            llamar(actor, "PATCH", "/recordatorios/" + id + "/estado?nuevoEstado=COMPLETADO", null, 400);
            assertThat(llamar(actor, "GET", "/recordatorios/" + id, null, 200).get("confirmadoEn").isNull()).isTrue();
        }
        var completo = llamar(actor, "PATCH", "/recordatorios/" + recordatorio + "/estado?nuevoEstado=COMPLETADO", null, 200);
        assertThat(completo.get("confirmadoPorId").asText()).isEqualTo(actor.getIdUsuario().toString());
    }

    @Test void confirmacionesSimultaneasConservanUnSoloAutor() throws Exception {
        try (var hilos = Executors.newFixedThreadPool(2)) {
            var inicio = new CountDownLatch(1);
            Callable<Integer> confirmarFamilia = () -> { inicio.await(5, TimeUnit.SECONDS); return estadoConfirmar(familia); };
            Callable<Integer> confirmarCuidador = () -> { inicio.await(5, TimeUnit.SECONDS); return estadoConfirmar(cuidador); };
            var a = hilos.submit(confirmarFamilia); var b = hilos.submit(confirmarCuidador); inicio.countDown();
            int resultadoFamilia = a.get(15, TimeUnit.SECONDS), resultadoCuidador = b.get(15, TimeUnit.SECONDS);
            assertThat(List.of(resultadoFamilia, resultadoCuidador)).containsExactlyInAnyOrder(200, 400);
            var autor = resultadoFamilia == 200 ? familia : cuidador;
            assertThat(llamar(propietario, "GET", "/recordatorios/" + recordatorio, null, 200).get("confirmadoPorId").asText()).isEqualTo(autor.getIdUsuario().toString());
        }
    }

    private int estadoConfirmar(Usuario actor) throws Exception {
        return mvc.perform(request(HttpMethod.PATCH, "/recordatorios/" + recordatorio + "/confirmacion").with(user(actor.getEmail())))
                .andReturn().getResponse().getStatus();
    }

    @Test void revocarYCambiarPerfilSeAplicanConElMismoJwt() throws Exception {
        var token = jwt.generarToken(familia);
        var acceso = accesos.findByPropietario_IdUsuarioAndInvitado_IdUsuarioAndActivoTrue(propietario.getIdUsuario(), familia.getIdUsuario()).orElseThrow();
        llamarConToken(token, "POST", rutaMascota("recordatorios"), datosRecordatorio(ahora), 201);
        llamar(propietario, "PATCH", "/cuidado-compartido/accesos/" + acceso.getIdAcceso() + "/perfil", Map.of("perfil", "Cuidador"), 200);
        llamarConToken(token, "POST", rutaMascota("recordatorios"), datosRecordatorio(ahora), 403);
        llamarConToken(token, "GET", "/fotos/" + foto, null, 200);
        llamar(propietario, "DELETE", "/cuidado-compartido/accesos/" + acceso.getIdAcceso(), null, 200);
        llamarConToken(token, "GET", "/fotos/" + foto, null, 403);
        assertThat(ids(llamarConToken(token, "GET", "/mascotas", null, 200), "idMascota")).containsExactly(propiaFamilia.getIdMascota().toString());
    }

    @Test void albumConOtraMascotaNoAutorizadaNoFiltraDatosPorNingunaRuta() throws Exception {
        relaciones.saveAndFlush(AlbumMascota.builder().album(albumes.findById(UUID.fromString(album)).orElseThrow()).mascota(otra).build());
        for (var ruta : List.of(rutaMascota("albumes"), "/albumes/" + album, "/albumes/" + album + "/fotos", "/fotos/" + foto))
            llamar(familia, "GET", ruta, null, 403);
    }

    @Test void albumConVariasMascotasDelPropietarioSigueSiendoLegible() throws Exception {
        relaciones.saveAndFlush(AlbumMascota.builder().album(albumes.findById(UUID.fromString(album)).orElseThrow()).mascota(mascota(propietario)).build());
        for (var ruta : List.of(rutaMascota("albumes"), "/albumes/" + album, "/albumes/" + album + "/fotos", "/fotos/" + foto))
            llamar(familia, "GET", ruta, null, 200);
    }

    @Test void mascotasCompartidasNoConsumenElCupoDeMascotasPropias() throws Exception {
        for (int i = 0; i < 3; i++) mascota(familia);
        var datos = Map.of("nombre", "Propia QA", "especie", "Perro", "fechaNacimiento", "2020-01-01", "fechaAproximada", false);
        llamar(familia, "POST", "/mascotas", datos, 201);
        llamar(familia, "POST", "/mascotas", datos, 409);
        assertThat(llamar(familia, "GET", "/mascotas", null, 200).size()).isEqualTo(6);
    }

    @Test void propietarioConservaGestionMultimediaYEliminadosNoSeLeen() throws Exception {
        llamar(propietario, "PUT", "/albumes/" + album, datosAlbum(), 200);
        llamar(propietario, "DELETE", "/badges/" + badge, null, 204);
        llamar(familia, "GET", "/badges/" + badge, null, 404);
        llamar(propietario, "DELETE", "/albumes/" + album, null, 204);
        llamar(familia, "GET", "/albumes/" + album, null, 404);
        llamar(cuidador, "GET", "/fotos/" + foto, null, 404);
    }

    @Test void cuentaPropietariaInactivaOAccesoInactivoNoOtorganLectura() throws Exception {
        propietario.setActivo(false); usuarios.saveAndFlush(propietario);
        llamar(familia, "GET", "/mascotas/" + mascota.getIdMascota(), null, 403);
        assertThat(ids(llamar(familia, "GET", "/mascotas", null, 200), "idMascota")).containsExactly(propiaFamilia.getIdMascota().toString());
    }

    private Usuario usuario() { var u = new Usuario(); u.setNombre("Permisos QA"); u.setEmail(UUID.randomUUID() + "@example.test"); u.setPasswordHash("hash-ficticio"); u = usuarios.saveAndFlush(u); cuentas.add(u.getIdUsuario()); return u; }
    private Mascota mascota(Usuario u) { var m = new Mascota(); m.setPropietario(u); m.setNombre("Mascota QA"); m.setEspecie("Perro"); m.setFechaNacimiento(LocalDate.of(2020, 1, 1)); return mascotas.saveAndFlush(m); }
    private void acceso(Usuario p, Usuario i, PerfilCompartido perfil) { accesos.saveAndFlush(AccesoCompartido.builder().propietario(p).invitado(i).perfil(perfil).fechaIngreso(ahora).build()); }
    private Usuario actor(String perfil) { return "Propietario".equals(perfil) ? propietario : "Familia".equals(perfil) ? familia : cuidador; }
    private String rutaMascota(String recurso) { return "/mascotas/" + mascota.getIdMascota() + "/" + recurso; }
    private Map<String, Object> clinico() { return Map.of("tipo", "VACUNA", "fecha", "2024-01-01T12:00:00Z", "nombre", "Vacuna QA", "mascotaId", mascota.getIdMascota().toString()); }
    private Map<String, Object> datosRecordatorio(Instant fecha) { return Map.of("titulo", "Recordatorio QA", "tipo", "OTRO", "modalidad", "unica", "fechaHoraInicio", fecha.toString()); }
    private Map<String, Object> datosAlbum() { return Map.of("nombre", "Album QA", "mascotaId", mascota.getIdMascota().toString()); }
    private Map<String, Object> datosFoto() { return Map.of("urlArchivo", "https://example.test/foto.jpg", "formato", "jpg"); }
    private Map<String, Object> datosBadge() { return Map.of("texto", "Badge QA", "mascotaId", mascota.getIdMascota().toString()); }
    private String nuevoRecordatorio(Instant fecha) throws Exception { return llamar(propietario, "POST", rutaMascota("recordatorios"), datosRecordatorio(fecha), 201).get("idRecordatorio").asText(); }
    private List<String> ids(JsonNode lista, String campo) { var ids = new ArrayList<String>(); lista.forEach(n -> ids.add(n.get(campo).asText())); return ids; }
    private JsonNode llamar(Usuario actor, String metodo, String ruta, Object cuerpo, int esperado) throws Exception {
        return ejecutar(actor, null, metodo, ruta, cuerpo, esperado);
    }
    private JsonNode llamarConToken(String token, String metodo, String ruta, Object cuerpo, int esperado) throws Exception { return ejecutar(null, token, metodo, ruta, cuerpo, esperado); }
    private JsonNode ejecutar(Usuario actor, String token, String metodo, String ruta, Object cuerpo, int esperado) throws Exception {
        var peticion = request(HttpMethod.valueOf(metodo), ruta);
        if (actor != null) peticion.with(user(actor.getEmail()));
        if (token != null) peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (cuerpo != null) peticion.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo));
        var r = mvc.perform(peticion).andReturn().getResponse();
        assertThat(r.getStatus()).as(metodo + " " + ruta + ": " + r.getContentAsString()).isEqualTo(esperado);
        return r.getContentAsString().isEmpty() ? null : json.readTree(r.getContentAsString());
    }
}
