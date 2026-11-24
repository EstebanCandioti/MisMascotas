package com.MisMascotas.backend.Service;

import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.MisMascotas.backend.DTO.CancelarSuscripcionResponseDTO;
import com.MisMascotas.backend.DTO.CancelarSuscripcionResponseDTO.Resultado;
import com.MisMascotas.backend.Integration.MercadoPagoGateway;
import com.MisMascotas.backend.Integration.MercadoPagoGateway.PreapprovalConsultado;
import com.MisMascotas.backend.Service.SuscripcionCancelacionTransaccionalService.Candidata;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SuscripcionCancelacionService {
    private static final Logger logger = LoggerFactory.getLogger(SuscripcionCancelacionService.class);
    private final MercadoPagoGateway proveedor;
    private final SuscripcionCancelacionTransaccionalService transacciones;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CancelarSuscripcionResponseDTO cancelar(UUID usuarioId, UUID id) {
        var candidata = transacciones.buscar(usuarioId, id);
        if (!candidata.elegible()) return CancelarSuscripcionResponseDTO.sinConfirmar(id, Resultado.NO_CANCELABLE);
        if (candidata.idMercadoPago() == null || candidata.idMercadoPago().isBlank())
            return CancelarSuscripcionResponseDTO.sinConfirmar(id, Resultado.REQUIERE_CONCILIACION);
        PreapprovalConsultado remoto;
        try {
            remoto = proveedor.consultarPreapproval(candidata.idMercadoPago());
            if (!identidad(candidata, remoto))
                return CancelarSuscripcionResponseDTO.sinConfirmar(id, Resultado.REQUIERE_CONCILIACION);
            if (!cancelada(remoto)) {
                if (remoto.estado() == null || !Set.of("authorized", "paused").contains(remoto.estado()))
                    return CancelarSuscripcionResponseDTO.sinConfirmar(id, Resultado.REQUIERE_CONCILIACION);
                try {
                    proveedor.cancelarPreapproval(candidata.idMercadoPago());
                } catch (RuntimeException ex) {
                    // Un timeout no prueba que el PUT haya fallado: decide el GET posterior.
                    logger.warn("Cancelacion remota sin respuesta para {} por {}; se verificara", id, ex.getClass().getSimpleName());
                }
                remoto = proveedor.consultarPreapproval(candidata.idMercadoPago());
                if (!identidad(candidata, remoto))
                    return CancelarSuscripcionResponseDTO.sinConfirmar(id, Resultado.REQUIERE_CONCILIACION);
                if (!cancelada(remoto))
                    return CancelarSuscripcionResponseDTO.sinConfirmar(id, Resultado.CANCELACION_NO_CONFIRMADA);
            }
        } catch (RuntimeException ex) {
            logger.warn("No se pudo verificar cancelacion de {} por {}", id, ex.getClass().getSimpleName());
            return CancelarSuscripcionResponseDTO.sinConfirmar(id, Resultado.CANCELACION_NO_CONFIRMADA);
        }
        try {
            return transacciones.confirmar(usuarioId, candidata, remoto);
        } catch (RuntimeException ex) {
            logger.warn("Cancelacion remota confirmada sin cierre local de {} por {}", id, ex.getClass().getSimpleName());
            return CancelarSuscripcionResponseDTO.sinConfirmar(id, Resultado.REQUIERE_CONCILIACION);
        }
    }

    private boolean identidad(Candidata local, PreapprovalConsultado remoto) {
        return remoto != null && local.idMercadoPago().equals(remoto.id())
                && local.idSuscripcion().toString().equals(remoto.referenciaExterna());
    }
    private boolean cancelada(PreapprovalConsultado remoto) {
        return "cancelled".equals(remoto.estado()) || "canceled".equals(remoto.estado());
    }
}
