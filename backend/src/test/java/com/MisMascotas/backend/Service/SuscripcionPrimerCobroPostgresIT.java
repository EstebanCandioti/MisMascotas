package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

import com.MisMascotas.backend.Entity.Suscripcion;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Integration.MercadoPagoGateway;
import com.MisMascotas.backend.Integration.MercadoPagoGateway.*;
import com.MisMascotas.backend.Repository.EstadoRepository;
import com.MisMascotas.backend.Repository.PagoRepository;
import com.MisMascotas.backend.Repository.SuscripcionRepository;
import com.MisMascotas.backend.Repository.UsuarioRepository;
import com.MisMascotas.backend.Service.SuscripcionCobroTransaccionalService.EstadoCobro;

/** Solo se habilita con una base PostgreSQL dedicada, nunca con la conexion local de desarrollo. */
@SpringBootTest(properties = {
        "spring.datasource.url=${QA_COBRO_DB_URL}",
        "spring.datasource.username=${QA_COBRO_DB_USERNAME}",
        "spring.datasource.password=${QA_COBRO_DB_PASSWORD}",
        "spring.jpa.hibernate.ddl-auto=validate",
        "jwt.secret=secreto-ficticio-de-pruebas-con-mas-de-32-caracteres",
        "suscripciones.planes.premium-mensual.importe=2500.00",
        "auditoria.pendientes.procesamiento-habilitado=false"
})
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_PRIMER_COBRO_IT", matches = "true")
class SuscripcionPrimerCobroPostgresIT {

    @jakarta.annotation.Resource private SuscripcionService service;
    @jakarta.annotation.Resource private SuscripcionReservaService reservas;
    @jakarta.annotation.Resource private UsuarioRepository usuarios;
    @jakarta.annotation.Resource private SuscripcionRepository suscripciones;
    @jakarta.annotation.Resource private PagoRepository pagos;
    @jakarta.annotation.Resource private EstadoRepository estados;
    @jakarta.annotation.Resource private JdbcTemplate sql;
    @jakarta.annotation.Resource private PremiumService premium;
    @MockitoBean private MercadoPagoGateway proveedor;
    @MockitoBean private java.time.Clock reloj;
    @MockitoSpyBean private EventoAuditoriaPendienteService auditoria;

    private final List<UUID> usuariosQA = new ArrayList<>();
    private Usuario usuario;
    private Suscripcion suscripcion;
    private String idPago;
    private static final Instant FECHA = Instant.parse("2026-02-01T02:30:00Z");

    @BeforeEach
    void preparar() {
        when(reloj.instant()).thenReturn(FECHA);
        usuario = crearUsuario();
        suscripcion = crearReserva(usuario);
        idPago = UUID.randomUUID().toString();
        configurarProveedor(suscripcion);
    }

    @AfterEach
    void limpiarSoloDatosDeEstaPrueba() {
        for (UUID id : usuariosQA) {
            sql.update("DELETE FROM log_auditoria WHERE actor_id = ?", id);
            sql.update("DELETE FROM evento_auditoria_pendiente WHERE actor_id = ?", id);
            sql.update("DELETE FROM pago WHERE suscripcion_id IN (SELECT id_suscripcion FROM suscripcion WHERE usuario_id = ?)", id);
            sql.update("DELETE FROM suscripcion WHERE usuario_id = ?", id);
            sql.update("DELETE FROM usuario WHERE id_usuario = ?", id);
        }
    }

    @Test
    void dosConfirmacionesConcurrentesYUnaRepeticionDejanUnPagoYDosEventos() throws Exception {
        CountDownLatch consultas = new CountDownLatch(2);
        when(proveedor.consultarPreapproval(suscripcion.getIdSuscripcionMercadopago())).thenAnswer(invocacion -> {
            consultas.countDown();
            if (!consultas.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("No llegaron ambas consultas simuladas");
            }
            return remoto(suscripcion);
        });

        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var primera = ejecutor.submit(() -> service.confirmarPrimerCobro(usuario.getIdUsuario(), suscripcion.getIdSuscripcion()));
            var segunda = ejecutor.submit(() -> service.confirmarPrimerCobro(usuario.getIdUsuario(), suscripcion.getIdSuscripcion()));
            var a = primera.get(20, TimeUnit.SECONDS);
            var b = segunda.get(20, TimeUnit.SECONDS);
            assertThat(List.of(a.estado(), b.estado())).containsExactlyInAnyOrder(EstadoCobro.ACTIVADA, EstadoCobro.YA_CONFIRMADA);
            assertThat(a.idPago()).isEqualTo(b.idPago());
            assertThat(a.fechaVencimiento()).isEqualTo(b.fechaVencimiento());
        }

