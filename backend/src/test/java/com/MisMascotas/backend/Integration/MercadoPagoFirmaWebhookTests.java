package com.MisMascotas.backend.Integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MercadoPagoFirmaWebhookTests {

    private static final String SECRETO = "secreto-ficticio-mismascotas";
    private static final String DATA_ID = "7034059187";
    private static final String REQUEST_ID = "qa-webhook-001";
    private static final String TS = "1704908010";
    // Vector calculado por separado en PowerShell y comprobado en Java.
    private static final String FIRMA = "de029c13ba6f5a3eb2f103e5a5562903fa77c957ca2f9181bb48ca90974cf1aa";
    private static final String SIGNATURE = "ts=" + TS + ",v1=" + FIRMA;

    private final MercadoPagoFirmaWebhook validador = new MercadoPagoFirmaWebhook();

    @Test
    void aceptaElVectorFijoSinAplicarUnaReglaDeAntiguedad() {
        assertThat(validador.esValida(DATA_ID, REQUEST_ID, SIGNATURE, SECRETO)).isTrue();
    }

    @Test
    void extraeLosCamposPorNombreAunqueCambieElOrden() {
        assertThat(validador.esValida(DATA_ID, REQUEST_ID, "v1=" + FIRMA + ",ts=" + TS, SECRETO)).isTrue();
    }

    @Test
    void admiteEspaciosDeSeparacionEnElEncabezado() {
        assertThat(validador.esValida(DATA_ID, REQUEST_ID, " ts = " + TS + " , v1 = " + FIRMA + " ", SECRETO))
                .isTrue();
    }

    @Test
    void comparaBytesYAdmiteHexadecimalEnMayusculas() {
        assertThat(validador.esValida(DATA_ID, REQUEST_ID, "ts=" + TS + ",v1=" + FIRMA.toUpperCase(Locale.ROOT), SECRETO))
                .isTrue();
    }

    @Test
    void cambiarElDataIdInvalidaLaFirma() {
        assertThat(validador.esValida("7034059188", REQUEST_ID, SIGNATURE, SECRETO)).isFalse();
    }

    @Test
    void cambiarElRequestIdInvalidaLaFirma() {
        assertThat(validador.esValida(DATA_ID, "qa-webhook-002", SIGNATURE, SECRETO)).isFalse();
    }

    @Test
    void cambiarElSecretoInvalidaLaFirma() {
        assertThat(validador.esValida(DATA_ID, REQUEST_ID, SIGNATURE, "otro-secreto-ficticio")).isFalse();
    }

    @Test
    void cambiarElTimestampInvalidaLaFirma() {
        assertThat(validador.esValida(DATA_ID, REQUEST_ID, "ts=1704908011,v1=" + FIRMA, SECRETO)).isFalse();
    }

    @Test
    void noNormalizaLosDatosQueFormanElMensaje() {
        assertThat(validador.esValida("0" + DATA_ID, REQUEST_ID, SIGNATURE, SECRETO)).isFalse();
        assertThat(validador.esValida(DATA_ID, REQUEST_ID + " ", SIGNATURE, SECRETO)).isFalse();
        assertThat(validador.esValida(DATA_ID, REQUEST_ID, SIGNATURE, SECRETO + " ")).isFalse();
        assertThat(validador.esValida(DATA_ID, REQUEST_ID, "ts=0" + TS + ",v1=" + FIRMA, SECRETO)).isFalse();
    }

    @Test
    void rechazaUnDigestHexadecimalValidoConUnByteAlterado() {
        String alterada = "00" + FIRMA.substring(2);
        assertThat(validador.esValida(DATA_ID, REQUEST_ID, "ts=" + TS + ",v1=" + alterada, SECRETO)).isFalse();
    }

    @ParameterizedTest
    @MethodSource("entradasAusentes")
    void rechazaCualquierEntradaRequeridaAusente(String dataId, String requestId, String signature, String secreto) {
        assertThat(validador.esValida(dataId, requestId, signature, secreto)).isFalse();
    }

    static Stream<Arguments> entradasAusentes() {
        return IntStream.range(0, 4).boxed().flatMap(indice -> Stream.of(null, "", " ", "\t").map(ausente -> {
            String[] datos = { DATA_ID, REQUEST_ID, SIGNATURE, SECRETO };
            datos[indice] = ausente;
            return Arguments.of((Object[]) datos);
        }));
    }

    @ParameterizedTest
    @MethodSource("firmasMalFormadas")
    void rechazaFirmasMalFormadasOCamposDuplicados(String signature) {
        assertThat(validador.esValida(DATA_ID, REQUEST_ID, signature, SECRETO)).isFalse();
    }

    static Stream<String> firmasMalFormadas() {
        return Stream.of(
                "ts=" + TS,
                "v1=" + FIRMA,
                "ts=" + TS + ",v1=" + FIRMA + ",ts=" + TS,
                "ts=" + TS + ",v1=" + FIRMA + ",v1=" + FIRMA,
                "ts=,ts=" + TS + ",v1=" + FIRMA,
                "ts=" + TS + ",v1=,v1=" + FIRMA,
                "ts=" + TS + ",v2=" + FIRMA,
                SIGNATURE + ",otro=valor",
                SIGNATURE + ",",
                "," + SIGNATURE,
                "ts=" + TS + ",,v1=" + FIRMA,
                "ts:" + TS + ",v1=" + FIRMA,
                "ts=" + TS + ";v1=" + FIRMA,
                "ts=abc,v1=" + FIRMA,
                "ts=-1,v1=" + FIRMA,
                "ts=1.5,v1=" + FIRMA,
                "ts=,v1=" + FIRMA,
                "ts=" + TS + ",v1=",
                "TS=" + TS + ",v1=" + FIRMA,
                "ts=" + TS + ",v1=" + "g".repeat(64),
                "ts=" + TS + ",v1=" + FIRMA.substring(1),
                "ts=" + TS + ",v1=" + FIRMA + "0",
                "ts=" + TS + ",v1=0x" + FIRMA,
                "ts=" + TS + ",v1=" + FIRMA + "=",
                SIGNATURE + "\r\n",
                "ts=" + TS + ",\nv1=" + FIRMA);
    }
}
