package com.MisMascotas.backend.Service;

import java.time.Instant;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import com.MisMascotas.backend.Exception.MercadoPagoException;
import com.MisMascotas.backend.Integration.MercadoPagoGateway;
import com.MisMascotas.backend.Repository.NotificacionMercadoPagoRepository;
import com.MisMascotas.backend.Repository.SuscripcionRepository;

@Service
public class ProcesadorNotificacionMercadoPagoService {
    private static final Set<String> PAGOS_PENDIENTES = Set.of("pending", "in_process", "authorized", "in_mediation");
    private final NotificacionMercadoPagoRepository notificaciones;
    private final SuscripcionRepository suscripciones;
    private final MercadoPagoGateway proveedor;
    private final SuscripcionCicloService ciclo;
    private final NotificacionMercadoPagoMarcadoService marcado;

    public ProcesadorNotificacionMercadoPagoService(NotificacionMercadoPagoRepository notificaciones,
            SuscripcionRepository suscripciones, MercadoPagoGateway proveedor, SuscripcionCicloService ciclo,
            NotificacionMercadoPagoMarcadoService marcado) {
        this.notificaciones = notificaciones; this.suscripciones = suscripciones;
        this.proveedor = proveedor; this.ciclo = ciclo; this.marcado = marcado;
    }

    public ResultadoProcesamiento procesar(String idNotificacion) {
        var notificacion = notificaciones.buscar(idNotificacion).orElse(null);
        if (notificacion == null) return ResultadoProcesamiento.revision("NOTIFICACION_NO_ENCONTRADA");
        if (notificacion.procesadaEn() != null)
            return new ResultadoProcesamiento(Estado.PROCESADA_PREVIAMENTE, "YA_PROCESADA", null, null);
        var resultado = evaluar(notificacion);
        if (resultado.estado() == Estado.COMPLETADA) {
            try { marcado.marcarProcesada(notificacion.idNotificacion()); }
            catch (RuntimeException ex) { return ResultadoProcesamiento.temporal("NO_SE_PUDO_MARCAR_PROCESADA"); }
        }
        return resultado;
    }

    /** Evalua el estado actual. El scheduler guarda el resultado y su observacion en una transaccion corta. */
    public ResultadoProcesamiento evaluar(NotificacionMercadoPagoRepository.NotificacionMercadoPago n) {
        try {
            if ("subscription_preapproval".equals(n.topico())) return ciclo.sincronizar(n.idRecurso());
            if (!"subscription_authorized_payment".equals(n.topico()))
                return ResultadoProcesamiento.revision("TOPICO_NO_SOPORTADO");
            return verificarFactura(n.idRecurso());
        } catch (MercadoPagoException ex) {
            return ResultadoProcesamiento.desdeErrorProveedor(ex);
        } catch (DataIntegrityViolationException ex) {
            // Se captura despues de que el proxy transaccional revirtio la escritura.
            return ResultadoProcesamiento.revision("PAGO_O_FACTURA_YA_ASOCIADOS");
        }
    }

