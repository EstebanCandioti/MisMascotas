package com.MisMascotas.backend.Service;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import com.MisMascotas.backend.Repository.NotificacionMercadoPagoRepository.ResultadoProgramado;
import com.MisMascotas.backend.Service.ProcesadorNotificacionMercadoPagoService.ResultadoProcesamiento;
import com.MisMascotas.backend.Service.ProcesadorNotificacionMercadoPagoService.TipoReintento;

/** Calcula la proxima ejecucion conservando los dos plazos independientes. */
public final class PlanificadorNotificacionMercadoPago {

    private PlanificadorNotificacionMercadoPago() {
    }

    public static ResultadoProgramado planificar(ResultadoProcesamiento resultado, Instant ahora, EstadoPrevio previo) {
        switch (resultado.estado()) {
            case COMPLETADA, PROCESADA_PREVIAMENTE:
                return new ResultadoProgramado(ahora, null, resultado.motivo(), "COMPLETADA",
                        previo.temporales(), previo.primerTemporal(), previo.pendientes(), previo.primerPendiente());
            case REVISION:
                return revision(resultado.motivo(), previo);
            case PENDIENTE:
                break;
        }

        if (resultado.reintento() == TipoReintento.COBRO_PENDIENTE) {
            // Una consulta valida termina el episodio tecnico, pero conserva el plazo de la factura.
            EstadoPrevio recuperado = new EstadoPrevio(0, null, previo.pendientes(), previo.primerPendiente());
            ResultadoProgramado vencido = revisionPorPlazo(ahora, recuperado);
            return vencido == null ? pendiente(resultado.motivo(), ahora, recuperado) : vencido;
        }
        ResultadoProgramado vencido = revisionPorPlazo(ahora, previo);
        return vencido == null ? temporal(resultado, ahora, previo) : vencido;
    }

    /** Se aplica despues de la ultima consulta, para no perder un cobro aprobado en el limite. */
    public static ResultadoProgramado revisionPorPlazo(Instant ahora, EstadoPrevio previo) {
        if (previo.primerTemporal() != null
                && !ahora.isBefore(previo.primerTemporal().plus(Duration.ofHours(24)))) {
            return revision("PLAZO_TEMPORAL_AGOTADO", previo);
        }
        if (previo.primerPendiente() != null
                && !ahora.isBefore(previo.primerPendiente().plus(Duration.ofDays(10)))) {
            return revision("PLAZO_COBRO_PENDIENTE_AGOTADO", previo);
        }
        return null;
    }

    private static ResultadoProgramado temporal(ResultadoProcesamiento resultado, Instant ahora, EstadoPrevio previo) {
        Instant inicio = previo.primerTemporal() == null ? ahora : previo.primerTemporal();
        int intentos = previo.temporales() + 1;
        long[] minutos = {1, 5, 15, 60};
        long demora = intentos <= minutos.length ? minutos[intentos - 1] : 60;
        Instant limite = limite(inicio.plus(Duration.ofHours(24)), previo.primerPendiente(), Duration.ofDays(10));
        Instant indicado = fechaRetryAfter(resultado.retryAfter(), ahora);
        EstadoPrevio actualizado = new EstadoPrevio(intentos, inicio, previo.pendientes(), previo.primerPendiente());
        if (indicado != null && !indicado.isBefore(limite)) {
            return revision("RETRY_AFTER_FUERA_DE_PLAZO", actualizado);
        }
        Instant proximo = indicado == null ? ahora.plus(Duration.ofMinutes(demora)) : indicado;
        return new ResultadoProgramado(null, proximo.isAfter(limite) ? limite : proximo, resultado.motivo(), "PENDIENTE",
                intentos, inicio, previo.pendientes(), previo.primerPendiente());
    }

    private static ResultadoProgramado pendiente(String motivo, Instant ahora, EstadoPrevio previo) {
        Instant inicio = previo.primerPendiente() == null ? ahora : previo.primerPendiente();
        int intentos = previo.pendientes() + 1;
        long demora = "ESPERANDO_REINTENTO_MP".equals(motivo) ? 1440 : 5;
        Instant limite = inicio.plus(Duration.ofDays(10));
        Instant proximo = ahora.plus(Duration.ofMinutes(demora));
        return new ResultadoProgramado(null, proximo.isAfter(limite) ? limite : proximo, motivo, "PENDIENTE",
                previo.temporales(), previo.primerTemporal(), intentos, inicio);
    }

    private static Instant fechaRetryAfter(String valor, Instant ahora) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String indicacion = valor.trim();
        if (indicacion.matches("[0-9]+")) {
            try {
                return ahora.plusSeconds(Long.parseLong(indicacion));
            } catch (ArithmeticException | DateTimeException | NumberFormatException ex) {
                // Una cantidad valida que excede el rango tambien excede el plazo de reintento.
                return Instant.MAX;
            }
        }
        try {
            Instant fecha = ZonedDateTime.parse(indicacion, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            return fecha.isBefore(ahora) ? ahora : fecha;
        } catch (DateTimeException ex) {
            return null;
        }
    }

    private static Instant limite(Instant vencimiento, Instant otroInicio, Duration otroPlazo) {
        if (otroInicio != null && otroInicio.plus(otroPlazo).isBefore(vencimiento)) {
            return otroInicio.plus(otroPlazo);
        }
        return vencimiento;
    }

    private static ResultadoProgramado revision(String motivo, EstadoPrevio previo) {
        return new ResultadoProgramado(null, null, motivo, "REVISION",
                previo.temporales(), previo.primerTemporal(), previo.pendientes(), previo.primerPendiente());
    }

    public record EstadoPrevio(int temporales, Instant primerTemporal, int pendientes, Instant primerPendiente) {
    }
}
