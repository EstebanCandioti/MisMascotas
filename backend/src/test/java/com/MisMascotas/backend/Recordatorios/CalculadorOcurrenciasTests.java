package com.MisMascotas.backend.Recordatorios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.MisMascotas.backend.DTO.RecordatorioRequestDTO;
import com.MisMascotas.backend.Exception.ValidacionRequestException;

class CalculadorOcurrenciasTests {
    @Test
    void avanzaDiezDiasYReanudaSinPerderElAncla() {
        var plan = intervalo("2026-07-20T21:00:00Z", 10, "dias", null);
        assertThat(calcular(plan, plan.inicio(), 3)).containsExactly(
                instante("2026-07-20T21:00:00Z"), instante("2026-07-30T21:00:00Z"), instante("2026-08-09T21:00:00Z"));
        assertThat(calcular(plan, instante("2026-11-09T03:00:00Z"), 2)).containsExactly(
                instante("2026-11-17T21:00:00Z"), instante("2026-11-27T21:00:00Z"));
    }

    @Test
    void cadaDosSemanasConservaElDiaYHoraYOmiteUnaFechaPreviaAlCorte() {
        var plan = intervalo("2026-11-02T21:00:00Z", 2, "semanas", null);
        assertThat(calcular(plan, instante("2026-11-16T21:00:00.000001Z"), 2)).containsExactly(
                instante("2026-11-30T21:00:00Z"), instante("2026-12-14T21:00:00Z"));
    }

    @ParameterizedTest
    @CsvSource({"2027,28", "2028,29"})
    void calculaMesesCortosDesdeElDiaOriginal(int anio, int febrero) {
        var plan = intervalo(anio + "-01-31T21:00:00Z", 1, "meses", anio + "-03-31T21:00:00Z");
        assertThat(calcular(plan, instante(anio + "-02-01T03:00:00Z"), 5)).containsExactly(
                instante(anio + "-02-" + febrero + "T21:00:00Z"), instante(anio + "-03-31T21:00:00Z"));
    }

    @Test
    void conservaLaUnidadAnualDelSeedYElAnclaBisiesta() {
        var plan = intervalo("2024-02-29T21:00:00Z", 1, "anios", "2028-02-29T21:00:00Z");
        assertThat(calcular(plan, instante("2027-01-01T03:00:00Z"), 12)).containsExactly(
                instante("2027-02-28T21:00:00Z"), instante("2028-02-29T21:00:00Z"));
    }

    @Test
    void usaDiasSemanalesLocalesYRespetaUnInicioEnMartes() {
        // 02:30Z del miércoles todavía es martes a las 23:30 en Argentina.
        var plan = programacion("2026-11-04T02:30:00Z", "dias_semana", null, null,
                "miércoles, lunes", "2026-11-10T02:30:00Z");
        assertThat(calcular(plan, plan.inicio(), 12)).containsExactly(
                instante("2026-11-05T02:30:00Z"), instante("2026-11-10T02:30:00Z"));
        assertThat(plan.diasParaGuardar()).isEqualTo("LUNES,MIERCOLES");
    }

    @Test
    void finEsInclusivoYUnaProgramacionUnicaCaeUnaSolaVez() {
        String fecha = "2026-11-05T02:30:00Z";
        var unica = programacion(fecha, null, null, null, null, null);
        assertThat(calcular(unica, Instant.EPOCH, 12)).containsExactly(instante(fecha));
        assertThat(calcular(unica, unica.inicio().plusSeconds(1), 12)).isEmpty();

        var intervalo = intervalo(fecha, 1, "dias", "2026-11-06T02:29:59Z");
        assertThat(calcular(intervalo, Instant.EPOCH, 12)).containsExactly(instante(fecha));
    }

    @Test
    void unIntervaloAnualGrandePuedeQuedarFueraDelRango() {
        var plan = intervalo("2026-01-01T15:00:00Z", Integer.MAX_VALUE, "anios", "2027-01-01T15:00:00Z");
        assertThat(calcular(plan, plan.inicio(), 12)).containsExactly(plan.inicio());
    }

    @Test
    void rechazaCombinacionesIncompatiblesYDiasRepetidos() {
        String inicio = "2026-11-04T02:30:00Z";
        assertThatThrownBy(() -> programacion(inicio, "unica", 1, null, null, null))
                .isInstanceOf(ValidacionRequestException.class).hasMessageContaining("intervaloValor");
        assertThatThrownBy(() -> programacion(inicio, "recurrente", 1, "meses", "LUNES", null))
                .isInstanceOf(ValidacionRequestException.class).hasMessageContaining("diasSemana");
        assertThatThrownBy(() -> programacion(inicio, "dias_semana", null, null, "Miercoles, miércoles", null))
                .isInstanceOf(ValidacionRequestException.class).hasMessageContaining("repetido");
        assertThatThrownBy(() -> programacion(inicio, "dias_semana", null, null, "LUNES,", null))
                .isInstanceOf(ValidacionRequestException.class).hasMessageContaining("inválido");
    }

    private ProgramacionRecordatorio intervalo(String inicio, int valor, String unidad, String fin) {
        return programacion(inicio, "recurrente", valor, unidad, null, fin);
    }

    private ProgramacionRecordatorio programacion(String inicio, String modalidad, Integer valor,
            String unidad, String dias, String fin) {
        return ProgramacionRecordatorio.desde(new RecordatorioRequestDTO(null, "Cuidado", "OTRO",
                instante(inicio), modalidad, valor, unidad, dias, fin == null ? null : instante(fin)));
    }

    private static Instant instante(String fecha) {
        return Instant.parse(fecha);
    }

    private List<Instant> calcular(ProgramacionRecordatorio plan, Instant desde, int maximo) {
        return CalculadorOcurrencias.desde(plan, desde, maximo);
    }
}
