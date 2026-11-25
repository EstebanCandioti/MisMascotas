package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import com.MisMascotas.backend.Entity.*;
import com.MisMascotas.backend.Repository.*;
import com.MisMascotas.backend.Util.NormalizadorEmail;

/** Persistencia real de C02; no expone todavía el flujo de invitaciones. */
@SpringBootTest(properties = "spring.datasource.hikari.connection-init-sql=SET lock_timeout = '5s'")
@ActiveProfiles("ci")
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_CUIDADO_IT", matches = "true")
class CuidadoCompartidoBasePostgresIT {
    @Autowired InvitacionRepository invitaciones;
    @Autowired AccesoCompartidoRepository accesos;
    @Autowired UsuarioRepository usuarios;
    @Autowired EstadoRepository estados;
    @Autowired JdbcTemplate sql;
    final Instant ahora = Instant.parse("2030-01-01T12:00:00Z");
    final List<UUID> cuentas = new ArrayList<>();
    Usuario propietario, invitado, otro;

    @BeforeEach void preparar() {
        propietario = usuario(); invitado = usuario(); otro = usuario();
    }

    @AfterEach void limpiar() {
        for (var id : cuentas) {
            sql.update("DELETE FROM invitacion WHERE propietario_id = ?", id);
            sql.update("DELETE FROM acceso_compartido WHERE propietario_id = ? OR invitado_id = ?", id, id);
            sql.update("DELETE FROM usuario WHERE id_usuario = ?", id);
        }
    }

    @Test void invitacionNormalizaEmailYPersistenPerfilPlazoYEstado() {
        var invitacion = nuevaInvitacion(propietario, "PENDIENTE", ahora.plusSeconds(48 * 3600));
        invitacion.setEmailInvitado("  " + invitado.getEmail().toUpperCase(Locale.ROOT) + "  ");
        invitacion.setPerfil(PerfilCompartido.Familia);
        invitacion = invitaciones.saveAndFlush(invitacion);
        var guardada = invitaciones.findById(invitacion.getIdInvitacion()).orElseThrow();
        assertThat(guardada.getEmailInvitado()).isEqualTo(invitado.getEmail());
        assertThat(guardada.getPerfil()).isEqualTo(PerfilCompartido.Familia);
        assertThat(guardada.getExpiraEn()).isEqualTo(ahora.plusSeconds(48 * 3600));
        var emailNormalizado = NormalizadorEmail.normalizar(invitado.getEmail());
        assertThat(invitaciones.findByEmailInvitadoOrderByCreadoEnDesc(emailNormalizado))
                .extracting(Invitacion::getIdInvitacion).containsExactly(guardada.getIdInvitacion());
    }

    @Test void cupoSoloCuentaPendientesVigentesDelPropietarioYExcluyeElInstanteExacto() {
        var vigente = invitaciones.saveAndFlush(nuevaInvitacion(propietario, "PENDIENTE", ahora.plusSeconds(1)));
        invitaciones.saveAndFlush(nuevaInvitacion(propietario, "PENDIENTE", ahora));
        invitaciones.saveAndFlush(nuevaInvitacion(propietario, "PENDIENTE", ahora.minusSeconds(1)));
        for (var estado : List.of("ACEPTADA", "RECHAZADA", "CANCELADA", "EXPIRADA"))
            invitaciones.saveAndFlush(nuevaInvitacion(propietario, estado, ahora.plusSeconds(3600)));
        invitaciones.saveAndFlush(nuevaInvitacion(otro, "PENDIENTE", ahora.plusSeconds(3600)));
        assertThat(invitaciones.contarPendientesVigentes(propietario.getIdUsuario(), ahora)).isEqualTo(1);
        assertThat(invitaciones.existePendienteVigente(propietario.getIdUsuario(), invitado.getEmail(), ahora)).isTrue();
        assertThat(invitaciones.existePendienteVigente(propietario.getIdUsuario(), otro.getEmail(), ahora)).isFalse();
        assertThat(invitaciones.contarPendientesVigentes(propietario.getIdUsuario(), vigente.getExpiraEn())).isZero();
        assertThat(invitaciones.existePendienteVigente(propietario.getIdUsuario(), invitado.getEmail(), vigente.getExpiraEn())).isFalse();
    }

    @Test void consultasDeBandejaYPorIdDistinguenPropietarioYDestinatario() {
        var propia = invitaciones.saveAndFlush(nuevaInvitacion(propietario, "PENDIENTE", ahora.plusSeconds(1)));
        var ajena = nuevaInvitacion(otro, "PENDIENTE", ahora.plusSeconds(1));
        ajena.setEmailInvitado(propietario.getEmail()); invitaciones.saveAndFlush(ajena);
        assertThat(invitaciones.findByPropietario_IdUsuarioOrderByCreadoEnDesc(propietario.getIdUsuario()))
                .extracting(Invitacion::getIdInvitacion).containsExactly(propia.getIdInvitacion());
        assertThat(invitaciones.findByEmailInvitadoOrderByCreadoEnDesc(invitado.getEmail()))
                .extracting(Invitacion::getIdInvitacion).containsExactly(propia.getIdInvitacion());
        assertThat(invitaciones.findByIdInvitacionAndPropietario_IdUsuario(propia.getIdInvitacion(), otro.getIdUsuario())).isEmpty();
        assertThat(invitaciones.findByIdInvitacionAndEmailInvitado(propia.getIdInvitacion(), otro.getEmail())).isEmpty();
    }

