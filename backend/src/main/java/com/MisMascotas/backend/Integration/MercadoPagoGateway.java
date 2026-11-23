package com.MisMascotas.backend.Integration;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface MercadoPagoGateway {

    void validarConfiguracion();

    PreapprovalCreado crearPreapproval(SolicitudPreapproval solicitud);

    PreapprovalConsultado consultarPreapproval(String idPreapproval);

    void cancelarPreapproval(String idPreapproval);

    List<CuotaConsultada> buscarCuotas(String idPreapproval);

    PagoConsultado consultarPago(String idPago);

    default FacturaAutorizada consultarFacturaAutorizada(String idFactura) {
        throw new UnsupportedOperationException("La consulta de factura no esta implementada");
    }

    record CuotaConsultada(String idPreapproval, String idPago) {
    }

    record PagoConsultado(String id, String estado, BigDecimal importe, String moneda, Instant fechaAprobacion,
            String detalleEstado) {
        public PagoConsultado(String id, String estado, BigDecimal importe, String moneda, Instant fechaAprobacion) {
            this(id, estado, importe, moneda, fechaAprobacion, null);
        }
    }

    /** Factura de una cuota; su id no es el id del pago asociado. */
    record FacturaAutorizada(String id, String idPreapproval, String referenciaExterna, String idPago,
            String estado, Integer intento, Instant fechaDebito, Instant proximoReintento,
            BigDecimal importe, String moneda) {
        public FacturaAutorizada(String id, String idPreapproval, String referenciaExterna, String idPago) {
            this(id, idPreapproval, referenciaExterna, idPago, null, null, null, null, null, null);
        }
        public FacturaAutorizada(String id, String idPreapproval, String referenciaExterna) {
            this(id, idPreapproval, referenciaExterna, null);
        }
    }

    record SolicitudPreapproval(
            String motivo,
            String referenciaExterna,
            String emailPagador,
            BigDecimal importe,
            String moneda,
            int frecuencia,
            String tipoFrecuencia) {
    }

    record PreapprovalCreado(String id, String initPoint) {
    }

    record PreapprovalConsultado(
            String id,
            String referenciaExterna,
            String estado,
            String initPoint,
            BigDecimal importe,
            String moneda,
            int frecuencia,
            String tipoFrecuencia,
            Instant proximoPago, Instant ultimaModificacion) {
        public PreapprovalConsultado(String id, String referenciaExterna, String estado, String initPoint,
                BigDecimal importe, String moneda, int frecuencia, String tipoFrecuencia) {
            this(id, referenciaExterna, estado, initPoint, importe, moneda, frecuencia, tipoFrecuencia, null, null);
        }
    }
}