        var repetida = service.confirmarPrimerCobro(usuario.getIdUsuario(), suscripcion.getIdSuscripcion());
        assertThat(repetida.estado()).isEqualTo(EstadoCobro.YA_CONFIRMADA);
        assertThat(repetida.fechaVencimiento()).isEqualTo(Instant.parse("2026-03-01T02:30:00Z"));
        assertThat(pagos.findBySuscripcion_IdSuscripcionOrderByFechaPagoAsc(suscripcion.getIdSuscripcion())).hasSize(1);
        assertThat(usuarios.findById(usuario.getIdUsuario()).orElseThrow().isEsPremium()).isTrue();
        assertThat(cantidadEventos()).isEqualTo(2);
        assertThat(sql.queryForList("SELECT tipo_accion FROM evento_auditoria_pendiente WHERE actor_id = ?",
                String.class, usuario.getIdUsuario())).containsExactlyInAnyOrder("CREATE", "ACTIVATE");
        verify(proveedor, times(2)).consultarPreapproval(suscripcion.getIdSuscripcionMercadopago());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(longs = {0, 1})
    void confirmacionTardiaRegistraElPagoSinConcederPremiumFueraDelPeriodo(long segundos) {
        var fin = Instant.parse("2026-03-01T02:30:00Z");
        when(reloj.instant()).thenReturn(fin.plusSeconds(segundos));
        var resultado = service.confirmarPrimerCobro(usuario.getIdUsuario(), suscripcion.getIdSuscripcion());
        assertThat(resultado.estado()).isEqualTo(EstadoCobro.ACTIVADA);
        assertThat(resultado.fechaVencimiento()).isEqualTo(fin);
        assertThat(pagos.findBySuscripcion_IdSuscripcionOrderByFechaPagoAsc(suscripcion.getIdSuscripcion())).hasSize(1);
        assertThat(usuarios.findById(usuario.getIdUsuario()).orElseThrow().isEsPremium()).isFalse();
        assertThat(premium.esPremium(usuario.getIdUsuario())).isFalse();
        assertThat(cantidadEventos()).isEqualTo(2);
    }

