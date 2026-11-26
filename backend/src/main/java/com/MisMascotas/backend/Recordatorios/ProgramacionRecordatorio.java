package com.MisMascotas.backend.Recordatorios;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.MisMascotas.backend.DTO.RecordatorioRequestDTO;
import com.MisMascotas.backend.Entity.Recordatorio;
import com.MisMascotas.backend.Exception.ValidacionRequestException;

public record ProgramacionRecordatorio(Modalidad modalidad, Instant inicio, Instant fin,
        Integer intervalo, Unidad unidad, Set<DayOfWeek> dias) {

    public static final int OCURRENCIAS_POR_TANDA = 12;
    public static final int MAX_OCURRENCIAS_CON_FIN = 366;

    public enum Modalidad { UNICA, RECURRENTE, DIAS_SEMANA }
    public enum Unidad { DIAS, SEMANAS, MESES, ANIOS }

    private static final List<String> NOMBRES_DIAS =
            List.of("LUNES", "MARTES", "MIERCOLES", "JUEVES", "VIERNES", "SABADO", "DOMINGO");

    public ProgramacionRecordatorio {
        dias = Set.copyOf(dias);
    }

    public static ProgramacionRecordatorio desde(RecordatorioRequestDTO solicitud) {
        return desde(solicitud.modalidad(), solicitud.fechaHoraInicio(), solicitud.fechaFin(),
                solicitud.intervaloValor(), solicitud.intervaloUnidad(), solicitud.diasSemana());
    }

    public static ProgramacionRecordatorio desde(Recordatorio recordatorio) {
        return desde(recordatorio.getModalidad(), recordatorio.getFechaHoraInicio(), recordatorio.getFechaFin(),
                recordatorio.getIntervaloValor(), recordatorio.getIntervaloUnidad(), recordatorio.getDiaSemana());
    }

    private static ProgramacionRecordatorio desde(String modalidadGuardada, Instant inicio, Instant fin,
            Integer intervalo, String unidad, String dias) {
        var modalidad = modalidadGuardada == null ? Modalidad.UNICA
                : leerEnum(Modalidad.class, modalidadGuardada, "modalidad");
        if (inicio == null) throw invalido("fechaHoraInicio", "es obligatoria");
        if (fin != null && fin.isBefore(inicio)) {
            throw invalido("fechaFin", "debe ser igual o posterior a fechaHoraInicio");
        }

        if (modalidad == Modalidad.RECURRENTE) {
            if (intervalo == null || intervalo < 1) {
                throw invalido("intervaloValor", "debe ser un entero mayor que cero");
            }
            if (dias != null) throw invalido("diasSemana", "no corresponde con modalidad recurrente");
            return new ProgramacionRecordatorio(modalidad, inicio, fin, intervalo,
                    leerEnum(Unidad.class, unidad, "intervaloUnidad"), Set.of());
        }

        if (intervalo != null) throw invalido("intervaloValor", "solo corresponde con modalidad recurrente");
        if (unidad != null) throw invalido("intervaloUnidad", "solo corresponde con modalidad recurrente");
        if (modalidad == Modalidad.UNICA && dias != null) {
            throw invalido("diasSemana", "solo corresponde con modalidad dias_semana");
        }
        return new ProgramacionRecordatorio(modalidad, inicio, fin, null, null,
                modalidad == Modalidad.DIAS_SEMANA ? leerDias(dias) : Set.of());
    }

    public String diasParaGuardar() {
        return dias.isEmpty() ? null : dias.stream().sorted()
                .map(dia -> NOMBRES_DIAS.get(dia.getValue() - 1))
                .collect(Collectors.joining(","));
    }

    public boolean esSerie() {
        return modalidad != Modalidad.UNICA;
    }

    private static Set<DayOfWeek> leerDias(String texto) {
        if (texto == null || texto.isBlank()) throw invalido("diasSemana", "seleccioná al menos un día");
        var dias = EnumSet.noneOf(DayOfWeek.class);
        for (String entrada : texto.split(",", -1)) {
            String nombre = Normalizer.normalize(entrada.strip(), Normalizer.Form.NFD)
                    .replaceAll("\\p{M}+", "").toUpperCase(Locale.ROOT);
            int posicion = NOMBRES_DIAS.indexOf(nombre);
            if (posicion < 0) throw invalido("diasSemana", "contiene un día inválido");
            if (!dias.add(DayOfWeek.of(posicion + 1))) {
                throw invalido("diasSemana", "contiene un día repetido");
            }
        }
        return dias;
    }

    private static <E extends Enum<E>> E leerEnum(Class<E> tipo, String valor, String campo) {
        if (valor == null) throw invalido(campo, "es obligatorio");
        try {
            return Enum.valueOf(tipo, valor.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw invalido(campo, "valor inválido");
        }
    }

    private static ValidacionRequestException invalido(String campo, String motivo) {
        return new ValidacionRequestException(campo + ": " + motivo);
    }
}
