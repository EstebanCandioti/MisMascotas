package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import com.MisMascotas.backend.Entity.*;
import com.MisMascotas.backend.Repository.*;
import com.MisMascotas.backend.Integration.MercadoPagoGateway;
import com.MisMascotas.backend.Security.JwtService;
import tools.jackson.databind.json.JsonMapper;

/** Flujo HTTP con transacciones confirmadas y PostgreSQL real; sin SMTP ni MP. */
@SpringBootTest(properties = "spring.datasource.hikari.connection-init-sql=SET lock_timeout = '8s'")
@ActiveProfiles("ci")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_CUIDADO_IT", matches = "true")
class CuidadoCompartidoPostgresIT {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired MascotaRepository mascotas;
    @Autowired InvitacionRepository invitaciones;
    @Autowired AccesoCompartidoRepository accesos;
    @Autowired EstadoRepository estados;
    @Autowired SuscripcionRepository suscripciones;
    @Autowired PagoRepository pagos;
    @Autowired JdbcTemplate sql;
    @Autowired JwtService jwt;
    @MockitoBean Clock reloj;
    @MockitoBean MercadoPagoGateway proveedor;
    @MockitoBean EmailService correo;
    @MockitoSpyBean EventoAuditoriaPendienteService auditoria;
    final JsonMapper json = JsonMapper.builder().build();
    final Instant ahora = Instant.parse("2030-01-01T12:00:00Z");
    final List<UUID> cuentas = new ArrayList<>();
    Usuario propietario, invitado, otro;
    static final String RAIZ = "/cuidado-compartido";

    @BeforeEach void preparar() {
        when(reloj.instant()).thenReturn(ahora);
        propietario = usuario(); invitado = usuario(); otro = usuario(); mascota(propietario);
    }

    @AfterEach void limpiar() {
        for (var id : cuentas) {
            sql.update("DELETE FROM evento_auditoria_pendiente WHERE actor_id = ?", id);
            sql.update("DELETE FROM log_auditoria WHERE actor_id = ?", id);
        }
        for (var id : cuentas) sql.update("DELETE FROM invitacion WHERE propietario_id = ?", id);
        for (var id : cuentas) {
            sql.update("DELETE FROM acceso_compartido WHERE propietario_id = ? OR invitado_id = ?", id, id);
            sql.update("DELETE FROM mascota WHERE propietario_id = ?", id);
            sql.update("DELETE FROM pago WHERE suscripcion_id IN (SELECT id_suscripcion FROM suscripcion WHERE usuario_id = ?)", id);
            sql.update("DELETE FROM suscripcion WHERE usuario_id = ?", id);
        }
        for (var id : cuentas) sql.update("DELETE FROM usuario WHERE id_usuario = ?", id);
        verifyNoInteractions(proveedor);
    }

