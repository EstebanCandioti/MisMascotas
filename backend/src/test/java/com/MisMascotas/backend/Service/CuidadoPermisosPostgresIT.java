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
import com.MisMascotas.backend.Repository.RecordatorioRepository.SerieParaRenovar;
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
    @Autowired RecordatorioRepository recordatorios;
    @Autowired RecordatorioService servicioRecordatorios;
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
        propietario.setEsPremium(true); propietario = usuarios.saveAndFlush(propietario);
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
            sql.update("""
                    DELETE FROM ocurrencia_recordatorio WHERE recordatorio_id IN (
                        SELECT r.id_recordatorio FROM recordatorio r
                        JOIN mascota m ON m.id_mascota=r.mascota_id WHERE m.propietario_id=?
                    )
                    """, id);
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
        return estadoConfirmar(actor, "/recordatorios/" + recordatorio + "/confirmacion");
    }

    private int estadoConfirmar(Usuario actor, String ruta) throws Exception {
        return mvc.perform(request(HttpMethod.PATCH, ruta).with(user(actor.getEmail())))
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

    @Test void validaYNormalizaProgramacionesPorHttpAntesDeModificarDatos() throws Exception {
        var base = new HashMap<>(datosRecordatorio(ahora));
        base.remove("modalidad");
        assertThat(llamar(propietario, "POST", rutaMascota("recordatorios"), base, 201)
                .get("modalidad").asText()).isEqualTo("unica");

        var dias = new HashMap<>(base);
        dias.put("modalidad", "dias_semana");
        dias.put("diasSemana", "domingo, miércoles, jueves, martes, viernes, sábado, lunes");
        var serie = llamar(familia, "POST", rutaMascota("recordatorios"), dias, 201);
        UUID id = UUID.fromString(serie.get("idRecordatorio").asText());
        assertThat(serie.get("diaSemana").asText())
                .isEqualTo("LUNES,MARTES,MIERCOLES,JUEVES,VIERNES,SABADO,DOMINGO");
        var antes = estadoProgramacion(id);

        var recurrente = new HashMap<>(base);
        recurrente.put("modalidad", "recurrente");
        recurrente.put("intervaloUnidad", "dias");

        for (var caso : List.of(
                Map.entry("intervaloValor", Map.of("intervaloValor", 1.5)),
                Map.entry("intervaloValor", Map.of("intervaloValor", "1")),
                Map.entry("intervaloValor", Map.of("intervaloValor", 0)),
                Map.entry("intervaloValor", Map.of("intervaloValor", 2147483648L)),
                Map.entry("intervaloValor", Map.of()),
                Map.entry("intervaloUnidad", Map.of("intervaloValor", 1, "intervaloUnidad", "horas")),
                Map.entry("diasSemana", Map.of("intervaloValor", 1, "diasSemana", "LUNES")),
                Map.entry("fechaFin", Map.of("intervaloValor", 1, "fechaFin", "2030-01-01T21:00:00Z")),
                Map.entry("fechaFin", Map.of("intervaloValor", 1, "fechaFin", "2032-01-01T15:00:00Z")))) {
            var invalido = new HashMap<String, Object>(recurrente);
            caso.getValue().forEach((clave, valor) -> invalido.put(clave.toString(), valor));
            var respuesta = llamar(familia, "PUT", "/recordatorios/" + id, invalido, 400);
            assertThat(respuesta.get("codigo").asText()).isEqualTo("ERROR_VALIDACION");
            assertThat(respuesta.get("mensaje").asText()).startsWith(caso.getKey() + ":");
        }
        assertThat(estadoProgramacion(id)).isEqualTo(antes);
        assertThat(sql.queryForObject("""
                SELECT count(*) FROM log_auditoria WHERE entidad_afectada='recordatorio'
                    AND id_entidad=? AND tipo_accion='UPDATE'
                """, Integer.class, id)).isZero();
        llamar(cuidador, "PUT", "/recordatorios/" + id, dias, 403);
    }

    @Test void buscaSoloTandasSinFinActivasYConUltimaFechaAlcanzada() throws Exception {
        var datos = new HashMap<>(datosRecordatorio(ahora));
        datos.put("modalidad", "recurrente");
        datos.put("intervaloValor", 1);
        datos.put("intervaloUnidad", "anios");

        var alDia = nuevaSerie(datos);
        var anterior = nuevaSerie(datos);
        var futuro = nuevaSerie(datos);
        var pausa = nuevaSerie(datos);
        var eliminada = nuevaSerie(datos);
        var pendienteDePrimeraTanda = nuevaSerie(datos);
        // Representa una serie previa a V12, que aún no tiene progreso registrado.
        actualizarUltima(pendienteDePrimeraTanda, null);
        var conFin = new HashMap<>(datos);
        conFin.put("fechaFin", ahora.plus(30, java.time.temporal.ChronoUnit.DAYS).toString());
        var finito = nuevaSerie(conFin);

        LocalDate hoy = LocalDate.of(2030, 1, 1);
        for (UUID serie : List.of(alDia, pausa, eliminada, finito)) actualizarUltima(serie, hoy);
        actualizarUltima(anterior, hoy.minusDays(1));
        actualizarUltima(futuro, hoy.plusDays(1));
        actualizarUltima(UUID.fromString(recordatorio), hoy);
        llamar(propietario, "PATCH", "/recordatorios/" + pausa + "/estado?nuevoEstado=INACTIVO", null, 200);
        llamar(propietario, "DELETE", "/recordatorios/" + eliminada, null, 204);

        assertThat(recordatorios.buscarSeriesParaRenovar(hoy, null, null, 20))
                .extracting(SerieParaRenovar::getIdRecordatorio).containsExactly(anterior, alDia);
        var primeraPagina = recordatorios.buscarSeriesParaRenovar(hoy, null, null, 1);
        assertThat(primeraPagina).extracting(SerieParaRenovar::getIdRecordatorio).containsExactly(anterior);
        var cursor = primeraPagina.getFirst();
        assertThat(recordatorios.buscarSeriesParaRenovar(hoy, cursor.getUltimaFechaGenerada(),
                cursor.getIdRecordatorio(), 1)).extracting(SerieParaRenovar::getIdRecordatorio).containsExactly(alDia);
        assertThat(recordatorios.findById(pendienteDePrimeraTanda).orElseThrow().getUltimaFechaGenerada()).isNull();
    }

    @Test void altaConFinGuardaTodasLasFechasYElUnicoNoCreaOcurrencias() throws Exception {
        var datos = programacionPorIntervalo("2030-01-31T21:00:00Z", 1, "meses");
        datos.put("fechaFin", "2030-03-31T21:00:00Z");
        var respuesta = llamar(familia, "POST", rutaMascota("recordatorios"), datos, 201);
        UUID serie = UUID.fromString(respuesta.get("idRecordatorio").asText());

        assertThat(respuesta.get("creadoPorId").asText()).isEqualTo(familia.getIdUsuario().toString());
        assertThat(respuesta.get("fechaHoraInicio").asText()).isEqualTo("2030-01-31T21:00:00Z");
        assertThat(respuesta.get("fechaFin").asText()).isEqualTo("2030-03-31T21:00:00Z");
        assertThat(fechasDeOcurrencias(serie)).containsExactly(
                Instant.parse("2030-01-31T21:00:00Z"), Instant.parse("2030-02-28T21:00:00Z"),
                Instant.parse("2030-03-31T21:00:00Z"));
        assertThat(recordatorios.findById(serie).orElseThrow().getUltimaFechaGenerada()).isNull();
        assertThat(fechasDeOcurrencias(UUID.fromString(recordatorio))).isEmpty();
        assertThat(sql.queryForObject("""
                SELECT count(*) FROM log_auditoria WHERE id_entidad=? AND entidad_afectada='recordatorio'
                  AND tipo_accion='CREATE' AND actor_id=?
                """, Integer.class, serie, familia.getIdUsuario())).isEqualTo(1);
    }

    @Test void altaSinFinGuardaDoceOcurrenciasYMarcaElUltimoDiaArgentino() throws Exception {
        UUID serie = nuevaSerie(programacionPorIntervalo("2030-01-02T02:30:00Z", 2, "dias"));
        var fechas = fechasDeOcurrencias(serie);
        assertThat(fechas).containsExactlyElementsOf(java.util.stream.IntStream.range(0, 12)
                .mapToObj(i -> Instant.parse("2030-01-02T02:30:00Z").plus(Duration.ofDays(2L * i))).toList());
        assertThat(fechas.getLast()).isEqualTo(Instant.parse("2030-01-24T02:30:00Z"));
        assertThat(recordatorios.findById(serie).orElseThrow().getUltimaFechaGenerada())
                .isEqualTo(LocalDate.of(2030, 1, 23));
        assertThat(sql.queryForObject("""
                SELECT count(*) FROM ocurrencia_recordatorio WHERE recordatorio_id=?
                  AND confirmado_por_id IS NULL AND confirmado_en IS NULL
                """, Integer.class, serie)).isEqualTo(12);
    }

    @Test void altaPorDiasDeSemanaEmpiezaEnElPrimerDiaElegido() throws Exception {
        var datos = new HashMap<>(datosRecordatorio(Instant.parse("2030-01-01T21:00:00Z")));
        datos.put("modalidad", "dias_semana");
        datos.put("diasSemana", "lunes, miércoles");
        datos.put("fechaFin", "2030-01-09T21:00:00Z");
        assertThat(fechasDeOcurrencias(nuevaSerie(datos))).containsExactly(
                Instant.parse("2030-01-02T21:00:00Z"), Instant.parse("2030-01-07T21:00:00Z"),
                Instant.parse("2030-01-09T21:00:00Z"));
    }

    @Test void altaAceptaElLimiteYRechazaExcesoIntervaloInvalidoOAccesoSinEscrituras() throws Exception {
        var datos = programacionPorIntervalo("2030-01-01T21:00:00Z", 1, "dias");
        datos.put("fechaFin", "2031-01-01T21:00:00Z");
        assertThat(fechasDeOcurrencias(nuevaSerie(datos))).hasSize(366);
        var antes = estadoAltas();

        datos.put("fechaFin", "2031-01-02T21:00:00Z");
        var exceso = llamar(propietario, "POST", rutaMascota("recordatorios"), datos, 400);
        assertThat(exceso.get("codigo").asText()).isEqualTo("ERROR_VALIDACION");
        assertThat(exceso.get("mensaje").asText()).startsWith("fechaFin:");

        datos.put("intervaloValor", 0);
        var intervalo = llamar(propietario, "POST", rutaMascota("recordatorios"), datos, 400);
        assertThat(intervalo.get("mensaje").asText()).startsWith("intervaloValor:");

        datos.put("fechaFin", "2030-01-10T21:00:00Z");
        datos.put("intervaloValor", 1);
        llamar(cuidador, "POST", rutaMascota("recordatorios"), datos, 403);
        assertThat(estadoAltas()).isEqualTo(antes);
    }

    @Test void fallaDeInsercionRevierteLaSerieLasOcurrenciasYLaAuditoria() throws Exception {
        var datos = programacionPorIntervalo("2040-12-01T21:00:00Z", 1, "dias");
        var antes = estadoAltas();
        sql.execute("""
                ALTER TABLE ocurrencia_recordatorio ADD CONSTRAINT prueba_falla_alta_serie
                  CHECK (fecha_programada <> TIMESTAMPTZ '2040-12-03T21:00:00Z') NOT VALID
                """);
        try {
            llamar(propietario, "POST", rutaMascota("recordatorios"), datos, 500);
            assertThat(estadoAltas()).isEqualTo(antes);
        } finally {
            sql.execute("ALTER TABLE ocurrencia_recordatorio DROP CONSTRAINT prueba_falla_alta_serie");
        }
        assertThat(fechasDeOcurrencias(nuevaSerie(datos))).hasSize(12);
    }

    @Test void renuevaElUltimoDiaSinConfirmarYElReintentoNoDuplica() throws Exception {
        var serie = nuevaSerie(programacionPorIntervalo("2029-12-21T21:00:00Z", 1, "dias"));
        var datosConFin = programacionPorIntervalo("2029-12-31T21:00:00Z", 1, "dias");
        datosConFin.put("fechaFin", "2030-01-01T21:00:00Z");
        var finita = nuevaSerie(datosConFin);
        var inactiva = nuevaSerie(programacionPorIntervalo("2029-12-21T21:00:00Z", 1, "dias"));
        llamar(propietario, "PATCH", "/recordatorios/" + inactiva + "/estado?nuevoEstado=INACTIVO", null, 200);
        var job = new RenovadorTandasRecordatorioProgramado(recordatorios, servicioRecordatorios, reloj);
        var iniciales = fechasDeOcurrencias(serie);
        assertThat(iniciales).hasSize(12).endsWith(Instant.parse("2030-01-01T21:00:00Z"));

        when(reloj.instant()).thenReturn(Instant.parse("2030-01-01T02:59:59Z"));
        job.ejecutar();
        assertThat(fechasDeOcurrencias(serie)).isEqualTo(iniciales);

        when(reloj.instant()).thenReturn(Instant.parse("2030-01-01T03:00:00Z"));
        job.ejecutar();
        var renovadas = fechasDeOcurrencias(serie);
        assertThat(renovadas).hasSize(24).startsWith(iniciales.toArray(Instant[]::new))
                .endsWith(Instant.parse("2030-01-13T21:00:00Z"));
        assertThat(renovadas.get(12)).isEqualTo(Instant.parse("2030-01-02T21:00:00Z"));
        assertThat(ultimaFechaDe(serie)).isEqualTo(LocalDate.of(2030, 1, 13));
        job.ejecutar();
        assertThat(fechasDeOcurrencias(serie)).isEqualTo(renovadas);
        assertThat(fechasDeOcurrencias(finita)).hasSize(2);
        assertThat(fechasDeOcurrencias(inactiva)).hasSize(11).endsWith(Instant.parse("2029-12-31T21:00:00Z"));
    }

    @Test void recuperaDesdeHoySinCompletarDiasPasadosNiCambiarLaFrecuencia() throws Exception {
        var serie = nuevaSerie(programacionPorIntervalo("2029-12-02T21:00:00Z", 2, "dias"));
        var iniciales = fechasDeOcurrencias(serie);
        assertThat(iniciales).hasSize(12).endsWith(Instant.parse("2029-12-24T21:00:00Z"));
        new RenovadorTandasRecordatorioProgramado(recordatorios, servicioRecordatorios, reloj).ejecutar();
        var renovadas = fechasDeOcurrencias(serie);
        assertThat(renovadas).hasSize(24).startsWith(iniciales.toArray(Instant[]::new));
        assertThat(renovadas.subList(12, 15)).containsExactly(Instant.parse("2030-01-01T21:00:00Z"),
                Instant.parse("2030-01-03T21:00:00Z"), Instant.parse("2030-01-05T21:00:00Z"));
        assertThat(ultimaFechaDe(serie)).isEqualTo(LocalDate.of(2030, 1, 23));
    }

    @Test void renovarEnParaleloAgregaUnaSolaTanda() throws Exception {
        var serie = nuevaSerie(programacionPorIntervalo("2029-12-21T21:00:00Z", 1, "dias"));
        try (var hilos = Executors.newFixedThreadPool(2)) {
            var preparados = new CountDownLatch(2);
            var salida = new CountDownLatch(1);
            Callable<Void> renovar = () -> {
                preparados.countDown();
                assertThat(salida.await(5, TimeUnit.SECONDS)).isTrue();
                servicioRecordatorios.renovarTanda(serie, LocalDate.of(2030, 1, 1));
                return null;
            };
            var primera = hilos.submit(renovar);
            var segunda = hilos.submit(renovar);
            assertThat(preparados.await(5, TimeUnit.SECONDS)).isTrue();
            salida.countDown();
            primera.get(15, TimeUnit.SECONDS);
            segunda.get(15, TimeUnit.SECONDS);
        }
        assertThat(fechasDeOcurrencias(serie)).hasSize(24).doesNotHaveDuplicates();
        assertThat(ultimaFechaDe(serie)).isEqualTo(LocalDate.of(2030, 1, 13));
    }

    @Test void elFalloDeUnaTandaNoAvanzaSuProgresoNiImpideProcesarOtra() throws Exception {
        var falla = nuevaSerie(programacionPorIntervalo("2029-12-21T20:00:00Z", 1, "dias"));
        var correcta = nuevaSerie(programacionPorIntervalo("2029-12-21T21:00:00Z", 1, "dias"));
        sql.execute("""
                ALTER TABLE ocurrencia_recordatorio ADD CONSTRAINT prueba_falla_renovacion
                CHECK (fecha_programada <> TIMESTAMPTZ '2030-01-04T20:00:00Z') NOT VALID
                """);
        var job = new RenovadorTandasRecordatorioProgramado(recordatorios, servicioRecordatorios, reloj);
        try {
            job.ejecutar();
            assertThat(fechasDeOcurrencias(falla)).hasSize(12);
            assertThat(ultimaFechaDe(falla)).isEqualTo(LocalDate.of(2030, 1, 1));
            assertThat(fechasDeOcurrencias(correcta)).hasSize(24);
        } finally {
            sql.execute("ALTER TABLE ocurrencia_recordatorio DROP CONSTRAINT prueba_falla_renovacion");
        }
        job.ejecutar();
        assertThat(fechasDeOcurrencias(falla)).hasSize(24);
        assertThat(fechasDeOcurrencias(correcta)).hasSize(24);
    }

    @Test void hoyMezclaConUnicosUsaFechaArgentinaYRespetaRevocacion() throws Exception {
        var datos = programacionPorIntervalo("2029-12-31T03:00:00Z", 1, "dias");
        var serie = nuevaSerie(datos);
        var inactiva = nuevaSerie(datos);
        var eliminada = nuevaSerie(datos);
        llamar(propietario, "PATCH", "/recordatorios/" + inactiva + "/estado?nuevoEstado=INACTIVO", null, 200);
        llamar(propietario, "DELETE", "/recordatorios/" + eliminada, null, 204);
        llamar(ajeno, "POST", "/mascotas/" + otra.getIdMascota() + "/recordatorios", datos, 201);
        var antes = estadoAltas();
        var token = jwt.generarToken(cuidador);
        var lista = llamarConToken(token, "GET", "/recordatorios/hoy", null, 200);
        assertThat(ids(lista, "idRecordatorio")).containsExactly(serie.toString(), recordatorio);
        assertThat(lista.get(0).get("idOcurrencia").asText()).isEqualTo(idOcurrencia(serie, "2030-01-01T03:00:00Z").toString());
        assertThat(lista.get(0).get("fechaProgramada").asText()).isEqualTo("2030-01-01T03:00:00Z");
        assertThat(lista.get(0).get("fechaHoraInicio").asText()).isEqualTo("2029-12-31T03:00:00Z");
        assertThat(lista.get(0).get("situacionTemporal").asText()).isEqualTo("PENDIENTE");
        assertThat(lista.get(0).get("puedeConfirmar").asBoolean()).isTrue();
        assertThat(lista.get(1).has("idOcurrencia")).isFalse();
        assertThat(llamarConToken(token, "GET", "/recordatorios/hoy", null, 200)).isEqualTo(lista);
        assertThat(estadoAltas()).isEqualTo(antes);
        var acceso = accesos.findByPropietario_IdUsuarioAndInvitado_IdUsuarioAndActivoTrue(
                propietario.getIdUsuario(), cuidador.getIdUsuario()).orElseThrow();
        llamar(propietario, "DELETE", "/cuidado-compartido/accesos/" + acceso.getIdAcceso(), null, 200);
        assertThat(llamarConToken(token, "GET", "/recordatorios/hoy", null, 200).size()).isZero();
        llamarConToken(token, "PATCH", "/recordatorios/" + serie + "/confirmacion?idOcurrencia="
                + lista.get(0).get("idOcurrencia").asText(), null, 403);
    }

    @ParameterizedTest @ValueSource(strings = {"Propietario", "Familia", "Cuidador"})
    void confirmaSoloLaOcurrenciaDeHoyAunqueTodaviaNoSeaLaHora(String perfil) throws Exception {
        var actor = actor(perfil);
        var serie = nuevaSerie(programacionPorIntervalo("2030-01-01T02:59:59Z", 1, "dias"));
        var anterior = idOcurrencia(serie, "2030-01-01T02:59:59Z");
        var actual = idOcurrencia(serie, "2030-01-02T02:59:59Z");
        var futura = idOcurrencia(serie, "2030-01-03T02:59:59Z");
        var ruta = "/recordatorios/" + serie + "/confirmacion?idOcurrencia=";
        llamar(actor, "PATCH", ruta + anterior, null, 400);
        llamar(actor, "PATCH", ruta + futura, null, 400);
        var confirmada = llamar(actor, "PATCH", ruta + actual, null, 200);
        assertThat(confirmada.get("idOcurrencia").asText()).isEqualTo(actual.toString());
        assertThat(confirmada.get("confirmadoPorId").asText()).isEqualTo(actor.getIdUsuario().toString());
        assertThat(confirmada.get("confirmadoEn").asText()).isEqualTo(ahora.toString());
        assertThat(confirmada.get("estado").asText()).isEqualTo("ACTIVO");
        assertThat(confirmada.get("situacionTemporal").asText()).isEqualTo("COMPLETADO");
        assertThat(confirmada.get("puedeConfirmar").asBoolean()).isFalse();
        llamar(actor, "PATCH", ruta + actual, null, 400);
        var padre = llamar(propietario, "GET", "/recordatorios/" + serie, null, 200);
        assertThat(padre.get("confirmadoEn").isNull()).isTrue();
        assertThat(padre.get("puedeConfirmar").asBoolean()).isFalse();
        var lista = llamar(actor, "GET", "/recordatorios/hoy", null, 200);
        assertThat(lista.get(1).get("idOcurrencia").asText()).isEqualTo(actual.toString());
        assertThat(lista.get(1).get("situacionTemporal").asText()).isEqualTo("COMPLETADO");
        assertThat(confirmadasDe(serie)).containsExactly(actual);
    }

    @Test void selectorYEstadoNoModificanLaSerieCompletaYDesmarcarRespetaPermisos() throws Exception {
        var datos = programacionPorIntervalo(ahora.toString(), 1, "dias");
        var serie = nuevaSerie(datos);
        var otraSerie = nuevaSerie(datos);
        var actual = idOcurrencia(serie, ahora.toString());
        var ajena = idOcurrencia(otraSerie, ahora.toString());
        var base = "/recordatorios/" + serie;
        var ruta = base + "/confirmacion?idOcurrencia=";
        for (var metodo : List.of("PATCH", "DELETE")) {
            llamar(familia, metodo, base + "/confirmacion", null, 400);
            llamar(familia, metodo, ruta + ajena, null, 404);
            llamar(familia, metodo, ruta + UUID.randomUUID(), null, 404);
        }
        llamar(familia, "PATCH", "/recordatorios/" + recordatorio + "/confirmacion?idOcurrencia=" + actual, null, 400);
        llamar(familia, "PATCH", base + "/estado?nuevoEstado=INACTIVO&idOcurrencia=" + actual, null, 400);
        llamar(familia, "PATCH", base + "/estado?nuevoEstado=INACTIVO", null, 200);
        llamar(cuidador, "PATCH", ruta + actual, null, 404);
        llamar(familia, "PATCH", base + "/estado?nuevoEstado=ACTIVO", null, 200);
        var reactivada = idOcurrencia(serie, ahora.toString());
        assertThat(reactivada).isNotEqualTo(actual);
        llamar(familia, "PATCH", base + "/estado?nuevoEstado=COMPLETADO", null, 400);
        llamar(ajeno, "PATCH", ruta + reactivada, null, 403);
        assertThat(confirmadasDe(serie)).isEmpty();
        assertThat(confirmadasDe(otraSerie)).isEmpty();
        var confirmada = llamar(familia, "PATCH", base + "/estado?nuevoEstado=COMPLETADO&idOcurrencia=" + reactivada,
                null, 200);
        assertThat(confirmada.get("estado").asText()).isEqualTo("ACTIVO");
        assertThat(confirmada.get("idOcurrencia").asText()).isEqualTo(reactivada.toString());
        llamar(cuidador, "DELETE", ruta + reactivada, null, 403);
        assertThat(confirmadasDe(serie)).containsExactly(reactivada);
        var pendiente = llamar(propietario, "DELETE", ruta + reactivada, null, 200);
        assertThat(pendiente.get("situacionTemporal").asText()).isEqualTo("PENDIENTE");
        assertThat(pendiente.get("confirmadoPorId").isNull()).isTrue();
        assertThat(pendiente.get("confirmadoEn").isNull()).isTrue();
        assertThat(confirmadasDe(serie)).isEmpty();
        llamar(cuidador, "PATCH", ruta + reactivada, null, 200);
        llamar(familia, "DELETE", ruta + reactivada, null, 200);
        assertThat(confirmadasDe(serie)).isEmpty();
    }

    @Test void confirmarLaMismaOcurrenciaEnParaleloConservaAlPrimerAutor() throws Exception {
        var serie = nuevaSerie(programacionPorIntervalo(ahora.toString(), 1, "dias"));
        var ocurrencia = idOcurrencia(serie, ahora.toString());
        var ruta = "/recordatorios/" + serie + "/confirmacion?idOcurrencia=" + ocurrencia;
        try (var hilos = Executors.newFixedThreadPool(2)) {
            var preparados = new CountDownLatch(2);
            var salida = new CountDownLatch(1);
            var familiaRespuesta = hilos.submit(() -> {
                preparados.countDown();
                assertThat(salida.await(5, TimeUnit.SECONDS)).isTrue();
                return estadoConfirmar(familia, ruta);
            });
            var cuidadorRespuesta = hilos.submit(() -> {
                preparados.countDown();
                assertThat(salida.await(5, TimeUnit.SECONDS)).isTrue();
                return estadoConfirmar(cuidador, ruta);
            });
            assertThat(preparados.await(5, TimeUnit.SECONDS)).isTrue();
            salida.countDown();
            var resultadoFamilia = familiaRespuesta.get(15, TimeUnit.SECONDS);
            var resultadoCuidador = cuidadorRespuesta.get(15, TimeUnit.SECONDS);
            assertThat(List.of(resultadoFamilia, resultadoCuidador)).containsExactlyInAnyOrder(200, 400);
            var autor = resultadoFamilia == 200 ? familia : cuidador;
            assertThat(sql.queryForObject("SELECT confirmado_por_id FROM ocurrencia_recordatorio WHERE id_ocurrencia=?",
                    UUID.class, ocurrencia)).isEqualTo(autor.getIdUsuario());
        }
        assertThat(confirmadasDe(serie)).containsExactly(ocurrencia);
    }

    @Test void editarReprogramaSoloFuturasYConservaLaPendienteDeHoyYLaAutoriaPasada() throws Exception {
        var original = programacionPorIntervalo("2029-12-31T21:00:00Z", 1, "dias");
        var serie = nuevaSerie(original);
        var anterior = idOcurrencia(serie, "2029-12-31T21:00:00Z");
        var hoy = idOcurrencia(serie, "2030-01-01T21:00:00Z");
        var futura = idOcurrencia(serie, "2030-01-02T21:00:00Z");
        when(reloj.instant()).thenReturn(Instant.parse("2029-12-31T23:00:00Z"));
        llamar(cuidador, "PATCH", rutaOcurrencia(serie, anterior), null, 200);
        when(reloj.instant()).thenReturn(ahora);
        var base = "/recordatorios/" + serie;
        var edicion = programacionPorIntervalo("2029-12-31T20:00:00Z", 2, "dias");
        llamar(cuidador, "PUT", base, edicion, 403);
        llamar(familia, "PUT", base, edicion, 200);

        assertThat(fechasDeOcurrencias(serie)).hasSize(14)
                .startsWith(Instant.parse("2029-12-31T21:00:00Z"), Instant.parse("2030-01-01T21:00:00Z"),
                        Instant.parse("2030-01-02T20:00:00Z"), Instant.parse("2030-01-04T20:00:00Z"))
                .endsWith(Instant.parse("2030-01-24T20:00:00Z"));
        assertThat(idOcurrencia(serie, "2030-01-01T21:00:00Z")).isEqualTo(hoy);
        assertThat(autorDe(anterior)).isEqualTo(cuidador.getIdUsuario());
        assertThat(confirmadasDe(serie)).containsExactly(anterior);
        llamar(familia, "PATCH", rutaOcurrencia(serie, futura), null, 404);
        assertThat(ultimaFechaDe(serie)).isEqualTo(LocalDate.of(2030, 1, 24));

        var siguiente = idOcurrencia(serie, "2030-01-02T20:00:00Z");
        edicion.put("titulo", "Solo cambia el título");
        llamar(propietario, "PUT", base, edicion, 200);
        assertThat(idOcurrencia(serie, "2030-01-02T20:00:00Z")).isEqualTo(siguiente);
        cuandoLasTandasVencen("2030-01-24T03:00:00Z");
        assertThat(fechasDeOcurrencias(serie)).hasSize(26)
                .contains(Instant.parse("2030-01-26T20:00:00Z"))
                .doesNotContain(Instant.parse("2030-01-25T21:00:00Z"));
    }

    @Test void pausarYReactivarDescartaPendientesDeLaPausaYMantieneElIntervaloOriginal() throws Exception {
        var datos = programacionPorIntervalo("2029-12-30T21:00:00Z", 2, "dias");
        var serie = nuevaSerie(datos);
        var anterior = idOcurrencia(serie, "2029-12-30T21:00:00Z");
        var delDia = idOcurrencia(serie, "2030-01-01T21:00:00Z");
        var deLaPausa = idOcurrencia(serie, "2030-01-03T21:00:00Z");
        when(reloj.instant()).thenReturn(Instant.parse("2029-12-30T23:00:00Z"));
        llamar(familia, "PATCH", rutaOcurrencia(serie, anterior), null, 200);
        when(reloj.instant()).thenReturn(ahora);
        var estado = "/recordatorios/" + serie + "/estado?nuevoEstado=";

        llamar(cuidador, "PATCH", estado + "INACTIVO", null, 403);
        llamar(familia, "PATCH", estado + "INACTIVO", null, 200);
        assertThat(fechasDeOcurrencias(serie)).containsExactly(Instant.parse("2029-12-30T21:00:00Z"));
        assertThat(ultimaFechaDe(serie)).isNull();
        assertThat(ids(llamar(cuidador, "GET", "/recordatorios/hoy", null, 200), "idRecordatorio"))
                .doesNotContain(serie.toString());
        llamar(cuidador, "PATCH", rutaOcurrencia(serie, delDia), null, 404);
        cuandoLasTandasVencen("2030-01-04T03:00:00Z");
        assertThat(fechasDeOcurrencias(serie)).hasSize(1);

        llamar(familia, "PATCH", estado + "ACTIVO", null, 200);
        var reactivadas = fechasDeOcurrencias(serie);
        assertThat(reactivadas).hasSize(13)
                .startsWith(Instant.parse("2029-12-30T21:00:00Z"), Instant.parse("2030-01-05T21:00:00Z"),
                        Instant.parse("2030-01-07T21:00:00Z"))
                .endsWith(Instant.parse("2030-01-27T21:00:00Z"));
        assertThat(ultimaFechaDe(serie)).isEqualTo(LocalDate.of(2030, 1, 27));
        assertThat(confirmadasDe(serie)).containsExactly(anterior);
        llamar(cuidador, "PATCH", rutaOcurrencia(serie, deLaPausa), null, 404);
        llamar(familia, "PATCH", estado + "ACTIVO", null, 200);
        assertThat(fechasDeOcurrencias(serie)).isEqualTo(reactivadas);
    }

    @Test void pausarYEditarUnaSerieConHoyConfirmadoNoCreaOtraOcurrenciaParaElMismoDia() throws Exception {
        var serie = nuevaSerie(programacionPorIntervalo("2029-12-31T21:00:00Z", 1, "dias"));
        var actual = idOcurrencia(serie, "2030-01-01T21:00:00Z");
        llamar(cuidador, "PATCH", rutaOcurrencia(serie, actual), null, 200);
        var ruta = "/recordatorios/" + serie;
        llamar(propietario, "PATCH", ruta + "/estado?nuevoEstado=INACTIVO", null, 200);
        llamar(propietario, "PUT", ruta, programacionPorIntervalo("2029-12-31T20:00:00Z", 1, "dias"), 200);
        assertThat(fechasDeOcurrencias(serie)).hasSize(2);
        assertThat(ultimaFechaDe(serie)).isNull();
        llamar(propietario, "PATCH", ruta + "/estado?nuevoEstado=ACTIVO", null, 200);
        assertThat(fechasDeOcurrencias(serie)).hasSize(13)
                .startsWith(Instant.parse("2029-12-31T21:00:00Z"), Instant.parse("2030-01-01T21:00:00Z"),
                        Instant.parse("2030-01-02T20:00:00Z"));
        assertThat(confirmadasDe(serie)).containsExactly(actual);
        assertThat(autorDe(actual)).isEqualTo(cuidador.getIdUsuario());
    }

    @Test void reactivarFinitaSinFechasRechazaSinCambiosYPermiteAmpliarElFin() throws Exception {
        var datos = programacionPorIntervalo("2029-12-31T21:00:00Z", 1, "semanas");
        datos.put("fechaFin", "2030-01-05T21:00:00Z");
        var serie = nuevaSerie(datos);
        var ruta = "/recordatorios/" + serie;
        llamar(familia, "PATCH", ruta + "/estado?nuevoEstado=INACTIVO", null, 200);
        when(reloj.instant()).thenReturn(Instant.parse("2030-01-03T15:00:00Z"));
        var antes = estadoProgramacion(serie);
        var error = llamar(familia, "PATCH", ruta + "/estado?nuevoEstado=ACTIVO", null, 400);
        assertThat(error.get("codigo").asText()).isEqualTo("ERROR_NEGOCIO");
        assertThat(estadoProgramacion(serie)).isEqualTo(antes);
        assertThat(fechasDeOcurrencias(serie)).containsExactly(Instant.parse("2029-12-31T21:00:00Z"));
        datos.put("fechaFin", "2030-01-07T21:00:00Z");
        llamar(familia, "PUT", ruta, datos, 200);
        assertThat(fechasDeOcurrencias(serie)).hasSize(1);
        assertThat(llamar(familia, "PATCH", ruta + "/estado?nuevoEstado=ACTIVO", null, 200)
                .get("estado").asText()).isEqualTo("ACTIVO");
        assertThat(fechasDeOcurrencias(serie)).containsExactly(Instant.parse("2029-12-31T21:00:00Z"),
                Instant.parse("2030-01-07T21:00:00Z"));
        assertThat(ultimaFechaDe(serie)).isNull();
        when(reloj.instant()).thenReturn(Instant.parse("2030-01-08T03:00:00Z"));
        llamar(familia, "PATCH", ruta + "/estado?nuevoEstado=INACTIVO", null, 200);
        llamar(familia, "PATCH", ruta + "/estado?nuevoEstado=ACTIVO", null, 400);
        assertThat(fechasDeOcurrencias(serie)).hasSize(2);
        assertThat(llamar(familia, "GET", ruta, null, 200).get("estado").asText()).isEqualTo("INACTIVO");
    }

    @Test void cambiarEntreUnicoYSerieConservaLaConfirmacionDeHoy() throws Exception {
        llamar(cuidador, "PATCH", "/recordatorios/" + recordatorio + "/confirmacion", null, 200);
        var id = UUID.fromString(recordatorio);
        var ruta = "/recordatorios/" + id;
        var respuesta = llamar(propietario, "PUT", ruta, programacionPorIntervalo(ahora.toString(), 1, "dias"), 200);
        assertThat(respuesta.get("estado").asText()).isEqualTo("ACTIVO");
        var historica = idOcurrencia(id, ahora.toString());
        assertThat(confirmadasDe(id)).containsExactly(historica);
        assertThat(autorDe(historica)).isEqualTo(cuidador.getIdUsuario());
        assertThat(fechasDeOcurrencias(id)).hasSize(13);

        llamar(propietario, "PUT", ruta, datosRecordatorio(ahora), 400);
        llamar(propietario, "PUT", ruta, datosRecordatorio(ahora.plusSeconds(86400)), 200);
        assertThat(fechasDeOcurrencias(id)).containsExactly(ahora);
        assertThat(ultimaFechaDe(id)).isNull();
        var lista = llamar(cuidador, "GET", "/recordatorios/hoy", null, 200);
        assertThat(lista.get(0).get("idOcurrencia").asText()).isEqualTo(historica.toString());
        assertThat(lista.get(0).get("situacionTemporal").asText()).isEqualTo("COMPLETADO");
        when(reloj.instant()).thenReturn(ahora.plusSeconds(86400));
        var unico = llamar(cuidador, "GET", "/recordatorios/hoy", null, 200).get(0);
        assertThat(unico.get("idRecordatorio").asText()).isEqualTo(recordatorio);
        assertThat(unico.has("idOcurrencia")).isFalse();
        assertThat(unico.get("puedeConfirmar").asBoolean()).isTrue();
    }

    @Test void editarEnParaleloConElJobDejaSoloFuturasDeLaNuevaProgramacion() throws Exception {
        var serie = nuevaSerie(programacionPorIntervalo("2029-12-21T21:00:00Z", 1, "dias"));
        var edicion = programacionPorIntervalo("2030-01-02T20:00:00Z", 2, "dias");
        edicion.put("fechaFin", "2030-01-04T20:00:00Z");
        try (var hilos = Executors.newFixedThreadPool(2)) {
            var preparados = new CountDownLatch(2);
            var salida = new CountDownLatch(1);
            var job = hilos.submit(() -> {
                preparados.countDown();
                assertThat(salida.await(5, TimeUnit.SECONDS)).isTrue();
                servicioRecordatorios.renovarTanda(serie, LocalDate.of(2030, 1, 1));
                return null;
            });
            var editar = hilos.submit(() -> {
                preparados.countDown();
                assertThat(salida.await(5, TimeUnit.SECONDS)).isTrue();
                return llamar(familia, "PUT", "/recordatorios/" + serie, edicion, 200);
            });
            assertThat(preparados.await(5, TimeUnit.SECONDS)).isTrue();
            salida.countDown();
            job.get(15, TimeUnit.SECONDS);
            editar.get(15, TimeUnit.SECONDS);
        }
        assertThat(fechasDeOcurrencias(serie)).hasSize(14).endsWith(Instant.parse("2030-01-01T21:00:00Z"),
                Instant.parse("2030-01-02T20:00:00Z"), Instant.parse("2030-01-04T20:00:00Z"));
        assertThat(ultimaFechaDe(serie)).isNull();
    }

    @Test void fallaAlReemplazarFuturasRevierteLaEdicionYConservaSusIds() throws Exception {
        var serie = nuevaSerie(programacionPorIntervalo("2029-12-31T21:00:00Z", 1, "dias"));
        var antes = estadoProgramacion(serie);
        var fechas = fechasDeOcurrencias(serie);
        var anterior = idOcurrencia(serie, "2030-01-02T21:00:00Z");
        sql.execute("""
                ALTER TABLE ocurrencia_recordatorio ADD CONSTRAINT prueba_falla_edicion
                CHECK (fecha_programada <> TIMESTAMPTZ '2030-01-04T20:00:00Z') NOT VALID
                """);
        try {
            llamar(familia, "PUT", "/recordatorios/" + serie,
                    programacionPorIntervalo("2029-12-31T20:00:00Z", 1, "dias"), 500);
            assertThat(estadoProgramacion(serie)).isEqualTo(antes);
            assertThat(fechasDeOcurrencias(serie)).isEqualTo(fechas);
            assertThat(idOcurrencia(serie, "2030-01-02T21:00:00Z")).isEqualTo(anterior);
        } finally {
            sql.execute("ALTER TABLE ocurrencia_recordatorio DROP CONSTRAINT prueba_falla_edicion");
        }
    }

    @Test void eliminarLogicamenteMantieneElHistorialYElJobNoVuelveAGenerar() throws Exception {
        var serie = nuevaSerie(programacionPorIntervalo("2029-12-21T21:00:00Z", 1, "dias"));
        var actual = idOcurrencia(serie, "2030-01-01T21:00:00Z");
        llamar(cuidador, "PATCH", rutaOcurrencia(serie, actual), null, 200);
        var ruta = "/recordatorios/" + serie;
        llamar(cuidador, "DELETE", ruta, null, 403);
        llamar(familia, "DELETE", ruta, null, 204);
        cuandoLasTandasVencen("2030-01-02T03:00:00Z");
        assertThat(fechasDeOcurrencias(serie)).hasSize(12);
        assertThat(confirmadasDe(serie)).containsExactly(actual);
        assertThat(recordatorios.findById(serie).orElseThrow().getFechaEliminacion()).isEqualTo(ahora);
        llamar(propietario, "GET", ruta, null, 404);
        llamar(propietario, "PATCH", ruta + "/estado?nuevoEstado=ACTIVO", null, 404);
    }

    @Test void calendarioCombinaGuardadasYCalculadasSinEscribirYConDetalleDeCadaFecha() throws Exception {
        var serie = nuevaSerie(programacionPorIntervalo("2029-12-30T21:00:00Z", 1, "dias"));
        var antes = estadoProgramacion(serie);
        var filas = estadoOcurrencias(serie);
        var altas = estadoAltas();
        var lista = llamar(propietario, "GET", "/calendario?desde=2030-01-01&hasta=2030-01-31", null, 200);
        var apariciones = apariciones(lista, serie);
        assertThat(apariciones).hasSize(31);
        assertThat(apariciones.stream().filter(n -> n.get("esPersistida").asBoolean())).hasSize(10);
        assertThat(apariciones.stream().filter(n -> !n.get("esPersistida").asBoolean())).hasSize(21);
        assertThat(apariciones.stream().map(n -> n.get("fechaProgramada").asText())).doesNotHaveDuplicates().isSorted();
        assertThat(apariciones.getFirst().get("puedeConfirmar").asBoolean()).isTrue();
        var calculada = apariciones.getLast();
        assertThat(calculada.get("idOcurrencia").isNull()).isTrue();
        assertThat(calculada.get("puedeConfirmar").asBoolean()).isFalse();
        assertThat(calculada.get("situacionTemporal").asText()).isEqualTo("PROXIMO");
        for (var fila : List.of(apariciones.getFirst(), calculada)) {
            assertThat(llamar(cuidador, "GET", detalleCalendario(serie, fila.get("fechaProgramada").asText()), null, 200))
                    .isEqualTo(fila);
        }
        assertThat(llamar(propietario, "GET", "/calendario?desde=2030-01-01&hasta=2030-01-31", null, 200)).isEqualTo(lista);
        assertThat(estadoProgramacion(serie)).isEqualTo(antes);
        assertThat(estadoOcurrencias(serie)).isEqualTo(filas);
        assertThat(estadoAltas()).isEqualTo(altas);
        assertThat(ultimaFechaDe(serie)).isEqualTo(LocalDate.of(2030, 1, 10));
    }

    @Test void calendarioIncluyeAmbosDiasEnArgentinaYAgrupaMascotasAccesiblesSinHistoriaClinica() throws Exception {
        var inicio = nuevoRecordatorio(Instant.parse("2030-01-01T03:00:00Z"));
        var fin = nuevoRecordatorio(Instant.parse("2030-01-03T02:59:59.999999Z"));
        var fuera = nuevoRecordatorio(Instant.parse("2030-01-03T03:00:00Z"));
        var pasado = nuevoRecordatorio(Instant.parse("2030-01-01T02:59:59Z"));
        var propio = llamar(familia, "POST", "/mascotas/" + propiaFamilia.getIdMascota() + "/recordatorios",
                datosRecordatorio(ahora), 201).get("idRecordatorio").asText();
        var ajenoId = llamar(ajeno, "POST", "/mascotas/" + otra.getIdMascota() + "/recordatorios",
                datosRecordatorio(ahora), 201).get("idRecordatorio").asText();
        var lista = llamar(familia, "GET", "/calendario?desde=2030-01-01&hasta=2030-01-02", null, 200);
        assertThat(ids(lista, "idRecordatorio")).containsExactlyInAnyOrder(inicio, fin, recordatorio, propio)
                .doesNotContain(fuera, pasado, ajenoId, evento);
        assertThat(ids(lista, "fechaProgramada")).isSorted();
        var hoy = llamar(cuidador, "GET", "/calendario?desde=2030-01-01&hasta=2030-01-01", null, 200);
        assertThat(ids(hoy, "idRecordatorio")).containsExactlyInAnyOrder(inicio, recordatorio);
        assertThat(llamar(ajeno, "GET", "/calendario?desde=2030-01-04&hasta=2030-01-04", null, 200).isEmpty()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?desde=2030-01-01", "?desde=2030-02-30&hasta=2030-03-01",
            "?desde=01-01-2030&hasta=2030-01-01", "?desde=2029-12-31&hasta=2030-01-02",
            "?desde=2030-01-02&hasta=2030-01-01", "?desde=2030-01-01&hasta=2031-01-02"})
    void calendarioRechazaRangosInvalidos(String consulta) throws Exception {
        var antes = estadoAltas();
        assertThat(llamar(propietario, "GET", "/calendario" + consulta, null, 400).get("codigo").asText())
                .isEqualTo("ERROR_VALIDACION");
        assertThat(estadoAltas()).isEqualTo(antes);
    }

    @Test void calendarioPermite366DiasInclusivosYValidaElSelectorDelDetalle() throws Exception {
        llamar(propietario, "GET", "/calendario?desde=2030-01-01&hasta=2031-01-01", null, 200);
        for (String ruta : List.of("/calendario/" + recordatorio, "/calendario/no-es-uuid?fechaProgramada=2030-01-01T21:00:00Z",
                detalleCalendario(UUID.fromString(recordatorio), "incorrecta"),
                detalleCalendario(UUID.fromString(recordatorio), "2029-12-31T21:00:00Z"))) {
            assertThat(llamar(propietario, "GET", ruta, null, 400).get("codigo").asText()).isEqualTo("ERROR_VALIDACION");
        }
        llamar(propietario, "GET", detalleCalendario(UUID.fromString(recordatorio), "2030-01-01T21:00:00Z"), null, 404);
        llamar(propietario, "GET", detalleCalendario(UUID.randomUUID(), ahora.toString()), null, 404);
    }

    @ParameterizedTest @CsvSource({"dias,2,2030-03-02,2030-03-08,4", "semanas,2,2030-09-01,2030-09-30,2",
            "meses,1,2031-02-01,2031-03-31,2", "anios,1,2043-01-01,2043-12-31,1"})
    void calendarioCalculaIntervalosDesdeElAnclaOriginal(String unidad, int intervalo, String desde, String hasta, int cantidad) throws Exception {
        var serie = nuevaSerie(programacionPorIntervalo("2030-01-31T21:00:00Z", intervalo, unidad));
        var apariciones = apariciones(llamar(propietario, "GET", "/calendario?desde=" + desde + "&hasta=" + hasta, null, 200), serie);
        assertThat(apariciones).hasSize(cantidad).allSatisfy(n -> assertThat(n.get("esPersistida").asBoolean()).isFalse());
        if (unidad.equals("meses")) assertThat(apariciones.stream().map(n -> n.get("fechaProgramada").asText()))
                .containsExactly("2031-02-28T21:00:00Z", "2031-03-31T21:00:00Z");
        assertThat(fechasDeOcurrencias(serie)).hasSize(12);
    }

    @Test void calendarioCalculaDiasDeSemanaYNoExtiendeSeriesFinitasNiLegadasSinTanda() throws Exception {
        var datos = new HashMap<>(datosRecordatorio(Instant.parse("2030-01-01T21:00:00Z")));
        datos.put("modalidad", "dias_semana"); datos.put("diasSemana", "lunes,miercoles");
        var serie = nuevaSerie(datos);
        var marzo = apariciones(llamar(propietario, "GET", "/calendario?desde=2030-03-01&hasta=2030-03-31", null, 200), serie);
        assertThat(marzo.stream().map(n -> n.get("fechaProgramada").asText())).containsExactly(
                "2030-03-04T21:00:00Z", "2030-03-06T21:00:00Z", "2030-03-11T21:00:00Z", "2030-03-13T21:00:00Z",
                "2030-03-18T21:00:00Z", "2030-03-20T21:00:00Z", "2030-03-25T21:00:00Z", "2030-03-27T21:00:00Z");
        var finita = programacionPorIntervalo("2030-01-31T21:00:00Z", 1, "meses");
        finita.put("fechaFin", "2030-03-31T21:00:00Z");
        var finitaId = nuevaSerie(finita);
        var calendario = llamar(propietario, "GET", "/calendario?desde=2030-01-01&hasta=2030-05-31", null, 200);
        assertThat(apariciones(calendario, finitaId)).hasSize(3)
                .allSatisfy(n -> assertThat(n.get("esPersistida").asBoolean()).isTrue());
        actualizarUltima(serie, null);
        assertThat(apariciones(llamar(propietario, "GET", "/calendario?desde=2030-03-01&hasta=2030-03-31", null, 200), serie)).isEmpty();
    }

    @Test void calendarioRevalidaPermisosConElMismoJwtYConservaDatosAlRevocar() throws Exception {
        var serie = nuevaSerie(programacionPorIntervalo("2030-01-01T21:00:00Z", 1, "dias"));
        var ruta = detalleCalendario(serie, "2030-02-01T21:00:00Z");
        var token = jwt.generarToken(cuidador);
        for (var actor : List.of(propietario, familia, cuidador)) {
            assertThat(apariciones(llamar(actor, "GET", "/calendario?desde=2030-02-01&hasta=2030-02-01", null, 200), serie)).hasSize(1);
            llamar(actor, "GET", ruta, null, 200);
        }
        llamar(ajeno, "GET", ruta, null, 403);
        llamarConToken(token, "GET", ruta, null, 200);
        var acceso = accesos.findByPropietario_IdUsuarioAndInvitado_IdUsuarioAndActivoTrue(propietario.getIdUsuario(), cuidador.getIdUsuario()).orElseThrow();
        llamar(propietario, "DELETE", "/cuidado-compartido/accesos/" + acceso.getIdAcceso(), null, 200);
        llamarConToken(token, "GET", ruta, null, 403);
        assertThat(llamarConToken(token, "GET", "/calendario?desde=2030-01-01&hasta=2030-02-01", null, 200).isEmpty()).isTrue();
        assertThat(fechasDeOcurrencias(serie)).hasSize(12);
    }

    @Test void calendarioDescartaFechasAnterioresALaEdicionPausaYBorradoSinBorrarElHistorial() throws Exception {
        var serie = nuevaSerie(programacionPorIntervalo("2029-12-31T21:00:00Z", 1, "dias"));
        var detalle = detalleCalendario(serie, "2030-02-01T21:00:00Z");
        llamar(familia, "GET", detalle, null, 200);
        llamar(familia, "PUT", "/recordatorios/" + serie, programacionPorIntervalo("2029-12-31T20:00:00Z", 1, "dias"), 200);
        llamar(familia, "GET", detalle, null, 404);
        var nuevoDetalle = detalleCalendario(serie, "2030-02-01T20:00:00Z");
        llamar(familia, "GET", nuevoDetalle, null, 200);
        llamar(familia, "PATCH", "/recordatorios/" + serie + "/estado?nuevoEstado=INACTIVO", null, 200);
        var conservadas = fechasDeOcurrencias(serie);
        llamar(familia, "GET", nuevoDetalle, null, 404);
        assertThat(apariciones(llamar(familia, "GET", "/calendario?desde=2030-01-01&hasta=2030-02-01", null, 200), serie)).isEmpty();
        llamar(familia, "DELETE", "/recordatorios/" + serie, null, 204);
        llamar(familia, "GET", nuevoDetalle, null, 404);
        assertThat(fechasDeOcurrencias(serie)).isEqualTo(conservadas);
    }

    @Test void calendarioMuestraConfirmacionesYConservaLaAparicionDeHoyAlConvertirAUnico() throws Exception {
        var serie = nuevaSerie(programacionPorIntervalo("2030-01-01T21:00:00Z", 1, "dias"));
        var ocurrencia = idOcurrencia(serie, "2030-01-01T21:00:00Z");
        llamar(cuidador, "PATCH", rutaOcurrencia(serie, ocurrencia), null, 200);
        llamar(propietario, "PUT", "/recordatorios/" + serie, datosRecordatorio(Instant.parse("2030-01-02T21:00:00Z")), 200);
        var lista = apariciones(llamar(cuidador, "GET", "/calendario?desde=2030-01-01&hasta=2030-01-03", null, 200), serie);
        assertThat(lista).hasSize(2);
        assertThat(lista.getFirst().get("confirmadoPorId").asText()).isEqualTo(cuidador.getIdUsuario().toString());
        assertThat(lista.getFirst().get("situacionTemporal").asText()).isEqualTo("COMPLETADO");
        assertThat(lista.getFirst().get("puedeConfirmar").asBoolean()).isFalse();
        assertThat(lista.getLast().get("idOcurrencia").isNull()).isTrue();
        assertThat(lista.getLast().get("esPersistida").asBoolean()).isTrue();
    }

    @Test void calendarioExcluyeMascotasEliminadasYOrdenaPorIdCuandoCoincideElHorario() throws Exception {
        var empate = nuevoRecordatorio(ahora);
        var eliminada = mascota(propietario);
        var oculto = llamar(propietario, "POST", "/mascotas/" + eliminada.getIdMascota() + "/recordatorios",
                datosRecordatorio(ahora), 201).get("idRecordatorio").asText();
        eliminada.setFechaEliminacion(ahora); mascotas.saveAndFlush(eliminada);
        var lista = llamar(familia, "GET", "/calendario?desde=2030-01-01&hasta=2030-01-01", null, 200);
        assertThat(ids(lista, "idRecordatorio")).containsExactlyElementsOf(List.of(recordatorio, empate).stream().sorted().toList());
        llamar(familia, "GET", detalleCalendario(UUID.fromString(oculto), ahora.toString()), null, 404);
    }

    @Test void calendarioNoReemplazaAlJobSiFaltaLaTandaDeHoy() throws Exception {
        var serie = nuevaSerie(programacionPorIntervalo("2029-12-10T21:00:00Z", 1, "dias"));
        var antes = estadoProgramacion(serie);
        var filas = fechasDeOcurrencias(serie);
        var calendario = apariciones(llamar(propietario, "GET", "/calendario?desde=2030-01-01&hasta=2030-01-03", null, 200), serie);
        assertThat(calendario.stream().map(n -> n.get("fechaProgramada").asText()))
                .containsExactly("2030-01-02T21:00:00Z", "2030-01-03T21:00:00Z");
        assertThat(calendario).allSatisfy(n -> assertThat(n.get("esPersistida").asBoolean()).isFalse());
        assertThat(estadoProgramacion(serie)).isEqualTo(antes);
        assertThat(fechasDeOcurrencias(serie)).isEqualTo(filas);
    }

    private List<JsonNode> apariciones(JsonNode lista, UUID serie) {
        var resultado = new ArrayList<JsonNode>();
        lista.forEach(n -> { if (n.get("idRecordatorio").asText().equals(serie.toString())) resultado.add(n); });
        return resultado;
    }

    private String detalleCalendario(UUID serie, String fecha) {
        return "/calendario/" + serie + "?fechaProgramada=" + fecha;
    }

    private String estadoOcurrencias(UUID serie) {
        return sql.queryForObject("""
                SELECT coalesce(jsonb_agg(to_jsonb(o) ORDER BY id_ocurrencia),'[]'::jsonb)::text
                FROM ocurrencia_recordatorio o WHERE recordatorio_id=?
                """, String.class, serie);
    }

    private void cuandoLasTandasVencen(String instante) {
        when(reloj.instant()).thenReturn(Instant.parse(instante));
        new RenovadorTandasRecordatorioProgramado(recordatorios, servicioRecordatorios, reloj).ejecutar();
    }

    private String rutaOcurrencia(UUID serie, UUID ocurrencia) {
        return "/recordatorios/" + serie + "/confirmacion?idOcurrencia=" + ocurrencia;
    }

    private UUID autorDe(UUID ocurrencia) {
        return sql.queryForObject("SELECT confirmado_por_id FROM ocurrencia_recordatorio WHERE id_ocurrencia=?",
                UUID.class, ocurrencia);
    }

    private LocalDate ultimaFechaDe(UUID serie) {
        return recordatorios.findById(serie).orElseThrow().getUltimaFechaGenerada();
    }

    private UUID idOcurrencia(UUID serie, String fecha) {
        return sql.queryForObject("""
                SELECT id_ocurrencia FROM ocurrencia_recordatorio WHERE recordatorio_id=? AND fecha_programada=?
                """, UUID.class, serie, java.sql.Timestamp.from(Instant.parse(fecha)));
    }

    private List<UUID> confirmadasDe(UUID serie) {
        return sql.queryForList("""
                SELECT id_ocurrencia FROM ocurrencia_recordatorio WHERE recordatorio_id=? AND confirmado_en IS NOT NULL
                """, UUID.class, serie);
    }

    private Map<String, Object> programacionPorIntervalo(String inicio, int valor, String unidad) {
        var datos = new HashMap<>(datosRecordatorio(Instant.parse(inicio)));
        datos.put("modalidad", "recurrente");
        datos.put("intervaloValor", valor);
        datos.put("intervaloUnidad", unidad);
        return datos;
    }

    private List<Instant> fechasDeOcurrencias(UUID serie) {
        return sql.query("""
                SELECT fecha_programada FROM ocurrencia_recordatorio WHERE recordatorio_id=? ORDER BY fecha_programada
                """, (fila, i) -> fila.getTimestamp(1).toInstant(), serie);
    }

    private Map<String, Object> estadoAltas() {
        return sql.queryForMap("""
                SELECT (SELECT count(*) FROM recordatorio WHERE mascota_id=?) AS series,
                    (SELECT count(*) FROM ocurrencia_recordatorio o
                     JOIN recordatorio r ON r.id_recordatorio=o.recordatorio_id WHERE r.mascota_id=?) AS ocurrencias,
                    (SELECT count(*) FROM log_auditoria WHERE entidad_afectada='recordatorio'
                     AND tipo_accion='CREATE' AND actor_id IN (?,?,?)) AS auditoria
                """, mascota.getIdMascota(), mascota.getIdMascota(),
                propietario.getIdUsuario(), familia.getIdUsuario(), cuidador.getIdUsuario());
    }

    private UUID nuevaSerie(Map<String, Object> datos) throws Exception {
        return UUID.fromString(llamar(propietario, "POST", rutaMascota("recordatorios"), datos, 201)
                .get("idRecordatorio").asText());
    }

    private void actualizarUltima(UUID id, LocalDate fecha) {
        sql.update("UPDATE recordatorio SET ultima_fecha_generada=? WHERE id_recordatorio=?", fecha, id);
    }

    private String estadoProgramacion(UUID id) {
        return sql.queryForObject("SELECT to_jsonb(r)::text FROM recordatorio r WHERE id_recordatorio=?", String.class, id);
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
