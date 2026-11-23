package com.MisMascotas.backend.Repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import com.MisMascotas.backend.Repository.NotificacionMercadoPagoRepository.ResultadoRegistro;
import com.MisMascotas.backend.Service.ProcesadorNotificacionMercadoPagoService;
import com.MisMascotas.backend.Service.ProcesadorNotificacionMercadoPagoService.Estado;
import com.MisMascotas.backend.Service.ProcesadorNotificacionMercadoPagoService.ResultadoProcesamiento;
import com.MisMascotas.backend.Service.ProcesadorNotificacionesMercadoPagoProgramado;

/** Prueba JDBC aislada: no carga Spring Boot ni application.properties. */
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_NOTIFICACIONES_IT", matches = "true")
class NotificacionMercadoPagoPostgresIT {

    private static final String BASE_QA = "qa_notificaciones_mercado_pago";
    private static final String TOPICO = "subscription_authorized_payment";
    private static final String FACTURA = "7034059187";
    private static final String MIGRACION = "../DB/migraciones/20261116_notificacion_mercado_pago.sql";

    private DriverManagerDataSource dataSource;
    private JdbcTemplate sql;
    private NotificacionMercadoPagoRepository repository;
    private final List<String> idsCreados = new ArrayList<>();

    @BeforeEach
    void preparar() throws IOException {
        String url = variableRequerida("QA_MP_NOTIFICACIONES_DB_URL");
        if (!url.startsWith("jdbc:postgresql://")) {
            throw new IllegalArgumentException("La prueba requiere una URL JDBC de PostgreSQL local");
        }
        URI destino = URI.create(url.substring("jdbc:".length()));
        if (!List.of("localhost", "127.0.0.1", "[::1]").contains(destino.getHost())
                || !("/" + BASE_QA).equals(destino.getPath()) || destino.getRawQuery() != null) {
            throw new IllegalArgumentException("Usar exclusivamente PostgreSQL local y la base dedicada " + BASE_QA);
        }
        dataSource = new DriverManagerDataSource(url,
                variableRequerida("QA_MP_NOTIFICACIONES_DB_USERNAME"),
                variableRequerida("QA_MP_NOTIFICACIONES_DB_PASSWORD"));
        sql = new JdbcTemplate(dataSource);
        assertThat(sql.queryForObject("SELECT current_database()", String.class)).isEqualTo(BASE_QA);
        aplicarMigracion();
        repository = new NotificacionMercadoPagoRepository(sql);
    }

    @AfterEach
    void limpiarSoloLasFilasDeEstaPrueba() {
        if (sql != null) {
            for (String id : idsCreados) {
                sql.update("DELETE FROM public.notificacion_mercado_pago WHERE id_notificacion = ?", id);
            }
        }
    }

    @Test
    void nuevaNotificacionQuedaPersistidaYPendiente() {
        String id = nuevoId();

        assertThat(repository.registrar(id, TOPICO, FACTURA)).isEqualTo(ResultadoRegistro.REGISTRADA);

        assertThat(contar(id)).isEqualTo(1);
        assertThat(sql.queryForObject("SELECT topico FROM public.notificacion_mercado_pago WHERE id_notificacion = ?",
                String.class, id)).isEqualTo(TOPICO);
        assertThat(sql.queryForObject("SELECT id_recurso FROM public.notificacion_mercado_pago WHERE id_notificacion = ?",
                String.class, id)).isEqualTo(FACTURA);
        assertThat(fechaRecepcion(id)).isNotNull();
        assertThat(sql.queryForObject("SELECT procesada_en IS NULL FROM public.notificacion_mercado_pago WHERE id_notificacion = ?",
                Boolean.class, id)).isTrue();
    }

    @Test
    void reenvioNoDuplicaNiSobrescribeDatosOFechaOriginales() {
        String id = nuevoId();
        repository.registrar(id, TOPICO, FACTURA);
        Instant original = fechaRecepcion(id);

        assertThat(repository.registrar(id, "otro-topico-qa", "otra-factura-qa")).isEqualTo(ResultadoRegistro.YA_RECIBIDA);

        assertThat(contar(id)).isEqualTo(1);
        assertThat(fechaRecepcion(id)).isEqualTo(original);
        assertThat(sql.queryForObject("SELECT id_recurso FROM public.notificacion_mercado_pago WHERE id_notificacion = ?",
                String.class, id)).isEqualTo(FACTURA);
        assertThat(sql.queryForObject("SELECT topico FROM public.notificacion_mercado_pago WHERE id_notificacion = ?",
                String.class, id)).isEqualTo(TOPICO);
    }