    @Test void creaInvitacionNormalizadaConReserva48HorasYUnaAuditoria() throws Exception {
        var resultado = llamar(propietario, "POST", "/invitaciones", Map.of(
                "emailInvitado", "  " + invitado.getEmail().toUpperCase(Locale.ROOT) + "  ", "perfil", "Familia"), 201);
        assertThat(resultado.get("estado")).isEqualTo("PENDIENTE");
        assertThat(resultado.get("emailInvitado")).isEqualTo(invitado.getEmail());
        assertThat(resultado.get("propietarioId")).isEqualTo(propietario.getIdUsuario().toString());
        assertThat(resultado.get("perfil")).isEqualTo("Familia");
        assertThat(resultado.get("expiraEn")).isEqualTo(ahora.plus(Duration.ofHours(48)).toString());
        assertThat(eventos()).isEqualTo(1); assertThat(activos()).isZero();
        assertThat(cupo(propietario).get("ocupados")).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"email", "perfilNulo", "perfilInvalido", "json"})
    void solicitudInvalidaDevuelve400SinEscrituras(String caso) throws Exception {
        String cuerpo = switch (caso) {
            case "email" -> "{\"emailInvitado\":\"no-es-email\",\"perfil\":\"Familia\"}";
            case "perfilNulo" -> "{\"emailInvitado\":\"persona@example.test\"}";
            case "perfilInvalido" -> "{\"emailInvitado\":\"persona@example.test\",\"perfil\":\"Administrador\"}";
            default -> "{";
        };
        var r = mvc.perform(post(RAIZ + "/invitaciones").with(user(propietario.getEmail()))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andReturn().getResponse();
        assertThat(r.getStatus()).isEqualTo(400);
        assertThat(json.readValue(r.getContentAsString(), Map.class).get("codigo")).isEqualTo("ERROR_VALIDACION");
        assertThat(eventos()).isZero();
        assertThat(invitaciones.findByPropietario_IdUsuarioOrderByCreadoEnDesc(propietario.getIdUsuario())).isEmpty();
    }

    @Test void rechazaAutoInvitacion() throws Exception {
        assertThat(invitar(propietario, propietario, 409).get("codigo")).isEqualTo("AUTO_INVITACION");
        assertThat(eventos()).isZero();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void destinatarioInexistenteOInactivoNoSeInvita(boolean inactivo) throws Exception {
        String email = "inexistente@example.test";
        if (inactivo) { invitado.setActivo(false); usuarios.saveAndFlush(invitado); email = invitado.getEmail(); }
        llamar(propietario, "POST", "/invitaciones", Map.of("emailInvitado", email, "perfil", "Cuidador"), 404);
        assertThat(eventos()).isZero();
    }

    @Test void tenerSoloUnAccesoCompartidoNoPermiteInvitarSobreMascotasAjenas() throws Exception {
        acceso(propietario, invitado);
        assertThat(invitar(invitado, otro, 409).get("codigo")).isEqualTo("PROPIETARIO_SIN_MASCOTAS");
    }

    @Test void duplicadoPendienteYAccesoActivoSeRechazanSinReservarOtroLugar() throws Exception {
        var i = nueva(propietario, invitado);
        assertThat(invitar(propietario, invitado, 409).get("codigo")).isEqualTo("INVITACION_YA_PENDIENTE");
        aceptar(invitado, i);
        assertThat(invitar(propietario, invitado, 409).get("codigo")).isEqualTo("ACCESO_YA_EXISTENTE");
        assertThat(eventos()).isEqualTo(3); assertThat(activos()).isEqualTo(1);
    }

    @Test void freeReservaTresPendientesYRechazaUnaCuarta() throws Exception {
        nueva(propietario, invitado); nueva(propietario, otro); nueva(propietario, usuario());
        assertThat(invitar(propietario, usuario(), 409).get("codigo")).isEqualTo("LIMITE_INVITADOS_ALCANZADO");
        var cupo = cupo(propietario);
        assertThat(cupo.get("invitacionesPendientes")).isEqualTo(3);
        assertThat(cupo.get("disponibles")).isEqualTo(0);
        assertThat(eventos()).isEqualTo(3);
    }

    @Test void dosActivosMasUnaPendienteOcupanTresYLiberarUnAccesoPermiteOtraInvitacion() throws Exception {
        var a = acceso(propietario, invitado); acceso(propietario, otro); nueva(propietario, usuario());
        invitar(propietario, usuario(), 409);
        llamar(propietario, "DELETE", "/accesos/" + a.getIdAcceso(), null, 200);
        nueva(propietario, usuario());
        assertThat(cupo(propietario).get("ocupados")).isEqualTo(3);
    }

    @Test void premiumManualPermiteMasDeTresYAlPasarAFreeSeRespetaElCupoActual() throws Exception {
        propietario.setEsPremium(true); usuarios.saveAndFlush(propietario);
        var i = nueva(propietario, invitado);
        nueva(propietario, otro); nueva(propietario, usuario()); var cancelar = nueva(propietario, usuario());
        assertThat(cupo(propietario).get("limite")).isNull();
        propietario.setEsPremium(false); usuarios.saveAndFlush(propietario);
        assertThat(llamar(invitado, "POST", invitacion(i, "aceptar"), null, 409).get("codigo")).isEqualTo("LIMITE_INVITADOS_ALCANZADO");
        llamar(propietario, "POST", invitacion(cancelar, "cancelar"), null, 200);
        aceptar(invitado, i);
        assertThat(cupo(propietario).get("ocupados")).isEqualTo(3);
    }

    @Test void planPagadoVigenteYVencimientoExactoUsanPremiumServiceAunqueElIndicadorSeaFalso() throws Exception {
        cobertura(ahora.plusSeconds(60));
        nueva(propietario, invitado); nueva(propietario, otro); nueva(propietario, usuario()); nueva(propietario, usuario());
        assertThat(cupo(propietario).get("esPremium")).isEqualTo(true);
        when(reloj.instant()).thenReturn(ahora.plusSeconds(60));
        assertThat(invitar(propietario, usuario(), 409).get("codigo")).isEqualTo("LIMITE_INVITADOS_ALCANZADO");
        assertThat(cupo(propietario).get("esPremium")).isEqualTo(false);
        assertThat(invitaciones.contarPendientesVigentes(propietario.getIdUsuario(), ahora.plusSeconds(60))).isEqualTo(4);
    }

    @Test void bandejasNoExponenRelacionesAjenasYLosGetNoEscriben() throws Exception {
        var propia = nueva(propietario, invitado); mascota(otro); nueva(otro, usuario());
        long antes = eventos();
        var recibida = llamar(invitado, "GET", "/me?usuarioId=" + otro.getIdUsuario(), null, 200);
        assertThat(lista(recibida, "invitacionesRecibidas")).hasSize(1);
        assertThat(lista(recibida, "invitacionesEnviadas")).isEmpty();
        assertThat(objeto(recibida, "cupo").get("ocupados")).isEqualTo(0);
        assertThat(llamar(propietario, "GET", "/invitaciones/" + propia, null, 200).get("idInvitacion")).isEqualTo(propia.toString());
        assertThat(eventos()).isEqualTo(antes);
    }

    @Test void expiracionEfectivaEnGetNoEscribeYLiberaElLugar() throws Exception {
        var i = nueva(propietario, invitado);
        when(reloj.instant()).thenReturn(ahora.plus(Duration.ofHours(48)));
        assertThat(llamar(invitado, "GET", "/invitaciones/" + i, null, 200).get("estado")).isEqualTo("EXPIRADA");
        assertThat(cupo(propietario).get("ocupados")).isEqualTo(0);
        assertThat(sql.queryForObject("SELECT e.nombre FROM invitacion i JOIN estados e ON e.id_estado=i.estado_id WHERE id_invitacion=?", String.class, i)).isEqualTo("PENDIENTE");
        assertThat(eventos()).isEqualTo(1);
        nueva(propietario, invitado);
    }

    @ParameterizedTest
    @CsvSource({"otro,GET,", "otro,POST,aceptar", "otro,POST,rechazar", "otro,POST,cancelar", "otro,POST,reenviar",
        "propietario,POST,aceptar", "propietario,POST,rechazar", "invitado,POST,cancelar", "invitado,POST,reenviar"})
    void actoresAjenoOEquivocadoNoPuedenOperarLaInvitacion(String quien, String metodo, String accion) throws Exception {
        var i = nueva(propietario, invitado);
        var actor = switch (quien) { case "propietario" -> propietario; case "invitado" -> invitado; default -> otro; };
        llamar(actor, metodo, "/invitaciones/" + i + (accion == null ? "" : "/" + accion), null, 403);
        assertThat(eventos()).isEqualTo(1); assertThat(activos()).isZero();
    }

    @Test void aceptarCreaUnSoloAccesoConPerfilYAuditaAlActorReal() throws Exception {
        var i = nueva(propietario, invitado);
        var aceptada = aceptar(invitado, i);
        var idAcceso = UUID.fromString((String) objeto(aceptada, "acceso").get("idAcceso"));
        assertThat(objeto(aceptada, "acceso").get("perfil")).isEqualTo("Familia");
        assertThat(sql.queryForObject("SELECT acceso_id FROM invitacion WHERE id_invitacion=?", UUID.class, i)).isEqualTo(idAcceso);
        assertThat(eventosActor(propietario)).isEqualTo(1);
        assertThat(eventosActor(invitado)).isEqualTo(2);
        assertThat(lista(llamar(invitado, "GET", "/me", null, 200), "cuidadosRecibidos")).hasSize(1);
        assertThat(cupo(invitado).get("ocupados")).isEqualTo(0);
        var repetida = aceptar(invitado, i);
        assertThat(objeto(repetida, "acceso").get("idAcceso")).isEqualTo(idAcceso.toString());
        assertThat(eventos()).isEqualTo(3); assertThat(activos()).isEqualTo(1);
    }

    @Test void aceptacionRepetidaTrasRevocarYReinvitarNoReanimaElVinculoAnterior() throws Exception {
        var primera = nueva(propietario, invitado);
        var accesoViejo = (String) objeto(aceptar(invitado, primera), "acceso").get("idAcceso");
        llamar(propietario, "DELETE", "/accesos/" + accesoViejo, null, 200);
        var segunda = nueva(propietario, invitado);
        var accesoNuevo = (String) objeto(aceptar(invitado, segunda), "acceso").get("idAcceso");
        assertThat(accesoNuevo).isNotEqualTo(accesoViejo);
        long antes = eventos();
        var repetida = objeto(aceptar(invitado, primera), "acceso");
        assertThat(repetida.get("idAcceso")).isEqualTo(accesoViejo);
        assertThat(repetida.get("activo")).isEqualTo(false);
        assertThat(activos()).isEqualTo(1); assertThat(eventos()).isEqualTo(antes);
    }

    @ParameterizedTest @CsvSource({"rechazar,invitado,RECHAZADA", "cancelar,propietario,CANCELADA"})
    void cerrarInvitacionEsIdempotenteYLiberaLaReserva(String accion, String quien, String estado) throws Exception {
        var i = nueva(propietario, invitado); var actor = "invitado".equals(quien) ? invitado : propietario;
        llamar(actor, "POST", invitacion(i, accion), null, 200);
        assertThat(llamar(actor, "POST", invitacion(i, accion), null, 200).get("estado")).isEqualTo(estado);
        assertThat(cupo(propietario).get("ocupados")).isEqualTo(0);
        assertThat(eventos()).isEqualTo(2); assertThat(activos()).isZero();
        llamar(invitado, "POST", invitacion(i, "aceptar"), null, 409);
    }

    @ParameterizedTest @ValueSource(strings = {"aceptar", "rechazar", "cancelar"})
    void enElInstanteExactoDeExpiracionNoPuedeResolverse(String accion) throws Exception {
        var i = nueva(propietario, invitado);
        when(reloj.instant()).thenReturn(ahora.plus(Duration.ofHours(48)));
        var actor = "cancelar".equals(accion) ? propietario : invitado;
        assertThat(llamar(actor, "POST", invitacion(i, accion), null, 410).get("codigo")).isEqualTo("INVITACION_EXPIRADA");
        assertThat(eventos()).isEqualTo(1); assertThat(activos()).isZero();
    }

    @Test void aceptarLaTerceraReservaNoNecesitaUnCuartoLugar() throws Exception {
        acceso(propietario, otro); acceso(propietario, usuario()); var i = nueva(propietario, invitado);
        aceptar(invitado, i);
        assertThat(activos()).isEqualTo(3); assertThat(cupo(propietario).get("invitacionesPendientes")).isEqualTo(0);
    }

    @Test void reenviarPendienteCambiaElIdYElAnteriorNoPuedeAceptarse() throws Exception {
        var anterior = nueva(propietario, invitado);
        var nueva = llamar(propietario, "POST", invitacion(anterior, "reenviar"), null, 201);
        var nuevaId = UUID.fromString((String) nueva.get("idInvitacion"));
        assertThat(nuevaId).isNotEqualTo(anterior);
        assertThat(llamar(invitado, "GET", "/invitaciones/" + anterior, null, 200).get("estado")).isEqualTo("CANCELADA");
        llamar(invitado, "POST", invitacion(anterior, "aceptar"), null, 409);
        assertThat(cupo(propietario).get("ocupados")).isEqualTo(1);
        aceptar(invitado, nuevaId); assertThat(activos()).isEqualTo(1);
        assertThat(eventos()).isEqualTo(5);
    }

    @ParameterizedTest @ValueSource(strings = {"EXPIRADA", "RECHAZADA", "CANCELADA"})
    void reenviarUnaInvitacionCerradaGeneraOtraCon48Horas(String estado) throws Exception {
        var i = nueva(propietario, invitado);
        Instant nuevoInicio = ahora;
        if ("EXPIRADA".equals(estado)) { nuevoInicio = ahora.plus(Duration.ofHours(48)); when(reloj.instant()).thenReturn(nuevoInicio); }
        else llamar("RECHAZADA".equals(estado) ? invitado : propietario, "POST", invitacion(i,
                "RECHAZADA".equals(estado) ? "rechazar" : "cancelar"), null, 200);
        var resultado = llamar(propietario, "POST", invitacion(i, "reenviar"), null, 201);
        assertThat(resultado.get("idInvitacion")).isNotEqualTo(i.toString());
        assertThat(resultado.get("expiraEn")).isEqualTo(nuevoInicio.plus(Duration.ofHours(48)).toString());
    }

    @Test void reenvioQueNoPuedeReservarConservaLaInvitacionAnteriorYNoAudita() throws Exception {
        propietario.setEsPremium(true); usuarios.saveAndFlush(propietario);
        var i = nueva(propietario, invitado); nueva(propietario, otro); nueva(propietario, usuario()); nueva(propietario, usuario());
        propietario.setEsPremium(false); usuarios.saveAndFlush(propietario);
        long antes = eventos();
        llamar(propietario, "POST", invitacion(i, "reenviar"), null, 409);
        assertThat(llamar(invitado, "GET", "/invitaciones/" + i, null, 200).get("estado")).isEqualTo("PENDIENTE");
        assertThat(eventos()).isEqualTo(antes);
        assertThat(invitaciones.contarPendientesVigentes(propietario.getIdUsuario(), ahora)).isEqualTo(4);
    }

    @Test void aceptacionHistoricaSinVinculoExigeRevisionSinConcederAcceso() throws Exception {
        var i = nueva(propietario, invitado);
        sql.update("UPDATE invitacion SET estado_id=(SELECT id_estado FROM estados WHERE entidad='INVITACION' AND nombre='ACEPTADA') WHERE id_invitacion=?", i);
        assertThat(llamar(invitado, "POST", invitacion(i, "aceptar"), null, 409).get("codigo")).isEqualTo("INVITACION_REQUIERE_REVISION");
        assertThat(activos()).isZero(); assertThat(eventos()).isEqualTo(1);
    }

    @Test void perfilCambiaInmediatamenteSinAuditarRepeticiones() throws Exception {
        var id = (String) objeto(aceptar(invitado, nueva(propietario, invitado)), "acceso").get("idAcceso");
        llamar(propietario, "PATCH", "/accesos/" + id + "/perfil", Map.of("perfil", "Cuidador"), 200);
        assertThat(llamar(invitado, "GET", "/accesos/" + id, null, 200).get("perfil")).isEqualTo("Cuidador");
        long antes = eventos();
        llamar(propietario, "PATCH", "/accesos/" + id + "/perfil", Map.of("perfil", "Cuidador"), 200);
        assertThat(eventos()).isEqualTo(antes);
        llamar(invitado, "PATCH", "/accesos/" + id + "/perfil", Map.of("perfil", "Familia"), 403);
        llamar(otro, "PATCH", "/accesos/" + id + "/perfil", Map.of("perfil", "Familia"), 403);
        llamar(propietario, "DELETE", "/accesos/" + id, null, 200);
        llamar(propietario, "PATCH", "/accesos/" + id + "/perfil", Map.of("perfil", "Familia"), 409);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void revocarOSalirDesactivaSoloEseVinculoYEsIdempotente(boolean salir) throws Exception {
        var id = (String) objeto(aceptar(invitado, nueva(propietario, invitado)), "acceso").get("idAcceso");
        var actor = salir ? invitado : propietario; var metodo = salir ? "POST" : "DELETE";
        var ruta = "/accesos/" + id + (salir ? "/salir" : "");
        llamar(salir ? propietario : invitado, metodo, ruta, null, 403);
        llamar(otro, "GET", "/accesos/" + id, null, 403);
        assertThat(llamar(actor, metodo, ruta, null, 200).get("activo")).isEqualTo(false);
        long antes = eventos(); llamar(actor, metodo, ruta, null, 200);
        assertThat(eventos()).isEqualTo(antes);
        assertThat(activos()).isZero(); assertThat(cupo(propietario).get("ocupados")).isEqualTo(0);
        assertThat(lista(llamar(invitado, "GET", "/me", null, 200), "cuidadosRecibidos")).isEmpty();
        assertThat(sql.queryForObject("SELECT valor_nuevo FROM evento_auditoria_pendiente WHERE id_entidad=? AND tipo_accion='UNSHARE'", String.class, UUID.fromString(id)))
                .contains(salir ? "SALIDA_INVITADO" : "REVOCACION_PROPIETARIO");
    }

    @Test void falloDeAuditoriaRevierteCreacionYReserva() throws Exception {
        doThrow(new IllegalStateException("Fallo QA")).when(destinoAuditoria()).encolar(any(), any(), any(), any(), any(), any());
        invitar(propietario, invitado, 500);
        assertThat(invitaciones.contarPendientesVigentes(propietario.getIdUsuario(), ahora)).isZero();
        assertThat(eventos()).isZero();
    }

    @Test void falloDelSegundoEventoRevierteAceptacionAccesoYAmbasAuditorias() throws Exception {
        var i = nueva(propietario, invitado);
        doAnswer(invocacion -> {
            Object resultado = invocacion.callRealMethod();
            if ("invitacion".equals(invocacion.getArgument(1))) throw new IllegalStateException("Fallo QA despues del segundo evento");
            return resultado;
        }).when(destinoAuditoria()).encolar(any(), any(), any(), any(), any(), any());
        llamar(invitado, "POST", invitacion(i, "aceptar"), null, 500);
        assertThat(activos()).isZero(); assertThat(eventos()).isEqualTo(1);
        assertThat(sql.queryForObject("SELECT acceso_id FROM invitacion WHERE id_invitacion=?", UUID.class, i)).isNull();
        assertThat(cupo(propietario).get("invitacionesPendientes")).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings = {"perfil", "revocar", "salir"})
    void falloDeAuditoriaRevierteLaGestionDelVinculo(String accion) throws Exception {
        var id = (String) objeto(aceptar(invitado, nueva(propietario, invitado)), "acceso").get("idAcceso");
        long antes = eventos();
        doThrow(new IllegalStateException("Fallo QA")).when(destinoAuditoria()).encolar(any(), any(), any(), any(), any(), any());
        if ("perfil".equals(accion)) llamar(propietario, "PATCH", "/accesos/" + id + "/perfil", Map.of("perfil", "Cuidador"), 500);
        else llamar("salir".equals(accion) ? invitado : propietario, "salir".equals(accion) ? "POST" : "DELETE",
                "/accesos/" + id + ("salir".equals(accion) ? "/salir" : ""), null, 500);
        var despues = llamar(invitado, "GET", "/accesos/" + id, null, 200);
        assertThat(despues.get("activo")).isEqualTo(true); assertThat(despues.get("perfil")).isEqualTo("Familia");
        assertThat(eventos()).isEqualTo(antes);
    }

    @Test void dosAltasSimultaneasSoloConsumenElUltimoLugarDisponible() throws Exception {
        acceso(propietario, usuario()); acceso(propietario, usuario());
        var resultados = simultaneas(() -> enviar(propietario, invitado), () -> enviar(propietario, otro));
        assertThat(resultados).containsExactlyInAnyOrder(201, 409);
        assertThat(cupo(propietario).get("ocupados")).isEqualTo(3); assertThat(eventos()).isEqualTo(1);
    }

    @Test void dosInvitacionesConcurrentesAlMismoEmailNoDuplicanReserva() throws Exception {
        assertThat(simultaneas(() -> enviar(propietario, invitado), () -> enviar(propietario, invitado)))
                .containsExactlyInAnyOrder(201, 409);
        assertThat(cupo(propietario).get("ocupados")).isEqualTo(1); assertThat(eventos()).isEqualTo(1);
    }

    @Test void aceptarDosVecesEnParaleloConservaElMismoAccesoYDosEventosDeAceptacion() throws Exception {
        var i = nueva(propietario, invitado);
        var resultados = simultaneas(() -> aceptar(invitado, i), () -> aceptar(invitado, i));
        assertThat(objeto(resultados.get(0), "acceso").get("idAcceso"))
                .isEqualTo(objeto(resultados.get(1), "acceso").get("idAcceso"));
        assertThat(activos()).isEqualTo(1); assertThat(eventos()).isEqualTo(3);
    }

    @Test void aceptarConcurrenteConCancelarNoConcedeUnAccesoCancelado() throws Exception {
        var i = nueva(propietario, invitado);
        var resultados = simultaneas(() -> estadoHttp(invitado, "POST", invitacion(i, "aceptar"), null),
                () -> estadoHttp(propietario, "POST", invitacion(i, "cancelar"), null));
        assertThat(resultados).containsExactlyInAnyOrder(200, 409);
        String estado = (String) llamar(propietario, "GET", "/invitaciones/" + i, null, 200).get("estado");
        assertThat(activos()).isEqualTo("ACEPTADA".equals(estado) ? 1 : 0);
    }

    @Test void aceptacionesEntreDosPropietariosNoSeBloqueanMutuamente() throws Exception {
        mascota(invitado);
        var ida = nueva(propietario, invitado); var vuelta = nueva(invitado, propietario);
        simultaneas(() -> aceptar(invitado, ida), () -> aceptar(propietario, vuelta));
        assertThat(activos()).isEqualTo(1);
        assertThat(accesos.countByPropietario_IdUsuarioAndActivoTrue(invitado.getIdUsuario())).isEqualTo(1);
    }

    @Test void revocarConcurrenteConRepetirAceptacionNoReactivaElAcceso() throws Exception {
        var i = nueva(propietario, invitado);
        var id = (String) objeto(aceptar(invitado, i), "acceso").get("idAcceso");
        simultaneas(() -> aceptar(invitado, i), () -> llamar(propietario, "DELETE", "/accesos/" + id, null, 200));
        assertThat(activos()).isZero(); assertThat(eventos()).isEqualTo(4);
    }

    @Test void rutasProtegidasRechazanSesionAusentePreautenticacionCuentaInactivaEIdsInvalidos() throws Exception {
        llamar(null, "GET", "/me", null, 401);
        var preAuth = jwt.generarTokenPreAuth(propietario);
        assertThat(mvc.perform(get(RAIZ + "/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + preAuth)).andReturn().getResponse().getStatus()).isEqualTo(401);
        llamar(propietario, "GET", "/invitaciones/no-es-uuid", null, 400);
        llamar(propietario, "GET", "/invitaciones/" + UUID.randomUUID(), null, 404);
        llamar(propietario, "GET", "/accesos/" + UUID.randomUUID(), null, 404);
        invitado.setActivo(false); usuarios.saveAndFlush(invitado);
        llamar(invitado, "GET", "/me", null, 401);
    }

    @Test void tokenRealObtieneElActorDeLaSesionIgnorandoUnPropietarioDeConsultaAjeno() throws Exception {
        String token = jwt.generarToken(propietario);
        var r = mvc.perform(post(RAIZ + "/invitaciones").param("propietarioId", otro.getIdUsuario().toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(solicitud(invitado)))).andReturn().getResponse();
        assertThat(r.getStatus()).isEqualTo(201);
        assertThat(json.readValue(r.getContentAsString(), Map.class).get("propietarioId")).isEqualTo(propietario.getIdUsuario().toString());
    }

    private Usuario usuario() {
        var u = new Usuario(); u.setNombre("Cuidado HTTP QA");
        u.setEmail("cuidado-http-" + UUID.randomUUID() + "@example.test"); u.setPasswordHash("hash-ficticio-no-utilizable");
        u = usuarios.saveAndFlush(u); cuentas.add(u.getIdUsuario()); return u;
    }

    private void mascota(Usuario u) {
        var m = new Mascota(); m.setPropietario(u); m.setNombre("Mascota de prueba"); m.setEspecie("Perro");
        m.setFechaNacimiento(LocalDate.of(2020, 1, 1)); mascotas.saveAndFlush(m);
    }

    private AccesoCompartido acceso(Usuario propietario, Usuario invitado) {
        return accesos.saveAndFlush(AccesoCompartido.builder().propietario(propietario).invitado(invitado)
                .perfil(PerfilCompartido.Cuidador).fechaIngreso(ahora).build());
    }

    private void cobertura(Instant fin) {
        var s = new Suscripcion(); s.setUsuario(propietario); s.setTipoPlan("PREMIUM_MENSUAL"); s.setFechaVencimiento(fin);
        s.setEstadoCatalogo(estados.findByEntidadAndNombre("SUSCRIPCION", "CANCELADA").orElseThrow()); s = suscripciones.saveAndFlush(s);
        var p = new Pago(); p.setSuscripcion(s); p.setMonto(new BigDecimal("4000")); p.setFechaPago(ahora);
        p.setEstadoCatalogo(estados.findByEntidadAndNombre("PAGO", "APROBADO").orElseThrow()); pagos.saveAndFlush(p);
    }

    private Map<String, String> solicitud(Usuario destino) { return Map.of("emailInvitado", destino.getEmail(), "perfil", "Familia"); }
    private UUID nueva(Usuario actor, Usuario destino) throws Exception { return UUID.fromString((String) invitar(actor, destino, 201).get("idInvitacion")); }
    private Map<String, Object> invitar(Usuario actor, Usuario destino, int esperado) throws Exception { return llamar(actor, "POST", "/invitaciones", solicitud(destino), esperado); }
    private int enviar(Usuario actor, Usuario destino) throws Exception { return estadoHttp(actor, "POST", "/invitaciones", solicitud(destino)); }
    private String invitacion(UUID id, String accion) { return "/invitaciones/" + id + "/" + accion; }
    private Map<String, Object> aceptar(Usuario actor, UUID id) throws Exception { return llamar(actor, "POST", invitacion(id, "aceptar"), null, 200); }
    private Map<String, Object> cupo(Usuario actor) throws Exception { return objeto(llamar(actor, "GET", "/me", null, 200), "cupo"); }
    private long activos() { return accesos.countByPropietario_IdUsuarioAndActivoTrue(propietario.getIdUsuario()); }
    private long eventosActor(Usuario u) { return sql.queryForObject("SELECT count(*) FROM evento_auditoria_pendiente WHERE actor_id=?", Long.class, u.getIdUsuario()); }
    private long eventos() { return cuentas.stream().mapToLong(id -> sql.queryForObject("SELECT count(*) FROM evento_auditoria_pendiente WHERE actor_id=?", Long.class, id)).sum(); }
    private EventoAuditoriaPendienteService destinoAuditoria() { return AopTestUtils.getUltimateTargetObject(auditoria); }

    private MockHttpServletRequestBuilder peticion(Usuario actor, String metodo, String ruta, Object cuerpo) throws Exception {
        var peticion = request(HttpMethod.valueOf(metodo), RAIZ + ruta);
        if (actor != null) peticion.with(user(actor.getEmail()));
        if (cuerpo != null) peticion.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo));
        return peticion;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> llamar(Usuario actor, String metodo, String ruta, Object cuerpo, int esperado) throws Exception {
        var r = mvc.perform(peticion(actor, metodo, ruta, cuerpo)).andReturn().getResponse();
        assertThat(r.getStatus()).as(metodo + " " + ruta + ": " + r.getContentAsString()).isEqualTo(esperado);
        return json.readValue(r.getContentAsString(), Map.class);
    }

    private int estadoHttp(Usuario actor, String metodo, String ruta, Object cuerpo) throws Exception {
        return mvc.perform(peticion(actor, metodo, ruta, cuerpo)).andReturn().getResponse().getStatus();
    }

    @SuppressWarnings("unchecked") private Map<String, Object> objeto(Map<String, Object> datos, String campo) { return (Map<String, Object>) datos.get(campo); }
    @SuppressWarnings("unchecked") private List<Object> lista(Map<String, Object> datos, String campo) { return (List<Object>) datos.get(campo); }

    private <T> List<T> simultaneas(Callable<T> primera, Callable<T> segunda) throws Exception {
        try (var hilos = Executors.newFixedThreadPool(2)) {
            var inicio = new CountDownLatch(1);
            var a = hilos.submit(() -> { inicio.await(5, TimeUnit.SECONDS); return primera.call(); });
            var b = hilos.submit(() -> { inicio.await(5, TimeUnit.SECONDS); return segunda.call(); });
            inicio.countDown();
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        }
    }
}