    private ResultadoProcesamiento verificarFactura(String id) {
        var f = proveedor.consultarFacturaAutorizada(id);
        if (f == null || !id.equals(f.id()) || blanco(f.idPreapproval()) || blanco(f.referenciaExterna()))
            return ResultadoProcesamiento.revision("FACTURA_INCONSISTENTE");
        var local = suscripciones.findByIdSuscripcionMercadopagoConUsuario(f.idPreapproval()).orElse(null);
        if (local == null || !local.getIdSuscripcion().toString().equals(f.referenciaExterna()) || local.getUsuario() == null)
            return ResultadoProcesamiento.revision("SUSCRIPCION_INCONSISTENTE");
        var p = f.idPago() == null ? null : proveedor.consultarPago(f.idPago());
        Observacion observacion = new Observacion(f.estado(), p == null ? null : p.estado(),
                p == null ? null : p.detalleEstado(), f.idPago(), f.intento(), f.fechaDebito(), f.proximoReintento());
        ResultadoProcesamiento resultado;
        if (blanco(f.estado())) resultado = ResultadoProcesamiento.revision("ESTADO_FACTURA_AUSENTE");
        else if (f.idPago() != null && (p == null || !f.idPago().equals(p.id()) || blanco(p.estado())))
            resultado = ResultadoProcesamiento.revision("PAGO_INCONSISTENTE");
        else if (p != null && Set.of("refunded", "charged_back", "cancelled", "canceled").contains(p.estado()))
            resultado = ResultadoProcesamiento.revision("PAGO_REQUIERE_REVISION");
        else if ("recycling".equals(f.estado()) && p != null && "rejected".equals(p.estado()))
            resultado = ResultadoProcesamiento.cobroPendiente("ESPERANDO_REINTENTO_MP");
        else if ("waiting for gateway".equals(f.estado()) && (p == null || PAGOS_PENDIENTES.contains(p.estado())))
            resultado = ResultadoProcesamiento.cobroPendiente("ESPERANDO_RESOLUCION_MP");
        else if ("processed".equals(f.estado()) && p != null && "rejected".equals(p.estado()))
            resultado = ResultadoProcesamiento.completada("CUOTA_SIN_COBRO");
        else if ("processed".equals(f.estado()) && p != null && "approved".equals(p.estado()))
            resultado = ciclo.registrar(local, f, p);
        else if ("scheduled".equals(f.estado()) && p == null)
            resultado = ResultadoProcesamiento.cobroPendiente("COBRO_SIN_COBRO_APROBADO");
        else resultado = ResultadoProcesamiento.revision("ESTADO_INCONSISTENTE");
        return resultado.conObservacion(observacion);
    }

    private boolean blanco(String valor) { return valor == null || valor.isBlank(); }
    public enum Estado { COMPLETADA, PROCESADA_PREVIAMENTE, PENDIENTE, REVISION }
    public enum TipoReintento { TEMPORAL, COBRO_PENDIENTE }
    public record Observacion(String estadoFactura, String estadoPago, String detallePago, String idPago,
            Integer intento, Instant fechaDebito, Instant proximoReintento) {}
    public record ResultadoProcesamiento(Estado estado, String motivo, TipoReintento reintento, String retryAfter,
            Observacion observacion) {
        public ResultadoProcesamiento(Estado estado, String motivo, TipoReintento reintento, String retryAfter) {
            this(estado, motivo, reintento, retryAfter, null);
        }
        public ResultadoProcesamiento conObservacion(Observacion valor) {
            return new ResultadoProcesamiento(estado, motivo, reintento, retryAfter, valor);
        }
        public static ResultadoProcesamiento completada(String motivo) { return new ResultadoProcesamiento(Estado.COMPLETADA, motivo, null, null); }
        public static ResultadoProcesamiento revision(String motivo) { return new ResultadoProcesamiento(Estado.REVISION, motivo, null, null); }
        public static ResultadoProcesamiento temporal(String motivo) { return new ResultadoProcesamiento(Estado.PENDIENTE, motivo, TipoReintento.TEMPORAL, null); }
        public static ResultadoProcesamiento cobroPendiente(String motivo) { return new ResultadoProcesamiento(Estado.PENDIENTE, motivo, TipoReintento.COBRO_PENDIENTE, null); }
        public static ResultadoProcesamiento desdeErrorProveedor(MercadoPagoException error) {
            String motivo = error.codigoHttp() == null ? error.tipoFallo().name() : "HTTP_" + error.codigoHttp();
            return switch (error.tipoFallo()) {
                case TEMPORAL -> temporal(motivo);
                case LIMITE_SOLICITUDES -> new ResultadoProcesamiento(Estado.PENDIENTE, motivo, TipoReintento.TEMPORAL, error.retryAfter());
                case CREDENCIALES_INVALIDAS, HTTP_INVALIDO, RESPUESTA_INVALIDA -> revision(motivo);
            };
        }
    }
}
