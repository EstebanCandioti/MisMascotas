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
import org.springframework.http.*;
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

/** Operaciones HTTP con transacciones reales: el rechazo no modifica datos ni auditoría. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("ci")
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_PREMIUM_IT", matches = "true")
class AlbumPremiumPostgresIT {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate sql;
    @Autowired UsuarioRepository usuarios;
    @Autowired MascotaRepository mascotas;
    @Autowired AlbumRepository albumes;
    @Autowired AlbumMascotaRepository relaciones;
    @Autowired FotoRepository fotos;
    @Autowired AccesoCompartidoRepository accesos;
    @Autowired SuscripcionRepository suscripciones;
    @Autowired PagoRepository pagos;
    @Autowired EstadoRepository estados;
    @MockitoBean Clock reloj;
    @MockitoBean EmailService correo;
    @MockitoBean MercadoPagoGateway proveedor;
    final JsonMapper json = JsonMapper.builder().build();
    final Instant ahora = Instant.parse("2030-01-31T15:00:00Z");
    final List<UUID> cuentas = new ArrayList<>();
    Usuario propietario, familia, cuidador, ajeno;
    Mascota mascota;
    Album album;
    Foto foto;

    @BeforeEach void preparar() {
        when(reloj.instant()).thenReturn(ahora);
        propietario = usuario(false); familia = usuario(true); cuidador = usuario(true); ajeno = usuario(true);
        mascota = mascota(propietario);
        acceso(familia, PerfilCompartido.Familia); acceso(cuidador, PerfilCompartido.Cuidador);
        // Recuerdo previo al vencimiento: no se usa la API para eludir la regla bajo prueba.
        album = albumes.saveAndFlush(Album.builder().creadoPor(propietario).nombre("Recuerdos previos").build());
        asociar(mascota);
        foto = fotos.saveAndFlush(Foto.builder().album(album).subidaPor(propietario)
                .urlArchivo("https://example.test/previa.jpg").formato("jpg").build());
    }

    @AfterEach void limpiar() {
        for (var id : cuentas) {
            sql.update("DELETE FROM evento_auditoria_pendiente WHERE actor_id=?", id);
            sql.update("DELETE FROM log_auditoria WHERE actor_id=?", id);
        }
        sql.update("DELETE FROM foto WHERE album_id IN (SELECT id_album FROM album_mascota WHERE id_mascota=?)", mascota.getIdMascota());
        sql.update("DELETE FROM foto WHERE album_id=?", album.getIdAlbum());
        sql.update("DELETE FROM album_mascota WHERE id_album=? OR id_mascota=?", album.getIdAlbum(), mascota.getIdMascota());
        for (var id : cuentas) {
            sql.update("DELETE FROM album WHERE creado_por_id=?", id);
            sql.update("DELETE FROM acceso_compartido WHERE propietario_id=? OR invitado_id=?", id, id);
            sql.update("DELETE FROM mascota WHERE propietario_id=?", id);
            sql.update("DELETE FROM pago WHERE suscripcion_id IN (SELECT id_suscripcion FROM suscripcion WHERE usuario_id=?)", id);
            sql.update("DELETE FROM suscripcion WHERE usuario_id=?", id);
            sql.update("DELETE FROM usuario WHERE id_usuario=?", id);
        }
        verifyNoInteractions(proveedor, correo);
    }

    @ParameterizedTest @ValueSource(strings = {"free", "vencido", "exacto", "sin_pago", "sin_fecha"})
    void sinPremiumNoCreaNiEditaNiCargaFotosYConservaTodo(String caso) throws Exception {
        if (!"free".equals(caso)) cobertura(caso);
        var antes = estadoDatos();
        rechazarGestion(propietario, "PREMIUM_REQUERIDO");
        assertThat(estadoDatos()).isEqualTo(antes);
        for (var actor : List.of(propietario, familia, cuidador)) comprobarLectura(actor);
        assertThat(estadoDatos()).isEqualTo(antes);
    }

    @ParameterizedTest @ValueSource(strings = {"manual", "mensual", "anual", "cancelada", "pausada"})
    void premiumGestionaConContratosActualesYBorradoLogicoEnCascada(String caso) throws Exception {
        cobertura(caso);
        var creado = llamar(propietario, "POST", rutaAlta(), datosAlbum("Nuevo"), 201);
        assertThat(creado.get("nombre").asText()).isEqualTo("Nuevo");
        assertThat(creado.get("creadoPorId").asText()).isEqualTo(propietario.getIdUsuario().toString());
        assertThat(creado.get("mascotaId").asText()).isEqualTo(mascota.getIdMascota().toString());
        assertThat(creado.get("cantidadFotos").asInt()).isZero();
        assertThat(creado.has("esPremium")).isFalse();
        var editado = llamar(propietario, "PUT", rutaAlbum(), datosAlbum("Editado"), 200);
        assertThat(editado.get("nombre").asText()).isEqualTo("Editado");
        assertThat(editado.get("cantidadFotos").asInt()).isEqualTo(1);
        var nuevaFoto = llamar(propietario, "POST", rutaFotos(), datosFoto(), 201);
        assertThat(nuevaFoto.get("albumId").asText()).isEqualTo(album.getIdAlbum().toString());
        assertThat(nuevaFoto.get("subidaPorId").asText()).isEqualTo(propietario.getIdUsuario().toString());
        llamar(propietario, "DELETE", rutaAlbum(), null, 204);
        assertThat(albumes.findById(album.getIdAlbum()).orElseThrow().getFechaEliminacion()).isNotNull();
        assertThat(sql.queryForObject("SELECT count(*) FROM album_mascota WHERE id_album=? AND fecha_eliminacion IS NULL", Integer.class, album.getIdAlbum())).isZero();
        assertThat(sql.queryForObject("SELECT count(*) FROM foto WHERE album_id=? AND fecha_eliminacion IS NULL", Integer.class, album.getIdAlbum())).isZero();
        assertThat(sql.queryForObject("SELECT count(*) FROM foto WHERE album_id=?", Integer.class, album.getIdAlbum())).isEqualTo(2);
        for (var actor : List.of(propietario, familia, cuidador)) {
            llamar(actor, "GET", rutaAlbum(), null, 404);
            llamar(actor, "GET", "/fotos/" + foto.getIdFoto(), null, 404);
        }
        assertThat(sql.queryForObject("SELECT count(*) FROM log_auditoria WHERE actor_id=? AND entidad_afectada='album'", Integer.class, propietario.getIdUsuario())).isEqualTo(3);
        assertThat(albumes.findById(UUID.fromString(creado.get("idAlbum").asText())).orElseThrow().getFechaEliminacion()).isNull();
    }

    @ParameterizedTest @ValueSource(strings = {"free", "manual"})
    void invitadosPremiumYAjenoNoObtienenEscrituraAunqueElPropietarioSeaPremium(String plan) throws Exception {
        if ("manual".equals(plan)) cobertura(plan);
        var antes = estadoDatos();
        for (var actor : List.of(familia, cuidador, ajeno)) rechazarGestion(actor, "ACCESO_DENEGADO");
        for (var actor : List.of(familia, cuidador, ajeno)) {
            assertThat(llamar(actor, "DELETE", rutaAlbum(), null, 403).get("codigo").asText()).isEqualTo("ACCESO_DENEGADO");
            assertThat(llamar(actor, "DELETE", "/fotos/" + foto.getIdFoto(), null, 403).get("codigo").asText()).isEqualTo("ACCESO_DENEGADO");
        }
        comprobarLectura(familia); comprobarLectura(cuidador);
        llamar(ajeno, "GET", rutaAlbum(), null, 403);
        assertThat(estadoDatos()).isEqualTo(antes);
    }

    @ParameterizedTest @ValueSource(strings = {"free", "vencido", "exacto", "manual", "mensual"})
    void propietarioPuedeEliminarFotoIndividualYAlbumSinExigirPremium(String caso) throws Exception {
        if (!"free".equals(caso)) cobertura(caso);
        comprobarLectura(propietario); comprobarLectura(familia); comprobarLectura(cuidador);
        llamar(propietario, "DELETE", "/fotos/" + foto.getIdFoto(), null, 204);
        assertThat(fotos.findById(foto.getIdFoto()).orElseThrow().getFechaEliminacion()).isNotNull();
        assertThat(albumes.findById(album.getIdAlbum()).orElseThrow().getFechaEliminacion()).isNull();
        assertThat(llamar(propietario, "GET", rutaAlbum(), null, 200).get("cantidadFotos").asInt()).isZero();
        var otraFoto = fotos.saveAndFlush(Foto.builder().album(album).subidaPor(propietario)
                .urlArchivo("https://example.test/otra-previa.jpg").formato("jpg").build());
        llamar(propietario, "DELETE", rutaAlbum(), null, 204);
        assertThat(albumes.findById(album.getIdAlbum()).orElseThrow().getFechaEliminacion()).isNotNull();
        assertThat(fotos.findById(otraFoto.getIdFoto()).orElseThrow().getFechaEliminacion()).isNotNull();
        assertThat(sql.queryForObject("SELECT count(*) FROM album_mascota WHERE id_album=? AND fecha_eliminacion IS NULL", Integer.class, album.getIdAlbum())).isZero();
        assertThat(sql.queryForObject("SELECT count(*) FROM foto WHERE album_id=?", Integer.class, album.getIdAlbum())).isEqualTo(2);
        assertThat(sql.queryForObject("SELECT count(*) FROM log_auditoria WHERE actor_id=? AND entidad_afectada IN ('foto','album')", Integer.class, propietario.getIdUsuario())).isEqualTo(2);
        for (var actor : List.of(propietario, familia, cuidador)) {
            llamar(actor, "GET", rutaAlbum(), null, 404);
            llamar(actor, "GET", "/fotos/" + foto.getIdFoto(), null, 404);
            llamar(actor, "GET", "/fotos/" + otraFoto.getIdFoto(), null, 404);
        }
    }

    @Test void alVencerSeBloqueaLaGestionSinCambiarLaSesionNiElIndicador() throws Exception {
        cobertura("mensual");
        llamar(propietario, "PUT", rutaAlbum(), datosAlbum("Antes del vencimiento"), 200);
        when(reloj.instant()).thenReturn(ahora.plusSeconds(3600));
        var antes = estadoDatos();
        rechazarGestion(propietario, "PREMIUM_REQUERIDO");
        comprobarLectura(propietario); comprobarLectura(familia); comprobarLectura(cuidador);
        assertThat(estadoDatos()).isEqualTo(antes);
    }

    @Test void decideElPlanDelPropietarioDeLaMascotaNoDelCreadorHistoricoDelAlbum() throws Exception {
        album.setCreadoPor(ajeno); albumes.saveAndFlush(album);
        var antes = estadoDatos();
        rechazarGestion(propietario, "PREMIUM_REQUERIDO");
        assertThat(estadoDatos()).isEqualTo(antes);
    }

    @Test void variasMascotasPropiasConservanAsociacionesAunqueElDtoIntenteReasignarlas() throws Exception {
        cobertura("manual");
        var segunda = mascota(propietario); asociar(segunda);
        var falsa = new HashMap<>(datosAlbum("Sin reasignar")); falsa.put("mascotaId", mascota(ajeno).getIdMascota());
        llamar(propietario, "PUT", rutaAlbum(), falsa, 200);
        assertThat(relaciones.findAllByAlbumIdActivos(album.getIdAlbum())).extracting(r -> r.getMascota().getIdMascota())
                .containsExactlyInAnyOrder(mascota.getIdMascota(), segunda.getIdMascota());
        comprobarLectura(familia);
    }

    @Test void otraMascotaSinPermisoRechazaTodaLaOperacionAntesDeEvaluarElPlan() throws Exception {
        asociar(mascota(ajeno));
        var antes = estadoDatos();
        for (var metodo : List.of("PUT", "DELETE"))
            assertThat(llamar(propietario, metodo, rutaAlbum(), "PUT".equals(metodo) ? datosAlbum("No guardar") : null, 403).get("codigo").asText()).isEqualTo("ACCESO_DENEGADO");
        assertThat(llamar(propietario, "POST", rutaFotos(), datosFoto(), 403).get("codigo").asText()).isEqualTo("ACCESO_DENEGADO");
        llamar(propietario, "GET", rutaAlbum(), null, 403);
        assertThat(estadoDatos()).isEqualTo(antes);
    }

    @Test void sinAsociacionesActivasSeConservaEl404YNoSeBorraNada() throws Exception {
        sql.update("UPDATE album_mascota SET fecha_eliminacion=? WHERE id_album=?", java.sql.Timestamp.from(ahora), album.getIdAlbum());
        var antes = estadoDatos();
        llamar(propietario, "PUT", rutaAlbum(), datosAlbum("No"), 404);
        llamar(propietario, "DELETE", rutaAlbum(), null, 404);
        llamar(propietario, "POST", rutaFotos(), datosFoto(), 404);
        assertThat(estadoDatos()).isEqualTo(antes);
    }

    @Test void validacionYRecursosEliminadosConservanSusContratos() throws Exception {
        cobertura("manual");
        var antes = estadoDatos();
        llamar(propietario, "POST", rutaAlta(), datosAlbum(" "), 400);
        llamar(propietario, "PUT", rutaAlbum(), datosAlbum(" "), 400);
        llamar(propietario, "DELETE", "/albumes/" + UUID.randomUUID(), null, 404);
        assertThat(estadoDatos()).isEqualTo(antes);
        llamar(propietario, "DELETE", rutaAlbum(), null, 204);
        llamar(propietario, "PUT", rutaAlbum(), datosAlbum("No revivir"), 404);
        llamar(propietario, "DELETE", rutaAlbum(), null, 404);
        llamar(propietario, "POST", rutaFotos(), datosFoto(), 404);
    }

    private void rechazarGestion(Usuario actor, String codigo) throws Exception {
        assertThat(llamar(actor, "POST", rutaAlta(), datosAlbum("No crear"), 403).get("codigo").asText()).isEqualTo(codigo);
        assertThat(llamar(actor, "PUT", rutaAlbum(), datosAlbum("No editar"), 403).get("codigo").asText()).isEqualTo(codigo);
        assertThat(llamar(actor, "POST", rutaFotos(), datosFoto(), 403).get("codigo").asText()).isEqualTo(codigo);
    }

    private void comprobarLectura(Usuario actor) throws Exception {
        llamar(actor, "GET", rutaAlta(), null, 200);
        llamar(actor, "GET", rutaAlbum(), null, 200);
        llamar(actor, "GET", rutaFotos(), null, 200);
        llamar(actor, "GET", "/fotos/" + foto.getIdFoto(), null, 200);
    }

    private Map<String, Object> estadoDatos() {
        var resultado = new HashMap<String, Object>();
        resultado.put("album", sql.queryForList("SELECT * FROM album WHERE id_album=? OR creado_por_id=? ORDER BY id_album", album.getIdAlbum(), propietario.getIdUsuario()));
        resultado.put("relaciones", sql.queryForList("SELECT * FROM album_mascota WHERE id_album=? ORDER BY id_album_mascota", album.getIdAlbum()));
        resultado.put("fotos", sql.queryForList("SELECT * FROM foto WHERE album_id=? ORDER BY id_foto", album.getIdAlbum()));
        resultado.put("usuario", sql.queryForList("SELECT * FROM usuario WHERE id_usuario=?", propietario.getIdUsuario()));
        resultado.put("pagos", sql.queryForList("SELECT p.* FROM pago p JOIN suscripcion s ON p.suscripcion_id=s.id_suscripcion WHERE s.usuario_id=? ORDER BY p.id_pago", propietario.getIdUsuario()));
        for (var id : cuentas) {
            resultado.put("auditoria-" + id, sql.queryForList("SELECT * FROM log_auditoria WHERE actor_id=? ORDER BY id_log", id));
            resultado.put("pendientes-" + id, sql.queryForList("SELECT * FROM evento_auditoria_pendiente WHERE actor_id=? ORDER BY id_evento", id));
        }
        return resultado;
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
        var u = new Usuario(); u.setNombre("Álbumes Premium QA"); u.setEmail(UUID.randomUUID() + "@example.test");
        u.setPasswordHash("hash-ficticio"); u.setEsPremium(premium); u = usuarios.saveAndFlush(u); cuentas.add(u.getIdUsuario()); return u;
    }
    private Mascota mascota(Usuario u) {
        var m = new Mascota(); m.setPropietario(u); m.setNombre("Mascota QA"); m.setEspecie("Perro"); m.setFechaNacimiento(LocalDate.of(2020, 1, 1)); return mascotas.saveAndFlush(m);
    }
    private void asociar(Mascota m) { relaciones.saveAndFlush(AlbumMascota.builder().album(album).mascota(m).build()); }
    private void acceso(Usuario u, PerfilCompartido perfil) { accesos.saveAndFlush(AccesoCompartido.builder().propietario(propietario).invitado(u).perfil(perfil).fechaIngreso(ahora).build()); }
    private String rutaAlta() { return "/mascotas/" + mascota.getIdMascota() + "/albumes"; }
    private String rutaAlbum() { return "/albumes/" + album.getIdAlbum(); }
    private String rutaFotos() { return rutaAlbum() + "/fotos"; }
    private Map<String, Object> datosAlbum(String nombre) { return Map.of("nombre", nombre, "mascotaId", mascota.getIdMascota().toString()); }
    private Map<String, Object> datosFoto() { return Map.of("urlArchivo", "https://example.test/nueva.jpg", "formato", "jpg"); }
    private JsonNode llamar(Usuario actor, String metodo, String ruta, Object cuerpo, int esperado) throws Exception {
        var peticion = request(HttpMethod.valueOf(metodo), ruta).with(user(actor.getEmail()));
        if (cuerpo != null) peticion.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo));
        var respuesta = mvc.perform(peticion).andReturn().getResponse();
        assertThat(respuesta.getStatus()).as(metodo + " " + ruta + ": " + respuesta.getContentAsString()).isEqualTo(esperado);
        if (esperado == 204) assertThat(respuesta.getContentAsString()).isEmpty();
        return respuesta.getContentAsString().isEmpty() ? null : json.readTree(respuesta.getContentAsString());
    }
}
