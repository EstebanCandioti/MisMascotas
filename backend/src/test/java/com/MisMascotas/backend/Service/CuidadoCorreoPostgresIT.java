package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import com.MisMascotas.backend.DTO.CuidadoCompartidoDTO.NuevaInvitacion;
import com.MisMascotas.backend.Entity.*;
import com.MisMascotas.backend.Repository.*;
import com.MisMascotas.backend.Integration.MercadoPagoGateway;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Listener y servicio de email reales; únicamente el transporte SMTP se sustituye. */
@SpringBootTest(properties = "cuidado-compartido.url-invitaciones=https://web.example.test/cuidado-compartido")
@AutoConfigureMockMvc
@ActiveProfiles("ci")
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_CUIDADO_IT", matches = "true")
class CuidadoCorreoPostgresIT {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired MascotaRepository mascotas;
    @Autowired JdbcTemplate sql;
    @Autowired CuidadoCompartidoService cuidado;
    @Autowired PlatformTransactionManager transacciones;
    @MockitoBean JavaMailSender smtp;
    @MockitoBean Clock reloj;
    @MockitoBean MercadoPagoGateway proveedor;
    final JsonMapper json = JsonMapper.builder().build();
    final List<UUID> cuentas = new ArrayList<>();
    final BlockingQueue<Entrega> entregas = new LinkedBlockingQueue<>();
    final Instant ahora = Instant.parse("2030-01-01T12:00:00Z");
    Usuario propietario, invitado;

    @BeforeEach void preparar() {
        when(reloj.instant()).thenReturn(ahora);
        propietario = usuario(); invitado = usuario();
        var m = new Mascota(); m.setPropietario(propietario); m.setNombre("Mascota correo QA"); m.setEspecie("Perro");
        m.setFechaNacimiento(LocalDate.of(2020, 1, 1)); mascotas.saveAndFlush(m);
        doAnswer(invocacion -> { registrar(invocacion.getArgument(0)); return null; }).when(smtp).send(any(SimpleMailMessage.class));
    }

    @AfterEach void limpiar() {
        for (var id : cuentas) {
            sql.update("DELETE FROM evento_auditoria_pendiente WHERE actor_id=?", id);
            sql.update("DELETE FROM log_auditoria WHERE actor_id=?", id);
            sql.update("DELETE FROM invitacion WHERE propietario_id=?", id);
        }
        for (var id : cuentas) {
            sql.update("DELETE FROM acceso_compartido WHERE propietario_id=? OR invitado_id=?", id, id);
            sql.update("DELETE FROM mascota WHERE propietario_id=?", id);
        }
        for (var id : cuentas) sql.update("DELETE FROM usuario WHERE id_usuario=?", id);
        verifyNoInteractions(proveedor);
    }

    @Test void invitacionSeEnviaFueraDeTransaccionConDatosYaConfirmadosYEnlaceWeb() throws Exception {
        String id = crear(); var entrega = recibir();
        assertThat(entrega.transaccionActiva()).isFalse();
        assertThat(entrega.filasVisibles()).isEqualTo(1);
        assertThat(entrega.mensaje().getTo()).containsExactly(invitado.getEmail());
        assertThat(entrega.mensaje().getText()).contains("perfil Familia", "2030-01-03T12:00:00Z",
                "https://web.example.test/cuidado-compartido?invitacionId=" + id,
                "aceptar o rechazar desde la web", "Abrir este enlace no concede acceso");
        assertThat(sql.queryForObject("SELECT count(*) FROM acceso_compartido WHERE propietario_id=?", Long.class, propietario.getIdUsuario())).isZero();
    }

    @Test void aceptarNotificaSoloAlPropietarioUnaVezSinDuplicarAcceso() throws Exception {
        String id = crear(); recibir();
        llamar(invitado, "POST", "/invitaciones/" + id + "/aceptar", null, 200);
        var mensaje = recibir().mensaje();
        assertThat(mensaje.getTo()).containsExactly(propietario.getEmail());
        assertThat(mensaje.getText()).contains("acepto tu invitacion", "invitacionId=" + id);
        llamar(invitado, "POST", "/invitaciones/" + id + "/aceptar", null, 200);
        verify(smtp, after(200).times(2)).send(any(SimpleMailMessage.class));
        assertThat(sql.queryForObject("SELECT count(*) FROM acceso_compartido WHERE propietario_id=?", Long.class, propietario.getIdUsuario())).isEqualTo(1);
    }

    @Test void rechazarNotificaSoloAlPropietarioUnaVezYLiberaCupo() throws Exception {
        String id = crear(); recibir();
        llamar(invitado, "POST", "/invitaciones/" + id + "/rechazar", null, 200);
        var mensaje = recibir().mensaje();
        assertThat(mensaje.getTo()).containsExactly(propietario.getEmail());
        assertThat(mensaje.getText()).contains("rechazo tu invitacion");
        llamar(invitado, "POST", "/invitaciones/" + id + "/rechazar", null, 200);
        verify(smtp, after(200).times(2)).send(any(SimpleMailMessage.class));
        assertThat(llamar(propietario, "GET", "/me", null, 200).get("cupo").get("ocupados").asInt()).isZero();
    }