    @Test
    void dosNotificacionesDistintasDeLaMismaFacturaSeGuardanSeparadas() {
        String primero = nuevoId();
        String segundo = nuevoId();

        assertThat(repository.registrar(primero, TOPICO, FACTURA)).isEqualTo(ResultadoRegistro.REGISTRADA);
        assertThat(repository.registrar(segundo, TOPICO, FACTURA)).isEqualTo(ResultadoRegistro.REGISTRADA);

        assertThat(contar(primero) + contar(segundo)).isEqualTo(2);
    }

    @Test
    void dosInsercionesConcurrentesDelMismoIdDejanUnaFila() throws Exception {
        String id = nuevoId();
        CountDownLatch preparados = new CountDownLatch(2);
        CountDownLatch inicio = new CountDownLatch(1);
        java.util.concurrent.Callable<ResultadoRegistro> insertar = () -> {
            preparados.countDown();
            if (!inicio.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("No se libero el inicio de la prueba");
            }
            return repository.registrar(id, TOPICO, FACTURA);
        };
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var primero = ejecutor.submit(insertar);
            var segundo = ejecutor.submit(insertar);
            try {
                assertThat(preparados.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                inicio.countDown();
            }
            assertThat(List.of(primero.get(15, TimeUnit.SECONDS), segundo.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(ResultadoRegistro.REGISTRADA, ResultadoRegistro.YA_RECIBIDA);
        }
        assertThat(contar(id)).isEqualTo(1);
    }

    @Test
    void laRestriccionDeBaseImpideDuplicarInclusoFueraDelRepositorio() {
        String id = nuevoId();
        repository.registrar(id, TOPICO, FACTURA);

        assertThatThrownBy(() -> sql.update("""
                INSERT INTO public.notificacion_mercado_pago (id_notificacion, topico, id_recurso)
                VALUES (?, ?, ?)
                """, id, TOPICO, FACTURA)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(contar(id)).isEqualTo(1);
    }

    @Test
    void reenvioDeUnaNotificacionProcesadaNoLaVuelvePendiente() {
        String id = nuevoId();
        repository.registrar(id, TOPICO, FACTURA);
        sql.update("UPDATE public.notificacion_mercado_pago SET procesada_en = now() WHERE id_notificacion = ?", id);

        assertThat(repository.registrar(id, TOPICO, FACTURA)).isEqualTo(ResultadoRegistro.YA_RECIBIDA);
        assertThat(sql.queryForObject("SELECT procesada_en IS NOT NULL FROM public.notificacion_mercado_pago WHERE id_notificacion = ?",
                Boolean.class, id)).isTrue();
    }

    @Test
    void migracionIndependientePuedeRepetirseSinPerderNotificaciones() throws IOException {
        String id = nuevoId();
        repository.registrar(id, TOPICO, FACTURA);

        aplicarMigracion();

        assertThat(contar(id)).isEqualTo(1);
        assertThat(sql.queryForObject("""
                SELECT pg_get_constraintdef(oid) FROM pg_constraint
                WHERE conrelid = 'public.notificacion_mercado_pago'::regclass
                  AND conname = 'notificacion_mercado_pago_pkey'
                """, String.class)).isEqualTo("PRIMARY KEY (id_notificacion)");
        assertThat(sql.queryForObject("""
                SELECT relrowsecurity FROM pg_class
                WHERE oid = 'public.notificacion_mercado_pago'::regclass
                """, Boolean.class)).isTrue();
    }

    @Test
    void schedulerGuardaFinalizacionReintentoYRevisionEnPostgres() {
        String completada = nuevoId();
        String pendiente = nuevoId();
        String revision = nuevoId();
        repository.registrar(completada, TOPICO, FACTURA + "-completada");
        repository.registrar(pendiente, TOPICO, FACTURA);
        repository.registrar(revision, TOPICO, FACTURA + "-revision");
        Instant ahora = Instant.now().plusSeconds(1);
        var procesador = mock(ProcesadorNotificacionMercadoPagoService.class);
        when(procesador.evaluar(any())).thenAnswer(invocacion -> {
            NotificacionMercadoPagoRepository.NotificacionMercadoPago notificacion = invocacion.getArgument(0);
            if (notificacion.idNotificacion().equals(completada)) {
                return ResultadoProcesamiento.completada("YA_CONFIRMADA");
            }
            if (notificacion.idNotificacion().equals(revision)) {
                return ResultadoProcesamiento.revision("FACTURA_INCONSISTENTE");
            }
            return ResultadoProcesamiento.cobroPendiente("COBRO_SIN_COBRO_APROBADO");
        });

        scheduler(procesador, ahora).ejecutar();

        assertThat(sql.queryForObject(
                "SELECT estado_procesamiento FROM public.notificacion_mercado_pago WHERE id_notificacion = ?",
                String.class, completada)).isEqualTo("COMPLETADA");
        assertThat(sql.queryForObject(
                "SELECT procesada_en IS NOT NULL AND proximo_procesamiento_en IS NULL FROM public.notificacion_mercado_pago WHERE id_notificacion = ?",
                Boolean.class, completada)).isTrue();
        assertThat(sql.queryForObject(
                "SELECT estado_procesamiento = 'REVISION' AND proximo_procesamiento_en IS NULL AND procesada_en IS NULL FROM public.notificacion_mercado_pago WHERE id_notificacion = ?",
                Boolean.class, revision)).isTrue();
        assertThat(repository.estadoPrevio(pendiente).pendientes()).isEqualTo(1);
        assertThat(repository.buscarVencidas(ahora, 20)).isEmpty();
        assertThat(repository.buscarVencidas(ahora.plusSeconds(300), 20))
                .extracting(NotificacionMercadoPagoRepository.NotificacionMercadoPago::idNotificacion)
                .containsExactly(pendiente);
    }

    @Test
    void nuevaEjecucionRetomaElReintentoPersistidoCuandoLlegaSuFecha() {
        String id = nuevoId();
        repository.registrar(id, TOPICO, FACTURA);
        Instant ahora = Instant.now().plusSeconds(1);
        var procesador = mock(ProcesadorNotificacionMercadoPagoService.class);
        when(procesador.evaluar(any()))
                .thenReturn(ResultadoProcesamiento.cobroPendiente("COBRO_SIN_COBRO_APROBADO"))
                .thenReturn(ResultadoProcesamiento.completada("ACTIVADA"));

        scheduler(procesador, ahora).ejecutar();
        scheduler(procesador, ahora.plusSeconds(299)).ejecutar();
        verify(procesador).evaluar(any());

        scheduler(procesador, ahora.plusSeconds(300)).ejecutar();

        verify(procesador, times(2)).evaluar(any());
        assertThat(repository.buscar(id).orElseThrow().procesadaEn()).isNotNull();
        assertThat(repository.buscarVencidas(ahora.plusSeconds(600), 20)).isEmpty();
    }

    @Test
    void repetirMigracionConservaReintentosYRevision() throws IOException {
        String id = nuevoId();
        repository.registrar(id, TOPICO, FACTURA);
        sql.update("UPDATE public.notificacion_mercado_pago SET estado_procesamiento = 'REVISION', "
                + "ultimo_motivo = 'FACTURA_INCONSISTENTE', proximo_procesamiento_en = NULL WHERE id_notificacion = ?", id);

        aplicarMigracion();

        assertThat(sql.queryForObject(
                "SELECT estado_procesamiento = 'REVISION' AND ultimo_motivo = 'FACTURA_INCONSISTENTE' "
                        + "AND proximo_procesamiento_en IS NULL FROM public.notificacion_mercado_pago WHERE id_notificacion = ?",
                Boolean.class, id)).isTrue();
        assertThat(sql.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' "
                        + "AND table_name = 'notificacion_mercado_pago' AND column_name IN ('id_reserva', 'reservado_hasta')",
                Long.class)).isZero();
    }


    @Test
    void persisteRetryAfterYRevisionYPermiteOtraNotificacionDeLaMismaFactura() {
        String limitada = nuevoId();
        String revision = nuevoId();
        repository.registrar(limitada, TOPICO, FACTURA);
        repository.registrar(revision, TOPICO, FACTURA + "-revision");
        Instant ahora = Instant.now().plusSeconds(1);
        var procesador = mock(ProcesadorNotificacionMercadoPagoService.class);
        when(procesador.evaluar(any())).thenAnswer(invocacion -> {
            NotificacionMercadoPagoRepository.NotificacionMercadoPago notificacion = invocacion.getArgument(0);
            if (notificacion.idNotificacion().equals(revision)) {
                return ResultadoProcesamiento.revision("HTTP_401");
            }
            return new ResultadoProcesamiento(Estado.PENDIENTE, "HTTP_429",
                    ProcesadorNotificacionMercadoPagoService.TipoReintento.TEMPORAL, "120");
        });

        scheduler(procesador, ahora).ejecutar();

        assertThat(sql.queryForObject(
                "SELECT ultimo_motivo = 'HTTP_429' AND estado_procesamiento = 'PENDIENTE' "
                        + "AND intentos_temporales = 1 AND intentos_pendientes = 0 "
                        + "FROM public.notificacion_mercado_pago WHERE id_notificacion = ?",
                Boolean.class, limitada)).isTrue();
        assertThat(repository.buscarVencidas(ahora.plusSeconds(119), 20)).isEmpty();
        assertThat(repository.buscarVencidas(ahora.plusSeconds(120), 20))
                .extracting(NotificacionMercadoPagoRepository.NotificacionMercadoPago::idNotificacion)
                .containsExactly(limitada);
        assertThat(sql.queryForObject(
                "SELECT ultimo_motivo = 'HTTP_401' AND estado_procesamiento = 'REVISION' "
                        + "AND proximo_procesamiento_en IS NULL AND procesada_en IS NULL "
                        + "FROM public.notificacion_mercado_pago WHERE id_notificacion = ?",
                Boolean.class, revision)).isTrue();

        String nueva = nuevoId();
        repository.registrar(nueva, TOPICO, FACTURA + "-revision");
        doReturn(ResultadoProcesamiento.completada("YA_CONFIRMADA")).when(procesador).evaluar(any());
        scheduler(procesador, ahora.plusSeconds(1)).ejecutar();
        assertThat(repository.buscar(nueva).orElseThrow().procesadaEn()).isNotNull();
        assertThat(repository.buscar(revision).orElseThrow().procesadaEn()).isNull();
    }

    private ProcesadorNotificacionesMercadoPagoProgramado scheduler(
            ProcesadorNotificacionMercadoPagoService procesador, Instant ahora) {
        return new ProcesadorNotificacionesMercadoPagoProgramado(repository, procesador,
                Clock.fixed(ahora, ZoneOffset.UTC), new DataSourceTransactionManager(dataSource), 20,
                mock(com.MisMascotas.backend.Repository.SuscripcionRepository.class), mock(com.MisMascotas.backend.Service.SuscripcionVencimientoService.class));
    }

    private void aplicarMigracion() throws IOException {
        sql.execute(Files.readString(Path.of(MIGRACION)));
        sql.execute(Files.readString(Path.of("../DB/migraciones/20261118_reintentos_notificacion_mercado_pago.sql")));
    }

    private String nuevoId() {
        String id = "notificacion-qa-" + UUID.randomUUID();
        idsCreados.add(id);
        return id;
    }

    private long contar(String id) {
        return sql.queryForObject("SELECT count(*) FROM public.notificacion_mercado_pago WHERE id_notificacion = ?",
                Long.class, id);
    }

    private Instant fechaRecepcion(String id) {
        return sql.queryForObject("SELECT recibida_en FROM public.notificacion_mercado_pago WHERE id_notificacion = ?",
                (fila, numero) -> fila.getTimestamp("recibida_en").toInstant(), id);
    }

    private String variableRequerida(String nombre) {
        String valor = System.getenv(nombre);
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Falta configurar " + nombre);
        }
        return valor;
    }
}