    @Test
    void falloLuegoDelSegundoEncoladoReviertePagoPremiumActivacionYAmbosEventos() {
        EventoAuditoriaPendienteService auditoriaObjetivo = AopTestUtils.getUltimateTargetObject(auditoria);
        doAnswer(invocacion -> {
            Object resultado = invocacion.callRealMethod();
            if ("suscripcion".equals(invocacion.getArgument(1))) {
                throw new IllegalStateException("Fallo QA posterior al segundo encolado");
            }
            return resultado;
        }).when(auditoriaObjetivo).encolar(any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> service.confirmarPrimerCobro(usuario.getIdUsuario(), suscripcion.getIdSuscripcion()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(pagos.findBySuscripcion_IdSuscripcionOrderByFechaPagoAsc(suscripcion.getIdSuscripcion())).isEmpty();
        assertThat(usuarios.findById(usuario.getIdUsuario()).orElseThrow().isEsPremium()).isFalse();
        assertThat(sql.queryForObject("SELECT fecha_vencimiento FROM suscripcion WHERE id_suscripcion = ?",
                java.sql.Timestamp.class, suscripcion.getIdSuscripcion())).isNull();
        assertThat(cantidadEventos()).isZero();
        assertThat(sql.queryForObject("""
                SELECT e.nombre FROM suscripcion s JOIN estados e ON s.estado_id = e.id_estado
                WHERE s.id_suscripcion = ?
                """, String.class, suscripcion.getIdSuscripcion())).isEqualTo("PENDIENTE");
    }

    @Test
    void reemplazoConfirmadoDuranteLaConsultaImpideActivarElIntentoCancelado() {
        when(proveedor.consultarPago(idPago)).thenAnswer(invocacion -> {
            reservas.reemplazarTrasCancelacionConfirmada(usuario.getIdUsuario(), suscripcion.getIdSuscripcion(),
                    suscripcion.getIdSuscripcionMercadopago());
            return pagoRemoto();
        });

        assertThat(service.confirmarPrimerCobro(usuario.getIdUsuario(), suscripcion.getIdSuscripcion()).estado())
                .isEqualTo(EstadoCobro.PENDIENTE_CONCILIACION);
        assertThat(pagos.findBySuscripcion_IdSuscripcionOrderByFechaPagoAsc(suscripcion.getIdSuscripcion())).isEmpty();
        assertThat(usuarios.findById(usuario.getIdUsuario()).orElseThrow().isEsPremium()).isFalse();
        assertThat(cantidadEventos()).isZero();
        assertThat(suscripciones.findAllByUsuario_IdUsuario(usuario.getIdUsuario())).hasSize(2);
    }

    @Test
    void pagoYaUsadoPorOtraCuentaNoActivaLaSegunda() {
        assertThat(service.confirmarPrimerCobro(usuario.getIdUsuario(), suscripcion.getIdSuscripcion()).estado())
                .isEqualTo(EstadoCobro.ACTIVADA);
        Usuario otro = crearUsuario();
        Suscripcion otra = crearReserva(otro);
        configurarProveedor(otra);

        assertThat(service.confirmarPrimerCobro(otro.getIdUsuario(), otra.getIdSuscripcion()).estado())
                .isEqualTo(EstadoCobro.PENDIENTE_CONCILIACION);
        assertThat(usuarios.findById(otro.getIdUsuario()).orElseThrow().isEsPremium()).isFalse();
        assertThat(pagos.findBySuscripcion_IdSuscripcionOrderByFechaPagoAsc(otra.getIdSuscripcion())).isEmpty();
    }

    @Test
    void baseRechazaDosSuscripcionesConElMismoIdRemoto() {
        Suscripcion duplicada = crearReserva(crearUsuario());
        duplicada.setIdSuscripcionMercadopago(suscripcion.getIdSuscripcionMercadopago());
        assertThatThrownBy(() -> suscripciones.saveAndFlush(duplicada))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void baseRechazaDosPagosConElMismoPaymentId() {
        service.confirmarPrimerCobro(usuario.getIdUsuario(), suscripcion.getIdSuscripcion());
        Suscripcion otra = crearReserva(crearUsuario());
        var duplicado = new com.MisMascotas.backend.Entity.Pago();
        duplicado.setSuscripcion(otra);
        duplicado.setIdTransaccionMercadopago(idPago);
        duplicado.setMonto(new BigDecimal("2500"));
        duplicado.setFechaPago(FECHA);
        duplicado.setEstadoCatalogo(estados.findByEntidadAndNombre("PAGO", "APROBADO").orElseThrow());

        assertThatThrownBy(() -> pagos.saveAndFlush(duplicado))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private long cantidadEventos() {
        return sql.queryForObject("SELECT count(*) FROM evento_auditoria_pendiente WHERE actor_id = ?", Long.class,
                usuario.getIdUsuario());
    }

    private Usuario crearUsuario() {
        Usuario nuevo = new Usuario();
        nuevo.setNombre("Usuario QA Primer Cobro");
        nuevo.setEmail("primer-cobro+" + UUID.randomUUID() + "@example.test");
        nuevo.setPasswordHash("hash-ficticio-no-utilizable");
        nuevo = usuarios.saveAndFlush(nuevo);
        usuariosQA.add(nuevo.getIdUsuario());
        return nuevo;
    }

    private Suscripcion crearReserva(Usuario propietario) {
        Suscripcion nueva = new Suscripcion();
        nueva.setUsuario(propietario);
        nueva.setTipoPlan("PREMIUM_MENSUAL");
        nueva.setEstadoCatalogo(estados.findByEntidadAndNombre("SUSCRIPCION", "PENDIENTE").orElseThrow());
        nueva.setIdSuscripcionMercadopago("preapproval-qa-" + UUID.randomUUID());
        return suscripciones.saveAndFlush(nueva);
    }

    private void configurarProveedor(Suscripcion reserva) {
        when(proveedor.consultarPreapproval(reserva.getIdSuscripcionMercadopago())).thenAnswer(invocacion -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
                    .as("La consulta HTTP simulada debe ocurrir fuera de la transaccion")
                    .isFalse();
            return remoto(reserva);
        });
        when(proveedor.buscarCuotas(reserva.getIdSuscripcionMercadopago()))
                .thenReturn(List.of(new CuotaConsultada(reserva.getIdSuscripcionMercadopago(), idPago)));
        when(proveedor.consultarPago(idPago)).thenReturn(pagoRemoto());
    }

    private PreapprovalConsultado remoto(Suscripcion reserva) {
        return new PreapprovalConsultado(reserva.getIdSuscripcionMercadopago(), reserva.getIdSuscripcion().toString(),
                "authorized", null, new BigDecimal("2500"), "ARS", 1, "months");
    }

    private PagoConsultado pagoRemoto() {
        return new PagoConsultado(idPago, "approved", new BigDecimal("2500"), "ARS", FECHA);
    }
}
