package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.MisMascotas.backend.Entity.*;
import com.MisMascotas.backend.Integration.MercadoPagoGateway;
import com.MisMascotas.backend.Repository.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** HTTP, transacciones confirmadas y carreras con esperas observadas en PostgreSQL real. */
@SpringBootTest(properties = "spring.datasource.hikari.connection-init-sql=SET lock_timeout = '10s'")
@AutoConfigureMockMvc
@ActiveProfiles("ci")
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_PREMIUM_IT", matches = "true")
class BadgePremiumPostgresIT {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate sql;
    @Autowired PlatformTransactionManager transacciones;
    @Autowired UsuarioRepository usuarios;
    @Autowired MascotaRepository mascotas;
    @Autowired BadgeRepository badges;
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

    @BeforeEach void preparar() {
        when(reloj.instant()).thenReturn(ahora);
        propietario = usuario(false); familia = usuario(false); cuidador = usuario(true); ajeno = usuario(true);
        mascota = mascota(propietario);
        acceso(familia, PerfilCompartido.Familia); acceso(cuidador, PerfilCompartido.Cuidador);
    }

    @AfterEach void limpiar() {
        for (var uid : cuentas) {
            sql.update("DELETE FROM evento_auditoria_pendiente WHERE actor_id=?", uid);
            sql.update("DELETE FROM log_auditoria WHERE actor_id=?", uid);
            sql.update("DELETE FROM acceso_compartido WHERE propietario_id=? OR invitado_id=?", uid, uid);
            sql.update("DELETE FROM badge WHERE mascota_id IN (SELECT id_mascota FROM mascota WHERE propietario_id=?)", uid);
            sql.update("DELETE FROM mascota WHERE propietario_id=?", uid);
            sql.update("DELETE FROM pago WHERE suscripcion_id IN (SELECT id_suscripcion FROM suscripcion WHERE usuario_id=?)", uid);
            sql.update("DELETE FROM suscripcion WHERE usuario_id=?", uid);
            sql.update("DELETE FROM usuario WHERE id_usuario=?", uid);
        }
        verifyNoInteractions(proveedor, correo);
    }

    @ParameterizedTest @ValueSource(strings = {"free", "vencido", "exacto", "sin_pago", "sin_fecha"})
    void sinPremiumNoCreaPeroConservaLecturaYBorradoDelPropietario(String caso) throws Exception {
        if (!"free".equals(caso)) cobertura(caso);
        var previa = badge(mascota, false);
        var antes = estadoDatos();
        assertThat(llamar(propietario, "POST", rutaAlta(), datos(), 403).get("codigo").asText()).isEqualTo("PREMIUM_REQUERIDO");
        assertThat(estadoDatos()).isEqualTo(antes);
        for (var actor : List.of(propietario, familia, cuidador)) {
            assertThat(llamar(actor, "GET", rutaAlta(), null, 200).size()).isEqualTo(1);
            llamar(actor, "GET", ruta(previa), null, 200);
        }
        llamar(propietario, "DELETE", ruta(previa), null, 204);
        assertThat(badges.findById(previa.getIdBadge()).orElseThrow().getFechaEliminacion()).isNotNull();
        assertThat(cantidad()).isZero();
        for (var actor : List.of(propietario, familia, cuidador)) llamar(actor, "GET", ruta(previa), null, 404);
    }

    @ParameterizedTest @ValueSource(strings = {"manual", "mensual", "anual", "cancelada", "pausada"})
    void premiumCreaLaTerceraYRechazaLaCuartaSinEscriturasNiAuditoria(String caso) throws Exception {
        cobertura(caso); badge(mascota, false); badge(mascota, false);
        var tercera = llamar(propietario, "POST", rutaAlta(), datos(), 201);
        assertThat(tercera.get("mascotaId").asText()).isEqualTo(mascota.getIdMascota().toString());
        assertThat(tercera.get("texto").asText()).isEqualTo("Sociable");
        assertThat(tercera.get("emoji").asText()).isEqualTo("★");
        assertThat(tercera.size()).isEqualTo(4);
        assertThat(cantidad()).isEqualTo(3);
        var antes = estadoDatos();
        var error = llamar(propietario, "POST", rutaAlta(), datos(), 409);
        assertThat(error.get("codigo").asText()).isEqualTo("LIMITE_BADGES_ALCANZADO");
        assertThat(error.get("mensaje").asText()).isEqualTo("Alcanzaste el limite de 3 badges activas por mascota.");
        assertThat(error.hasNonNull("timestamp")).isTrue();
        assertThat(estadoDatos()).isEqualTo(antes);
        assertThat(auditorias("CREATE")).isEqualTo(1);
    }

