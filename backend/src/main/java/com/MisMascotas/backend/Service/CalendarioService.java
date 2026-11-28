package com.MisMascotas.backend.Service;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import com.MisMascotas.backend.DTO.CalendarioItemDTO;
import com.MisMascotas.backend.Entity.Mascota;
import com.MisMascotas.backend.Entity.OcurrenciaRecordatorio;
import com.MisMascotas.backend.Entity.Recordatorio;
import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Exception.ValidacionRequestException;
import com.MisMascotas.backend.Recordatorios.CalculadorOcurrencias;
import com.MisMascotas.backend.Recordatorios.ProgramacionRecordatorio;
import com.MisMascotas.backend.Repository.OcurrenciaRecordatorioRepository;
import com.MisMascotas.backend.Repository.RecordatorioRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class CalendarioService {
    public static final int MAX_DIAS_CONSULTA = 366;
    private final RecordatorioRepository recordatorios;
    private final OcurrenciaRecordatorioRepository ocurrencias;
    private final AccesoMascotaService acceso;
    private final RecordatorioService servicioRecordatorios;
    private final Clock reloj;

    public List<CalendarioItemDTO> listar(String desde, String hasta, UUID actor) {
        var inicio = leerFecha(desde, "desde");
        var fin = leerFecha(hasta, "hasta");
        var hoy = hoy();
        validarRango(inicio, fin, hoy);
        var mascotas = acceso.obtenerMascotasAccesibles(actor).stream().map(Mascota::getIdMascota).toList();
        return buscar(mascotas, inicio, fin, hoy);
    }

    public CalendarioItemDTO detalle(String id, String fechaProgramada, UUID actor) {
        UUID identificador;
        try {
            identificador = UUID.fromString(id);
            if (!identificador.toString().equalsIgnoreCase(id)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException error) {
            throw new ValidacionRequestException("id: usá un UUID válido de recordatorio");
        }
        var fecha = leerInstante(fechaProgramada);
        var dia = fecha.atZone(CalculadorOcurrencias.ZONA).toLocalDate();
        var hoy = hoy();
        validarRango(dia, dia, hoy);
        var recordatorio = recordatorios.findByIdRecordatorioAndFechaEliminacionIsNull(identificador)
                .orElseThrow(() -> new RecursoNoEncontradoException("Recordatorio no encontrado"));
        acceso.verificarLectura(recordatorio.getMascota(), actor);
        return buscar(List.of(recordatorio.getMascota().getIdMascota()), dia, dia, hoy).stream()
                .filter(item -> item.idRecordatorio().equals(identificador) && item.fechaProgramada().equals(fecha))
                .findFirst().orElseThrow(() -> new RecursoNoEncontradoException(
                        "El recordatorio no tiene una aparición visible en esa fecha"));
    }

    private List<CalendarioItemDTO> buscar(List<UUID> mascotas, LocalDate desde, LocalDate hasta, LocalDate hoy) {
        if (mascotas.isEmpty()) return List.of();
        var zona = CalculadorOcurrencias.ZONA;
        var inicio = desde.atStartOfDay(zona).toInstant();
        var finExclusivo = hasta.plusDays(1).atStartOfDay(zona).toInstant();
        var resultado = new ArrayList<CalendarioItemDTO>();
        for (var unico : recordatorios.buscarUnicosEntreFechas(mascotas, inicio, finExclusivo)) {
            resultado.add(representar(unico, null, true, hoy));
        }
        for (var ocurrencia : ocurrencias.buscarEntreFechas(mascotas, inicio, finExclusivo)) {
            resultado.add(representar(ocurrencia.getRecordatorio(), ocurrencia, true, hoy));
        }
        for (var serie : recordatorios.buscarSeriesParaCalendario(mascotas, finExclusivo, hasta)) {
            // El job cubre la tanda guardada. El calendario solo calcula días futuros posteriores a esa cobertura.
            var fechaCorte = serie.getUltimaFechaGenerada().plusDays(1);
            if (!fechaCorte.isAfter(hoy)) fechaCorte = hoy.plusDays(1);
            if (fechaCorte.isBefore(desde)) fechaCorte = desde;
            if (fechaCorte.isAfter(hasta)) continue;
            var diasGuardados = new HashSet<LocalDate>();
            resultado.stream().filter(item -> item.idRecordatorio().equals(serie.getIdRecordatorio()))
                    .map(item -> item.fechaProgramada().atZone(zona).toLocalDate()).forEach(diasGuardados::add);
            var plan = ProgramacionRecordatorio.desde(serie);
            int dias = (int) ChronoUnit.DAYS.between(fechaCorte, hasta) + 1;
            for (var fecha : CalculadorOcurrencias.desde(plan, fechaCorte.atStartOfDay(zona).toInstant(), dias)) {
                if (!fecha.isBefore(finExclusivo)) break;
                if (diasGuardados.contains(fecha.atZone(zona).toLocalDate())) continue;
                var calculada = new OcurrenciaRecordatorio();
                calculada.setRecordatorio(serie);
                calculada.setFechaProgramada(fecha);
                resultado.add(representar(serie, calculada, false, hoy));
            }
        }
        resultado.sort(Comparator.comparing(CalendarioItemDTO::fechaProgramada)
                .thenComparing(item -> item.idRecordatorio().toString()));
        return List.copyOf(resultado);
    }

    private CalendarioItemDTO representar(Recordatorio registro, OcurrenciaRecordatorio ocurrencia,
            boolean persistida, LocalDate hoy) {
        var respuesta = servicioRecordatorios.mapToResponse(registro, ocurrencia, hoy);
        var mascota = registro.getMascota();
        return new CalendarioItemDTO(respuesta.idRecordatorio(), respuesta.idOcurrencia(),
                ocurrencia == null ? registro.getFechaHoraInicio() : ocurrencia.getFechaProgramada(),
                persistida, mascota.getIdMascota(), mascota.getNombre(), mascota.getPropietario().getIdUsuario(),
                respuesta.titulo(), respuesta.tipo(), respuesta.fechaHoraInicio(), respuesta.modalidad(),
                respuesta.intervaloValor(), respuesta.intervaloUnidad(), respuesta.diaSemana(), respuesta.fechaFin(),
                respuesta.creadoPorId(), respuesta.creadoEn(), respuesta.confirmadoPorId(), respuesta.confirmadoEn(),
                respuesta.estado(), respuesta.situacionTemporal(), persistida && respuesta.puedeConfirmar());
    }

    private LocalDate hoy() {
        return reloj.instant().atZone(CalculadorOcurrencias.ZONA).toLocalDate();
    }

    private void validarRango(LocalDate desde, LocalDate hasta, LocalDate hoy) {
        if (desde.isBefore(hoy)) throw new ValidacionRequestException("desde: debe ser hoy o una fecha futura");
        if (hasta.isBefore(desde)) throw new ValidacionRequestException("hasta: debe ser igual o posterior a desde");
        if (hasta.getYear() > 9999 || ChronoUnit.DAYS.between(desde, hasta) >= MAX_DIAS_CONSULTA) {
            throw new ValidacionRequestException("hasta: el rango debe abarcar como máximo 366 días y fechas hasta el año 9999");
        }
    }

    private LocalDate leerFecha(String texto, String campo) {
        if (texto == null || !texto.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new ValidacionRequestException(campo + ": usá el formato AAAA-MM-DD");
        }
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException error) {
            throw new ValidacionRequestException(campo + ": la fecha no es válida");
        }
    }

    private Instant leerInstante(String texto) {
        if (texto == null) throw new ValidacionRequestException("fechaProgramada: es obligatoria");
        try {
            var fecha = Instant.parse(texto);
            if (fecha.atZone(CalculadorOcurrencias.ZONA).getYear() > 9999) throw new DateTimeException("Año fuera de rango");
            return fecha;
        } catch (DateTimeException error) {
            throw new ValidacionRequestException("fechaProgramada: usá un instante ISO 8601 con zona u offset");
        }
    }
}
