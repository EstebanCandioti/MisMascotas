package com.MisMascotas.backend.Recordatorios;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Calcula fechas de una programación validada a partir de su inicio original. */
public final class CalculadorOcurrencias {
    public static final String ID_ZONA = "America/Argentina/Buenos_Aires";
    public static final ZoneId ZONA = ZoneId.of(ID_ZONA);

    private CalculadorOcurrencias() {}

    /** Devuelve hasta `maximo` fechas desde el instante indicado, con ambos límites inclusivos. */
    public static List<Instant> desde(ProgramacionRecordatorio plan, Instant desdeInclusivo, int maximo) {
        if (maximo < 1) throw new IllegalArgumentException("El máximo debe ser positivo");
        var desde = desdeInclusivo.isBefore(plan.inicio()) ? plan.inicio() : desdeInclusivo;
        if (plan.fin() != null && desde.isAfter(plan.fin())) return List.of();
        if (plan.modalidad() == ProgramacionRecordatorio.Modalidad.UNICA) {
            return desde.equals(plan.inicio()) ? List.of(plan.inicio()) : List.of();
        }

        var inicioLocal = plan.inicio().atZone(ZONA);
        var fechaInicial = inicioLocal.toLocalDate();
        var hora = inicioLocal.toLocalTime();
        var fechaCorte = desde.atZone(ZONA).toLocalDate();
        var resultado = new ArrayList<Instant>();
        if (plan.modalidad() == ProgramacionRecordatorio.Modalidad.DIAS_SEMANA) {
            LocalDate fecha = fechaCorte;
            while (resultado.size() < maximo) {
                Instant ocurrencia = alInstante(plan.inicio(), fechaInicial, hora, fecha);
                if (superaFin(plan, ocurrencia)) break;
                if (plan.dias().contains(fecha.getDayOfWeek()) && !ocurrencia.isBefore(desde)) {
                    resultado.add(ocurrencia);
                }
                if (fecha.equals(LocalDate.MAX)) break;
                fecha = fecha.plusDays(1);
            }
        } else {
            // Saltar directamente al intervalo cercano al corte, sin recorrer las tandas anteriores.
            long indice = distancia(plan.unidad(), fechaInicial, fechaCorte) / plan.intervalo();
            long ultimoIndice = distancia(plan.unidad(), fechaInicial, LocalDate.MAX) / plan.intervalo();
            while (resultado.size() < maximo && indice <= ultimoIndice) {
                var fecha = sumar(plan.unidad(), fechaInicial, indice * plan.intervalo());
                Instant ocurrencia = alInstante(plan.inicio(), fechaInicial, hora, fecha);
                if (superaFin(plan, ocurrencia)) break;
                if (!ocurrencia.isBefore(desde)) resultado.add(ocurrencia);
                indice++;
            }
        }
        return List.copyOf(resultado);
    }

    private static boolean superaFin(ProgramacionRecordatorio plan, Instant fecha) {
        return plan.fin() != null && fecha.isAfter(plan.fin());
    }

    private static Instant alInstante(Instant inicio, LocalDate fechaInicial, LocalTime hora, LocalDate fecha) {
        return fecha.equals(fechaInicial) ? inicio : fecha.atTime(hora).atZone(ZONA).toInstant();
    }

    private static long distancia(ProgramacionRecordatorio.Unidad unidad, LocalDate inicio, LocalDate corte) {
        return switch (unidad) {
            case DIAS -> ChronoUnit.DAYS.between(inicio, corte);
            case SEMANAS -> ChronoUnit.WEEKS.between(inicio, corte);
            case MESES -> ChronoUnit.MONTHS.between(YearMonth.from(inicio), YearMonth.from(corte));
            case ANIOS -> (long) corte.getYear() - inicio.getYear();
        };
    }

    private static LocalDate sumar(ProgramacionRecordatorio.Unidad unidad, LocalDate inicio, long cantidad) {
        return switch (unidad) {
            case DIAS -> inicio.plusDays(cantidad);
            case SEMANAS -> inicio.plusWeeks(cantidad);
            case MESES -> inicio.plusMonths(cantidad);
            case ANIOS -> inicio.plusYears(cantidad);
        };
    }
}
