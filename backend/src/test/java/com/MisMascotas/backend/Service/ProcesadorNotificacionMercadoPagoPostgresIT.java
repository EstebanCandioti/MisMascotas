package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;

import com.MisMascotas.backend.Entity.Suscripcion;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Integration.MercadoPagoGateway;
import com.MisMascotas.backend.Integration.MercadoPagoGateway.*;
import com.MisMascotas.backend.Repository.EstadoRepository;
import com.MisMascotas.backend.Repository.NotificacionMercadoPagoRepository;
import com.MisMascotas.backend.Repository.PagoRepository;
import com.MisMascotas.backend.Repository.SuscripcionRepository;
import com.MisMascotas.backend.Repository.UsuarioRepository;
import com.MisMascotas.backend.Service.ProcesadorNotificacionMercadoPagoService.Estado;

/** Invoca el servicio, sin HTTP del webhook, contra PostgreSQL QA dedicado. */
@SpringBootTest(properties = {
        "spring.datasource.url=${QA_COBRO_DB_URL}", "spring.datasource.username=${QA_COBRO_DB_USERNAME}",
        "spring.datasource.password=${QA_COBRO_DB_PASSWORD}", "spring.jpa.hibernate.ddl-auto=validate",
        "jwt.secret=secreto-ficticio-de-pruebas-con-mas-de-32-caracteres",
        "mercadopago.notificaciones.scheduler-habilitado=false",
        "suscripciones.planes.premium-mensual.importe=2500.00", "auditoria.pendientes.procesamiento-habilitado=false" })
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_PROCESADOR_NOTIFICACION_IT", matches = "true")
class ProcesadorNotificacionMercadoPagoPostgresIT {
    @jakarta.annotation.Resource private ProcesadorNotificacionMercadoPagoService procesador;
    @MockitoSpyBean private NotificacionMercadoPagoRepository notificaciones;
    @jakarta.annotation.Resource private PlatformTransactionManager gestor;
    @jakarta.annotation.Resource private UsuarioRepository usuarios;
    @jakarta.annotation.Resource private SuscripcionRepository suscripciones;
    @jakarta.annotation.Resource private EstadoRepository estados;
    @jakarta.annotation.Resource private PagoRepository pagos;
    @jakarta.annotation.Resource private JdbcTemplate sql;
    @MockitoBean private MercadoPagoGateway proveedor;
    private Usuario usuario; private Suscripcion suscripcion; private String notificacion; private String pago;

