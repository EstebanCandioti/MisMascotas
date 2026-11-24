package com.MisMascotas.backend.Repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.MisMascotas.backend.Repository.NotificacionMercadoPagoRepository.ResultadoRegistro;

@ExtendWith(MockitoExtension.class)
class NotificacionMercadoPagoRepositoryTests {

    private static final String ID = "notificacion-qa-001";
    private static final String TOPICO = "subscription_authorized_payment";
    private static final String FACTURA = "7034059187";

    @Mock private JdbcTemplate jdbcTemplate;
    private NotificacionMercadoPagoRepository repository;

    @BeforeEach
    void preparar() {
        repository = new NotificacionMercadoPagoRepository(jdbcTemplate);
    }

    @Test
    void devuelveRegistradaConUnaSolaEscrituraYParametrosSeparados() {
        when(jdbcTemplate.update(anyString(), eq(ID), eq(TOPICO), eq(FACTURA))).thenReturn(1);

        assertThat(repository.registrar(ID, TOPICO, FACTURA)).isEqualTo(ResultadoRegistro.REGISTRADA);

        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), eq(ID), eq(TOPICO), eq(FACTURA));
        assertThat(sql.getValue()).contains("ON CONFLICT ON CONSTRAINT notificacion_mercado_pago_pkey DO NOTHING")
                .doesNotContain(ID, TOPICO, FACTURA);
        verifyNoMoreInteractions(jdbcTemplate);
    }

    @Test
    void devuelveYaRecibidaCuandoPostgresNoInsertaPorConflicto() {
        when(jdbcTemplate.update(anyString(), eq(ID), eq(TOPICO), eq(FACTURA))).thenReturn(0);

        assertThat(repository.registrar(ID, TOPICO, FACTURA)).isEqualTo(ResultadoRegistro.YA_RECIBIDA);

        verify(jdbcTemplate).update(anyString(), eq(ID), eq(TOPICO), eq(FACTURA));
        verifyNoMoreInteractions(jdbcTemplate);
    }

    @Test
    void noConfundeUnFalloDeBaseConUnaNotificacionYaRecibida() {
        when(jdbcTemplate.update(anyString(), eq(ID), eq(TOPICO), eq(FACTURA)))
                .thenThrow(new DataAccessResourceFailureException("Fallo de base simulado"));

        assertThatThrownBy(() -> repository.registrar(ID, TOPICO, FACTURA))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    @ParameterizedTest
    @MethodSource("entradasAusentes")
    void datosRequeridosAusentesNoEjecutanSql(String id, String topico, String recurso) {
        assertThatThrownBy(() -> repository.registrar(id, topico, recurso))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbcTemplate);
    }

    static Stream<Arguments> entradasAusentes() {
        return IntStream.range(0, 3).boxed().flatMap(indice -> Stream.of(null, "", " ").map(ausente -> {
            String[] datos = { ID, TOPICO, FACTURA };
            datos[indice] = ausente;
            return Arguments.of((Object[]) datos);
        }));
    }
}