    @Test void reenviarGeneraNuevoEnlaceYUnaSolaReserva() throws Exception {
        String id = crear(); recibir();
        String nuevo = llamar(propietario, "POST", "/invitaciones/" + id + "/reenviar", null, 201).get("idInvitacion").asText();
        assertThat(nuevo).isNotEqualTo(id);
        assertThat(recibir().mensaje().getText()).contains("invitacionId=" + nuevo).doesNotContain("invitacionId=" + id);
        assertThat(llamar(propietario, "GET", "/me", null, 200).get("cupo").get("ocupados").asInt()).isEqualTo(1);
    }

    @Test void abrirConsultarYCancelarNoEnviaRespuestaNiOtorgaAcceso() throws Exception {
        String id = crear(); recibir();
        llamar(invitado, "GET", "/invitaciones/" + id, null, 200);
        llamar(invitado, "GET", "/invitaciones/" + id + "/aceptar", null, 405);
        llamar(propietario, "POST", "/invitaciones/" + id + "/cancelar", null, 200);
        verify(smtp, after(200).times(1)).send(any(SimpleMailMessage.class));
        assertThat(sql.queryForObject("SELECT count(*) FROM acceso_compartido WHERE propietario_id=?", Long.class, propietario.getIdUsuario())).isZero();
    }

    @Test void rollbackPosteriorAPublicarElEventoNoEnviaEmailNiReservaCupo() {
        assertThatThrownBy(() -> new TransactionTemplate(transacciones).execute(status -> {
            cuidado.crear(propietario.getIdUsuario(), new NuevaInvitacion(invitado.getEmail(), PerfilCompartido.Familia));
            throw new IllegalStateException("Rollback intencional de QA despues de publicar");
        })).isInstanceOf(IllegalStateException.class);
        verify(smtp, after(200).never()).send(any(SimpleMailMessage.class));
        assertThat(sql.queryForObject("SELECT count(*) FROM invitacion WHERE propietario_id=?", Long.class, propietario.getIdUsuario())).isZero();
    }

    @Test void falloSmtpConservaInvitacionBandejaYCupoSinReintentoAutomatico() throws Exception {
        fallarSmtp(); String id = crear(); recibir();
        assertThat(llamar(invitado, "GET", "/invitaciones/" + id, null, 200).get("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(llamar(invitado, "GET", "/me", null, 200).get("invitacionesRecibidas").size()).isEqualTo(1);
        assertThat(llamar(propietario, "GET", "/me", null, 200).get("cupo").get("ocupados").asInt()).isEqualTo(1);
        verify(smtp, after(200).times(1)).send(any(SimpleMailMessage.class));
    }

    @Test void falloDelEmailDeRespuestaNoRevierteLaAceptacionNiLaRepite() throws Exception {
        String id = crear(); recibir(); fallarSmtp();
        var primera = llamar(invitado, "POST", "/invitaciones/" + id + "/aceptar", null, 200); recibir();
        var repetida = llamar(invitado, "POST", "/invitaciones/" + id + "/aceptar", null, 200);
        assertThat(repetida.get("acceso").get("idAcceso").asText()).isEqualTo(primera.get("acceso").get("idAcceso").asText());
        verify(smtp, after(200).times(2)).send(any(SimpleMailMessage.class));
    }

    private void fallarSmtp() { doAnswer(invocacion -> { registrar(invocacion.getArgument(0)); throw new MailSendException("SMTP ficticio no disponible"); }).when(smtp).send(any(SimpleMailMessage.class)); }
    private void registrar(SimpleMailMessage mensaje) {
        var coincidencia = Pattern.compile("invitacionId=([a-f0-9-]{36})").matcher(mensaje.getText());
        long visibles = coincidencia.find() ? sql.queryForObject("SELECT count(*) FROM invitacion WHERE id_invitacion=?", Long.class, UUID.fromString(coincidencia.group(1))) : 0;
        entregas.add(new Entrega(new SimpleMailMessage(mensaje), TransactionSynchronizationManager.isActualTransactionActive(), visibles));
    }
    private Entrega recibir() throws InterruptedException { var entrega = entregas.poll(5, TimeUnit.SECONDS); assertThat(entrega).as("Correo posterior al commit").isNotNull(); return entrega; }
    private record Entrega(SimpleMailMessage mensaje, boolean transaccionActiva, long filasVisibles) {}
    private Usuario usuario() { var u = new Usuario(); u.setNombre("Correo QA"); u.setEmail(UUID.randomUUID() + "@example.test"); u.setPasswordHash("hash-ficticio"); u = usuarios.saveAndFlush(u); cuentas.add(u.getIdUsuario()); return u; }
    private String crear() throws Exception { return llamar(propietario, "POST", "/invitaciones", Map.of("emailInvitado", invitado.getEmail(), "perfil", "Familia"), 201).get("idInvitacion").asText(); }
    private JsonNode llamar(Usuario actor, String metodo, String ruta, Object cuerpo, int esperado) throws Exception {
        var peticion = request(HttpMethod.valueOf(metodo), "/cuidado-compartido" + ruta).with(user(actor.getEmail()));
        if (cuerpo != null) peticion.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo));
        var r = mvc.perform(peticion).andReturn().getResponse();
        assertThat(r.getStatus()).as(metodo + " " + ruta + ": " + r.getContentAsString()).isEqualTo(esperado);
        return json.readTree(r.getContentAsString());
    }
}