    @Test void eliminadasYOtrasMascotasNoConsumenCupoYLosDuplicadosEstanPermitidos() throws Exception {
        cobertura("manual");
        badge(mascota, true); badge(mascota, true);
        var otra = mascota(propietario); for (int n=0; n<3; n++) badge(otra, false);
        var ids = new HashSet<String>();
        for (int n=0; n<3; n++) ids.add(llamar(propietario, "POST", rutaAlta(), datos(), 201).get("idBadge").asText());
        assertThat(ids).hasSize(3);
        assertThat(cantidad()).isEqualTo(3);
        assertThat(sql.queryForObject("SELECT count(*) FROM badge WHERE mascota_id=?", Integer.class, mascota.getIdMascota())).isEqualTo(5);
        llamar(propietario, "DELETE", "/badges/"+ids.iterator().next(), null, 204);
        llamar(propietario, "POST", rutaAlta(), datos(), 201);
        assertThat(cantidad()).isEqualTo(3);
        assertThat(badges.countByMascotaIdMascotaAndFechaEliminacionIsNull(otra.getIdMascota())).isEqualTo(3);
    }

    @ParameterizedTest @ValueSource(strings = {"manual", "vencido"})
    void historicoExcedidoSeConservaYSoloVuelveACrearAlBajarDeTresConPremium(String caso) throws Exception {
        cobertura(caso);
        var previas = new ArrayList<Badge>(); for (int n=0; n<5; n++) previas.add(badge(mascota, false));
        var antes = estadoDatos();
        llamar(propietario, "POST", rutaAlta(), datos(), "manual".equals(caso) ? 409 : 403);
        for (var actor : List.of(propietario, familia, cuidador)) assertThat(llamar(actor, "GET", rutaAlta(), null, 200).size()).isEqualTo(5);
        assertThat(estadoDatos()).isEqualTo(antes);
        for (int n=0; n<2; n++) llamar(propietario, "DELETE", ruta(previas.get(n)), null, 204);
        if ("vencido".equals(caso)) cobertura("mensual");
        llamar(propietario, "POST", rutaAlta(), datos(), 409);
        llamar(propietario, "DELETE", ruta(previas.get(2)), null, 204);
        llamar(propietario, "POST", rutaAlta(), datos(), 201);
        assertThat(cantidad()).isEqualTo(3);
        assertThat(sql.queryForObject("SELECT count(*) FROM badge WHERE mascota_id=?", Integer.class, mascota.getIdMascota())).isEqualTo(6);
    }

    @ParameterizedTest @ValueSource(strings = {"free", "manual"})
    void invitadosYAjenoNoCreanNiBorranAunqueTenganPremium(String caso) throws Exception {
        if ("manual".equals(caso)) cobertura(caso);
        var previa = badge(mascota, false); var antes = estadoDatos();
        for (var actor : List.of(familia, cuidador, ajeno)) {
            assertThat(llamar(actor, "POST", rutaAlta(), datos(), 403).get("codigo").asText()).isEqualTo("ACCESO_DENEGADO");
            assertThat(llamar(actor, "DELETE", ruta(previa), null, 403).get("codigo").asText()).isEqualTo("ACCESO_DENEGADO");
        }
        llamar(familia, "GET", ruta(previa), null, 200); llamar(cuidador, "GET", ruta(previa), null, 200);
        llamar(ajeno, "GET", ruta(previa), null, 403);
        assertThat(estadoDatos()).isEqualTo(antes);
    }

    @Test void prevaleceLaMascotaDeLaRutaYSeConservanValidacionesYNoEncontrados() throws Exception {
        cobertura("manual");
        var falso = new HashMap<>(datos()); falso.put("mascotaId", mascota(ajeno).getIdMascota());
        assertThat(llamar(propietario, "POST", rutaAlta(), falso, 201).get("mascotaId").asText()).isEqualTo(mascota.getIdMascota().toString());
        var antes = estadoDatos(); var invalido = new HashMap<>(datos()); invalido.put("texto", " ");
        llamar(propietario, "POST", rutaAlta(), invalido, 400);
        llamar(propietario, "POST", "/mascotas/"+UUID.randomUUID()+"/badges", datos(), 404);
        assertThat(estadoDatos()).isEqualTo(antes);
        mascota.setFechaEliminacion(ahora); mascotas.saveAndFlush(mascota);
        llamar(propietario, "POST", rutaAlta(), datos(), 404);
    }

