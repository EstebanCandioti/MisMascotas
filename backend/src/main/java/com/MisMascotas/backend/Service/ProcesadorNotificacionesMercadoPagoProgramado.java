package com.MisMascotas.backend.Service;

import java.time.Clock;
import java.util.HashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.MisMascotas.backend.Repository.NotificacionMercadoPagoRepository;
import com.MisMascotas.backend.Repository.SuscripcionRepository;
import com.MisMascotas.backend.Repository.NotificacionMercadoPagoRepository.NotificacionMercadoPago;
import com.MisMascotas.backend.Service.ProcesadorNotificacionMercadoPagoService.ResultadoProcesamiento;

@Component
@ConditionalOnProperty(name = "mercadopago.notificaciones.scheduler-habilitado", havingValue = "true", matchIfMissing = true)
public class ProcesadorNotificacionesMercadoPagoProgramado {
    private static final Logger logger = LoggerFactory.getLogger(ProcesadorNotificacionesMercadoPagoProgramado.class);
    private final NotificacionMercadoPagoRepository notificaciones;
    private final ProcesadorNotificacionMercadoPagoService procesador;
    private final Clock reloj;
    private final int tamanoLote;
    private final TransactionTemplate transaccion;
    private final SuscripcionRepository suscripciones;
    private final SuscripcionVencimientoService vencimientos;

    public ProcesadorNotificacionesMercadoPagoProgramado(NotificacionMercadoPagoRepository notificaciones,
            ProcesadorNotificacionMercadoPagoService procesador, Clock reloj, PlatformTransactionManager gestor,
            @Value("${mercadopago.notificaciones.tamano-lote:20}") int tamanoLote,
            SuscripcionRepository suscripciones, SuscripcionVencimientoService vencimientos) {
        if (tamanoLote <= 0) throw new IllegalArgumentException("El tamano del lote debe ser mayor que cero");
        this.notificaciones = notificaciones; this.procesador = procesador; this.reloj = reloj;
        this.tamanoLote = tamanoLote; this.transaccion = new TransactionTemplate(gestor);
        this.suscripciones = suscripciones; this.vencimientos = vencimientos;
    }

    @Scheduled(fixedDelayString = "${mercadopago.notificaciones.intervalo-ms:30000}",
            initialDelayString = "${mercadopago.notificaciones.demora-inicial-ms:30000}")
    public synchronized void ejecutar() {
        try {
            var vistos = new HashSet<String>();
            for (var n : notificaciones.buscarVencidas(reloj.instant(), tamanoLote)) {
                if (vistos.add(n.topico() + "\0" + n.idRecurso())) procesar(n);
            }
        } catch (RuntimeException ex) {
            logger.error("No se pudo consultar la bandeja de Mercado Pago por {}", ex.getClass().getSimpleName());
        }
        try {
            for (var usuarioId : suscripciones.buscarUsuariosConCoberturaVencida(reloj.instant(), tamanoLote)) {
                try { vencimientos.vencer(usuarioId); }
                catch (RuntimeException ex) { logger.error("No se pudo evaluar el vencimiento del usuario {} por {}",
                        usuarioId, ex.getClass().getSimpleName()); }
            }
        } catch (RuntimeException ex) {
            logger.error("No se pudieron consultar los vencimientos por {}", ex.getClass().getSimpleName());
        }
    }

    private void procesar(NotificacionMercadoPago notificacion) {
        try {
            var capturadas = notificaciones.capturarPendientes(notificacion.topico(), notificacion.idRecurso());
            if (capturadas.isEmpty()) return;
            var previo = notificaciones.estadoPrevio(notificacion.idNotificacion());
            // Incluso al vencer el plazo se consulta una ultima vez. Un webhook nuevo siempre puede resolverlo.
            var evaluacion = evaluar(notificacion);
            var plan = PlanificadorNotificacionMercadoPago.planificar(evaluacion, reloj.instant(), previo);
            transaccion.executeWithoutResult(tx -> {
                for (var n : capturadas) {
                    if (notificaciones.aplicarResultado(n.idNotificacion(), plan))
                        notificaciones.guardarObservacion(n.idNotificacion(), evaluacion.observacion());
                }
            });
        } catch (RuntimeException ex) {
            logger.error("No se pudo guardar el resultado de la notificacion {} por {}",
                    notificacion.idNotificacion(), ex.getClass().getSimpleName());
        }
    }
    private ResultadoProcesamiento evaluar(NotificacionMercadoPago notificacion) {
        try { return procesador.evaluar(notificacion); }
        catch (RuntimeException ex) {
            logger.warn("Fallo el procesamiento de la notificacion {} por {}", notificacion.idNotificacion(), ex.getClass().getSimpleName());
            return ResultadoProcesamiento.temporal("FALLO_TEMPORAL");
        }
    }
}