    @BeforeEach void preparar() throws Exception {
        sql.execute(Files.readString(Path.of("../DB/migraciones/20261116_notificacion_mercado_pago.sql")));
        sql.execute(Files.readString(Path.of("../DB/migraciones/20261118_reintentos_notificacion_mercado_pago.sql")));
        usuario = new Usuario(); usuario.setNombre("QA procesador"); usuario.setEmail("procesador+" + UUID.randomUUID() + "@example.test"); usuario.setPasswordHash("hash-qa");
        usuario = usuarios.saveAndFlush(usuario);
        suscripcion = new Suscripcion(); suscripcion.setUsuario(usuario); suscripcion.setTipoPlan("PREMIUM_MENSUAL");
        suscripcion.setEstadoCatalogo(estados.findByEntidadAndNombre("SUSCRIPCION", "PENDIENTE").orElseThrow());
        suscripcion.setIdSuscripcionMercadopago("preapproval-qa-" + UUID.randomUUID()); suscripcion = suscripciones.saveAndFlush(suscripcion);
        notificacion = "notificacion-qa-" + UUID.randomUUID(); pago = "pago-qa-" + UUID.randomUUID();
        notificaciones.registrar(notificacion, "subscription_authorized_payment", "7034059187");
        when(proveedor.consultarFacturaAutorizada("7034059187")).thenReturn(new FacturaAutorizada("7034059187", suscripcion.getIdSuscripcionMercadopago(), suscripcion.getIdSuscripcion().toString(), pago, "processed", 1, null, null, new BigDecimal("2500"), "ARS"));
        when(proveedor.consultarPreapproval(suscripcion.getIdSuscripcionMercadopago())).thenReturn(new PreapprovalConsultado(suscripcion.getIdSuscripcionMercadopago(), suscripcion.getIdSuscripcion().toString(), "authorized", null, new BigDecimal("2500"), "ARS", 1, "months"));
        when(proveedor.buscarCuotas(suscripcion.getIdSuscripcionMercadopago())).thenReturn(java.util.List.of(new CuotaConsultada(suscripcion.getIdSuscripcionMercadopago(), pago)));
        when(proveedor.consultarPago(pago)).thenReturn(new PagoConsultado(pago, "approved", new BigDecimal("2500"), "ARS", Instant.parse("2026-02-01T02:30:00Z")));
    }
    @AfterEach void limpiar() {
        if (usuario == null) return;
        sql.update("DELETE FROM public.notificacion_mercado_pago WHERE id_notificacion = ?", notificacion);
        sql.update("DELETE FROM evento_auditoria_pendiente WHERE actor_id = ? OR id_entidad = ? OR id_entidad IN (SELECT id_pago FROM pago WHERE suscripcion_id = ?)", usuario.getIdUsuario(), suscripcion.getIdSuscripcion(), suscripcion.getIdSuscripcion());
        sql.update("DELETE FROM pago WHERE suscripcion_id = ?", suscripcion.getIdSuscripcion());
        sql.update("DELETE FROM suscripcion WHERE usuario_id = ?", usuario.getIdUsuario()); sql.update("DELETE FROM usuario WHERE id_usuario = ?", usuario.getIdUsuario());
    }
    @Test void procesaPrimerCobroYLaRepeticionNoDuplicaPagoNiAuditoria() {
        assertThat(procesador.procesar(notificacion).estado()).isEqualTo(Estado.COMPLETADA);
        assertThat(procesador.procesar(notificacion).estado()).isEqualTo(Estado.PROCESADA_PREVIAMENTE);
        assertThat(pagos.findBySuscripcion_IdSuscripcionOrderByFechaPagoAsc(suscripcion.getIdSuscripcion())).hasSize(1);
        assertThat(sql.queryForObject("SELECT procesada_en IS NOT NULL FROM public.notificacion_mercado_pago WHERE id_notificacion = ?", Boolean.class, notificacion)).isTrue();
        assertThat(sql.queryForObject("SELECT count(*) FROM evento_auditoria_pendiente WHERE id_entidad = ? OR id_entidad IN (SELECT id_pago FROM pago WHERE suscripcion_id = ?)", Long.class, suscripcion.getIdSuscripcion(), suscripcion.getIdSuscripcion())).isEqualTo(2L);
    }
    @Test
    void reinicioTrasFalloDeMarcadoConservaUnPagoYLaAuditoriaOriginal() {
        NotificacionMercadoPagoRepository destino = AopTestUtils.getUltimateTargetObject(notificaciones);
        doThrow(new DataAccessResourceFailureException("Fallo de guardado simulado")).doCallRealMethod()
                .when(destino).aplicarResultado(org.mockito.ArgumentMatchers.eq(notificacion), org.mockito.ArgumentMatchers.any());
        Clock reloj = Clock.fixed(Instant.now().plusSeconds(1), ZoneOffset.UTC);

        new ProcesadorNotificacionesMercadoPagoProgramado(notificaciones, procesador, reloj, gestor, 20, suscripciones, org.mockito.Mockito.mock(SuscripcionVencimientoService.class)).ejecutar();

        assertThat(notificaciones.buscar(notificacion).orElseThrow().procesadaEn()).isNull();
        assertThat(pagos.findBySuscripcion_IdSuscripcionOrderByFechaPagoAsc(suscripcion.getIdSuscripcion())).hasSize(1);
        Instant vencimiento = suscripciones.findById(suscripcion.getIdSuscripcion()).orElseThrow().getFechaVencimiento();

        new ProcesadorNotificacionesMercadoPagoProgramado(notificaciones, procesador, reloj, gestor, 20, suscripciones, org.mockito.Mockito.mock(SuscripcionVencimientoService.class)).ejecutar();

        assertThat(notificaciones.buscar(notificacion).orElseThrow().procesadaEn()).isNotNull();
        assertThat(pagos.findBySuscripcion_IdSuscripcionOrderByFechaPagoAsc(suscripcion.getIdSuscripcion())).hasSize(1);
        assertThat(suscripciones.findById(suscripcion.getIdSuscripcion()).orElseThrow().getFechaVencimiento()).isEqualTo(vencimiento);
        assertThat(sql.queryForObject("SELECT count(*) FROM evento_auditoria_pendiente WHERE id_entidad = ? OR id_entidad IN (SELECT id_pago FROM pago WHERE suscripcion_id = ?)",
                Long.class, suscripcion.getIdSuscripcion(), suscripcion.getIdSuscripcion())).isEqualTo(2L);
        assertThat(sql.queryForObject("SELECT ultimo_motivo FROM public.notificacion_mercado_pago WHERE id_notificacion = ?",
                String.class, notificacion)).isEqualTo("YA_CONFIRMADA");
    }

}