    @Test void dosAltasRealmenteBloqueadasCompitenPorUnSoloLugar() throws Exception {
        cobertura("manual"); badge(mascota, false); badge(mascota, false);
        try (var hilos = Executors.newFixedThreadPool(2)) {
            var trabajos = new ArrayList<Future<Resultado>>();
            new TransactionTemplate(transacciones).executeWithoutResult(tx -> {
                bloquearPropietarioDesdePrueba();
                for (int n=0; n<2; n++) trabajos.add(hilos.submit(() -> ejecutar(propietario, "POST", rutaAlta(), datos())));
                esperarSolicitudesBloqueadas(2);
            });
            var resultados = List.of(trabajos.get(0).get(20, TimeUnit.SECONDS), trabajos.get(1).get(20, TimeUnit.SECONDS));
            assertThat(resultados).extracting(Resultado::estado).containsExactlyInAnyOrder(201, 409);
            assertThat(resultados.stream().filter(r -> r.estado()==409).findFirst().orElseThrow().datos().get("codigo").asText()).isEqualTo("LIMITE_BADGES_ALCANZADO");
        }
        assertThat(cantidad()).isEqualTo(3);
        assertThat(sql.queryForObject("SELECT count(*) FROM badge WHERE mascota_id=?", Integer.class, mascota.getIdMascota())).isEqualTo(3);
        assertThat(auditorias("CREATE")).isEqualTo(1);
    }

    @Test void revalidaPremiumAlObtenerElBloqueoSinUsarElIndicadorPrecargado() throws Exception {
        cobertura("manual"); badge(mascota, false); badge(mascota, false);
        try (var hilo = Executors.newSingleThreadExecutor()) {
            var trabajos = new ArrayList<Future<Resultado>>();
            new TransactionTemplate(transacciones).executeWithoutResult(tx -> {
                bloquearPropietarioDesdePrueba();
                sql.update("UPDATE usuario SET es_premium=false WHERE id_usuario=?", propietario.getIdUsuario());
                trabajos.add(hilo.submit(() -> ejecutar(propietario, "POST", rutaAlta(), datos())));
                esperarSolicitudesBloqueadas(1);
            });
            var resultado = trabajos.getFirst().get(20, TimeUnit.SECONDS);
            assertThat(resultado.estado()).isEqualTo(403);
            assertThat(resultado.datos().get("codigo").asText()).isEqualTo("PREMIUM_REQUERIDO");
        }
        assertThat(cantidad()).isEqualTo(2);
        assertThat(auditorias("CREATE")).isZero();
    }

    @Test void dosBorradosSimultaneosNoAuditanDosVecesLaMismaEliminacion() throws Exception {
        var previa = badge(mascota, false);
        try (var hilos = Executors.newFixedThreadPool(2)) {
            var trabajos = new ArrayList<Future<Resultado>>();
            new TransactionTemplate(transacciones).executeWithoutResult(tx -> {
                bloquearPropietarioDesdePrueba();
                for (int n=0; n<2; n++) trabajos.add(hilos.submit(() -> ejecutar(propietario, "DELETE", ruta(previa), null)));
                esperarSolicitudesBloqueadas(2);
            });
            assertThat(List.of(trabajos.get(0).get(20, TimeUnit.SECONDS).estado(), trabajos.get(1).get(20, TimeUnit.SECONDS).estado())).containsExactlyInAnyOrder(204, 404);
        }
        assertThat(cantidad()).isZero();
        assertThat(auditorias("DELETE")).isEqualTo(1);
        assertThat(badges.findById(previa.getIdBadge())).isPresent();
    }

