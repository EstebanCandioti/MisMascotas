package com.MisMascotas.backend.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificacionMercadoPagoRepository {

    private static final String INSERTAR = """
            INSERT INTO public.notificacion_mercado_pago (id_notificacion, topico, id_recurso)
            VALUES (?, ?, ?)
            ON CONFLICT ON CONSTRAINT notificacion_mercado_pago_pkey DO NOTHING
            """;

    private final JdbcTemplate jdbcTemplate;

    public NotificacionMercadoPagoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Una sola sentencia delega la deduplicacion concurrente a PostgreSQL.
     * Un reenvio conserva los datos, la recepcion y el procesamiento originales.
     */
    public ResultadoRegistro registrar(String idNotificacion, String topico, String idRecurso) {
        validar(idNotificacion, "El ID de la notificacion");
        validar(topico, "El topico");
        validar(idRecurso, "El ID del recurso");

        int insertadas = jdbcTemplate.update(INSERTAR, idNotificacion, topico, idRecurso);
        return insertadas == 1 ? ResultadoRegistro.REGISTRADA : ResultadoRegistro.YA_RECIBIDA;
    }

    public Optional<NotificacionMercadoPago> buscar(String idNotificacion) {
        validar(idNotificacion, "El ID de la notificacion");
        return jdbcTemplate.query("""
                SELECT id_notificacion, topico, id_recurso, procesada_en
                FROM public.notificacion_mercado_pago WHERE id_notificacion = ?
                """, (fila, numero) -> new NotificacionMercadoPago(
                        fila.getString("id_notificacion"), fila.getString("topico"), fila.getString("id_recurso"),
                        fecha(fila.getTimestamp("procesada_en"))), idNotificacion).stream().findFirst();
    }

    /** Consulta un lote vencido para el unico procesador secuencial del backend. */
    public List<NotificacionMercadoPago> buscarVencidas(Instant ahora, int limite) {
        return jdbcTemplate.query("""
                SELECT id_notificacion, topico, id_recurso, procesada_en
                FROM public.notificacion_mercado_pago
                WHERE procesada_en IS NULL AND estado_procesamiento = 'PENDIENTE'
                  AND proximo_procesamiento_en <= ?
                ORDER BY proximo_procesamiento_en, recibida_en, id_notificacion
                LIMIT ?
                """, (fila, numero) -> new NotificacionMercadoPago(
                fila.getString(1), fila.getString(2), fila.getString(3), fecha(fila.getTimestamp(4))),
                Timestamp.from(ahora), limite);
    }

    public boolean aplicarResultado(String id, ResultadoProgramado resultado) {
        return jdbcTemplate.update("""
                UPDATE public.notificacion_mercado_pago
                SET procesada_en = ?, proximo_procesamiento_en = ?, ultimo_motivo = ?,
                    estado_procesamiento = ?, intentos_temporales = ?, primer_fallo_temporal_en = ?,
                    intentos_pendientes = ?, primera_comprobacion_pendiente_en = ?
                WHERE id_notificacion = ? AND procesada_en IS NULL AND estado_procesamiento = 'PENDIENTE'
                """, fecha(resultado.procesadaEn()), fecha(resultado.proximo()), resultado.motivo(), resultado.estado(),
                resultado.intentosTemporales(), fecha(resultado.primerTemporal()), resultado.intentosPendientes(),
                fecha(resultado.primerPendiente()), id) == 1;
    }

    public com.MisMascotas.backend.Service.PlanificadorNotificacionMercadoPago.EstadoPrevio estadoPrevio(String id) {
        return jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(CASE WHEN n.estado_procesamiento = 'PENDIENTE' THEN n.intentos_temporales END), 0),
                       MIN(CASE WHEN n.estado_procesamiento = 'PENDIENTE' THEN n.primer_fallo_temporal_en END),
                       COALESCE(MAX(n.intentos_pendientes), 0), MIN(n.primera_comprobacion_pendiente_en)
                FROM public.notificacion_mercado_pago n
                JOIN public.notificacion_mercado_pago actual
                  ON actual.topico = n.topico AND actual.id_recurso = n.id_recurso
                WHERE actual.id_notificacion = ?
                """, (fila, numero) -> new com.MisMascotas.backend.Service.PlanificadorNotificacionMercadoPago.EstadoPrevio(
                fila.getInt(1), fecha(fila.getTimestamp(2)), fila.getInt(3), fecha(fila.getTimestamp(4))), id);
    }

    /** Captura los IDs antes del HTTP. Una notificacion posterior se evalua en la siguiente tanda. */
    public List<NotificacionMercadoPago> capturarPendientes(String topico, String recurso) {
        return jdbcTemplate.query("""
                SELECT id_notificacion, topico, id_recurso, procesada_en
                FROM public.notificacion_mercado_pago
                WHERE topico = ? AND id_recurso = ? AND estado_procesamiento = 'PENDIENTE' AND procesada_en IS NULL
                ORDER BY recibida_en, id_notificacion
                """, (f, n) -> new NotificacionMercadoPago(f.getString(1), f.getString(2), f.getString(3),
                fecha(f.getTimestamp(4))), topico, recurso);
    }

    public void guardarObservacion(String id, com.MisMascotas.backend.Service.ProcesadorNotificacionMercadoPagoService.Observacion o) {
        if (o == null) return;
        jdbcTemplate.update("""
                UPDATE public.notificacion_mercado_pago
                SET estado_factura_mp = ?, estado_pago_mp = ?, detalle_pago_mp = ?, id_pago_mp = ?,
                    intento_mp = ?, fecha_debito_mp = ?, proximo_reintento_mp = ? WHERE id_notificacion = ?
                """, o.estadoFactura(), o.estadoPago(), o.detallePago(), o.idPago(), o.intento(),
                fecha(o.fechaDebito()), fecha(o.proximoReintento()), id);
    }

    private Timestamp fecha(Instant valor) { return valor == null ? null : Timestamp.from(valor); }

    /** No cambia una fecha ya confirmada por otra ejecucion. */
    public boolean marcarProcesada(String idNotificacion) {
        validar(idNotificacion, "El ID de la notificacion");
        return jdbcTemplate.update("""
                UPDATE public.notificacion_mercado_pago
                SET procesada_en = now(), proximo_procesamiento_en = NULL,
                    estado_procesamiento = 'COMPLETADA'
                WHERE id_notificacion = ? AND procesada_en IS NULL
                """, idNotificacion) == 1;
    }

    private Instant fecha(Timestamp fecha) {
        return fecha == null ? null : fecha.toInstant();
    }

    private void validar(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " es obligatorio");
        }
    }

    public enum ResultadoRegistro {
        REGISTRADA,
        YA_RECIBIDA
    }

    public record NotificacionMercadoPago(String idNotificacion, String topico, String idRecurso,
            Instant procesadaEn) {
    }
    public record ResultadoProgramado(Instant procesadaEn, Instant proximo, String motivo, String estado,
            int intentosTemporales, Instant primerTemporal, int intentosPendientes, Instant primerPendiente) {}
}