    @Test void accesosActivosConservanPerfilYAislanAmbosLadosDelVinculo() {
        var activo = nuevoAcceso(propietario, invitado, true); activo.setPerfil(PerfilCompartido.Familia);
        activo = accesos.saveAndFlush(activo);
        accesos.saveAndFlush(nuevoAcceso(propietario, otro, false));
        accesos.saveAndFlush(nuevoAcceso(otro, invitado, true));
        assertThat(accesos.countByPropietario_IdUsuarioAndActivoTrue(propietario.getIdUsuario())).isEqualTo(1);
        assertThat(accesos.findByPropietario_IdUsuarioAndActivoTrueOrderByFechaIngresoDesc(propietario.getIdUsuario()))
                .extracting(AccesoCompartido::getIdAcceso).containsExactly(activo.getIdAcceso());
        assertThat(accesos.findByInvitado_IdUsuarioAndActivoTrueOrderByFechaIngresoDesc(invitado.getIdUsuario())).hasSize(2);
        assertThat(accesos.findByPropietario_IdUsuarioAndInvitado_IdUsuarioAndActivoTrue(propietario.getIdUsuario(), invitado.getIdUsuario()))
                .get().extracting(AccesoCompartido::getPerfil).isEqualTo(PerfilCompartido.Familia);
        assertThat(accesos.findByIdAccesoAndPropietario_IdUsuario(activo.getIdAcceso(), otro.getIdUsuario())).isEmpty();
        assertThat(accesos.findByIdAccesoAndInvitado_IdUsuario(activo.getIdAcceso(), otro.getIdUsuario())).isEmpty();
    }

    @Test void historialesInactivosNoImpidenUnaNuevaAceptacionPeroDosActivosSeRechazan() {
        accesos.saveAndFlush(nuevoAcceso(propietario, invitado, false));
        accesos.saveAndFlush(nuevoAcceso(propietario, invitado, false));
        accesos.saveAndFlush(nuevoAcceso(propietario, invitado, true));
        assertThatThrownBy(() -> accesos.saveAndFlush(nuevoAcceso(propietario, invitado, true)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(accesos.countByPropietario_IdUsuarioAndActivoTrue(propietario.getIdUsuario())).isEqualTo(1);
    }

    @Test void baseRechazaUnAccesoAUnoMismo() {
        assertThatThrownBy(() -> accesos.saveAndFlush(nuevoAcceso(propietario, propietario, true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void baseRechazaUnaInvitacionConPlazoInvalido() {
        var invalida = nuevaInvitacion(propietario, "PENDIENTE", ahora.minusSeconds(61));
        assertThatThrownBy(() -> invitaciones.saveAndFlush(invalida))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void dosInsercionesConcurrentesNoCreanDosAccesosActivos() throws Exception {
        try (var hilos = Executors.newFixedThreadPool(2)) {
            var inicio = new CountDownLatch(1);
            Callable<Boolean> alta = () -> {
                inicio.await(5, TimeUnit.SECONDS);
                try { accesos.saveAndFlush(nuevoAcceso(propietario, invitado, true)); return true; }
                catch (DataIntegrityViolationException ex) { return false; }
            };
            var primera = hilos.submit(alta); var segunda = hilos.submit(alta); inicio.countDown();
            assertThat(List.of(primera.get(15, TimeUnit.SECONDS), segunda.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(accesos.countByPropietario_IdUsuarioAndActivoTrue(propietario.getIdUsuario())).isEqualTo(1);
    }

    private Usuario usuario() {
        var usuario = new Usuario(); usuario.setNombre("Base cuidado QA");
        usuario.setEmail("cuidado-" + UUID.randomUUID() + "@example.test");
        usuario.setPasswordHash("hash-ficticio-no-utilizable");
        usuario = usuarios.saveAndFlush(usuario); cuentas.add(usuario.getIdUsuario()); return usuario;
    }

    private Invitacion nuevaInvitacion(Usuario dueno, String estado, Instant fin) {
        return Invitacion.builder().propietario(dueno).emailInvitado(invitado.getEmail())
                .estado(estados.findByEntidadAndNombre("INVITACION", estado).orElseThrow())
                .creadoEn(ahora.minusSeconds(60)).expiraEn(fin).build();
    }

    private AccesoCompartido nuevoAcceso(Usuario dueno, Usuario destinatario, boolean activo) {
        return AccesoCompartido.builder().propietario(dueno).invitado(destinatario)
                .activo(activo).fechaIngreso(ahora).build();
    }
}