    private void bloquearPropietarioDesdePrueba() { sql.queryForObject("SELECT id_usuario FROM usuario WHERE id_usuario=? FOR UPDATE", UUID.class, propietario.getIdUsuario()); }
    private void esperarSolicitudesBloqueadas(int esperadas) {
        long limite = System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime()<limite) {
            sql.execute("SELECT pg_stat_clear_snapshot()");
            int esperando = sql.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND query ILIKE '%usuario%'", Integer.class);
            if (esperando>=esperadas) return;
            try { Thread.sleep(25); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
        }
        throw new AssertionError("No se observaron las "+esperadas+" solicitudes esperando bloqueo real en PostgreSQL");
    }
    private long cantidad() { return badges.countByMascotaIdMascotaAndFechaEliminacionIsNull(mascota.getIdMascota()); }
    private int auditorias(String accion) { return sql.queryForObject("SELECT count(*) FROM log_auditoria WHERE actor_id=? AND entidad_afectada='badge' AND tipo_accion=?", Integer.class, propietario.getIdUsuario(), accion); }
    private Map<String, Object> estadoDatos() {
        var r = new HashMap<String, Object>();
        r.put("badges", sql.queryForList("SELECT b.* FROM badge b JOIN mascota m ON b.mascota_id=m.id_mascota WHERE m.propietario_id=? ORDER BY b.id_badge", propietario.getIdUsuario()));
        r.put("propietario", sql.queryForList("SELECT * FROM usuario WHERE id_usuario=?", propietario.getIdUsuario()));
        for (var uid : cuentas) {
            r.put("auditoria-"+uid, sql.queryForList("SELECT * FROM log_auditoria WHERE actor_id=? ORDER BY id_log", uid));
            r.put("pendientes-"+uid, sql.queryForList("SELECT * FROM evento_auditoria_pendiente WHERE actor_id=? ORDER BY id_evento", uid));
        }
        return r;
    }
    private void cobertura(String caso) {
        if (List.of("manual", "vencido", "exacto", "sin_fecha").contains(caso)) { propietario.setEsPremium(true); propietario=usuarios.saveAndFlush(propietario); }
        if ("manual".equals(caso)) return;
        var s=new Suscripcion(); s.setUsuario(propietario); s.setTipoPlan("anual".equals(caso)?"PREMIUM_ANUAL":"PREMIUM_MENSUAL");
        s.setEstadoCatalogo(estados.findByEntidadAndNombre("SUSCRIPCION","cancelada".equals(caso)?"CANCELADA":"ACTIVA").orElseThrow());
        s.setEstadoMercadopago("cancelada".equals(caso)?"cancelled":"pausada".equals(caso)?"paused":"authorized");
        s.setFechaVencimiento("sin_fecha".equals(caso)?null:"exacto".equals(caso)?ahora:"vencido".equals(caso)?ahora.minusSeconds(1):ahora.plusSeconds(3600));
        s=suscripciones.saveAndFlush(s);
        var p=new Pago(); p.setSuscripcion(s); p.setMonto(new BigDecimal("anual".equals(caso)?"40000":"4000")); p.setFechaPago(ahora.minusSeconds(3600));
        p.setEstadoCatalogo(estados.findByEntidadAndNombre("PAGO","sin_pago".equals(caso)?"RECHAZADO":"APROBADO").orElseThrow()); pagos.saveAndFlush(p);
    }
    private Usuario usuario(boolean premium) { var u=new Usuario(); u.setNombre("Badges QA"); u.setEmail(UUID.randomUUID()+"@example.test"); u.setPasswordHash("hash-ficticio"); u.setEsPremium(premium); u=usuarios.saveAndFlush(u); cuentas.add(u.getIdUsuario()); return u; }
    private Mascota mascota(Usuario u) { var m=new Mascota(); m.setPropietario(u); m.setNombre("Mascota badges"); m.setEspecie("Perro"); m.setFechaNacimiento(LocalDate.of(2020,1,1)); return mascotas.saveAndFlush(m); }
    private Badge badge(Mascota m, boolean eliminada) { var b=new Badge(); b.setMascota(m); b.setTexto("Sociable"); b.setEmoji("★"); if(eliminada)b.setFechaEliminacion(ahora); return badges.saveAndFlush(b); }
    private void acceso(Usuario u, PerfilCompartido perfil) { accesos.saveAndFlush(AccesoCompartido.builder().propietario(propietario).invitado(u).perfil(perfil).fechaIngreso(ahora).build()); }
    private String rutaAlta() { return "/mascotas/"+mascota.getIdMascota()+"/badges"; }
    private String ruta(Badge b) { return "/badges/"+b.getIdBadge(); }
    private Map<String,Object> datos() { return Map.of("mascotaId",mascota.getIdMascota().toString(),"texto","Sociable","emoji","★"); }
    private JsonNode llamar(Usuario actor,String metodo,String ruta,Object cuerpo,int esperado)throws Exception { var r=ejecutar(actor,metodo,ruta,cuerpo); assertThat(r.estado()).as(metodo+" "+ruta+": "+r.datos()).isEqualTo(esperado); return r.datos(); }
    private Resultado ejecutar(Usuario actor,String metodo,String ruta,Object cuerpo)throws Exception {
        var p=request(HttpMethod.valueOf(metodo),ruta).with(user(actor.getEmail()));
        if(cuerpo!=null)p.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo));
        var r=mvc.perform(p).andReturn().getResponse();
        if(r.getStatus()==204)assertThat(r.getContentAsString()).isEmpty();
        return new Resultado(r.getStatus(),r.getContentAsString().isEmpty()?null:json.readTree(r.getContentAsString()));
    }
    private record Resultado(int estado,JsonNode datos) {}
}
