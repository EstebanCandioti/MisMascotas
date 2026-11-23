package com.MisMascotas.backend.Service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "auditoria.pendientes.procesamiento-habilitado",
        havingValue = "true",
        matchIfMissing = true)
public class ProcesadorEventosAuditoriaProgramado {

    private static final Logger logger = LoggerFactory.getLogger(ProcesadorEventosAuditoriaProgramado.class);
    private static final int UMBRAL_FALLO_PERSISTENTE = 3;

    private final ProcesadorEventoAuditoriaService procesador;
    private final int tamanoLote;

    public ProcesadorEventosAuditoriaProgramado(
            ProcesadorEventoAuditoriaService procesador,
            @Value("${auditoria.pendientes.tamano-lote:100}") int tamanoLote) {
        this.procesador = procesador;
        this.tamanoLote = tamanoLote;
    }

    @Scheduled(
            fixedDelayString = "${auditoria.pendientes.intervalo-ms:5000}",
            initialDelayString = "${auditoria.pendientes.demora-inicial-ms:5000}")
    public void procesarPendientes() {
        for (UUID idEvento : procesador.buscarIdsPendientes(tamanoLote)) {
            try {
                procesador.procesar(idEvento);
            } catch (RuntimeException ex) {
                registrarFallo(idEvento, ex);
            }
        }
    }

    private void registrarFallo(UUID idEvento, RuntimeException error) {
        try {
            int intentos = procesador.registrarFallo(idEvento, error);
            if (intentos >= UMBRAL_FALLO_PERSISTENTE) {
                logger.error(
                        "Fallo persistente al procesar evento de auditoria {} tras {} intentos: {}",
                        idEvento,
                        intentos,
                        error.getMessage());
            } else if (intentos > 0) {
                logger.warn(
                        "No se pudo procesar evento de auditoria {} (intento {}): {}",
                        idEvento,
                        intentos,
                        error.getMessage());
            }
        } catch (RuntimeException registroError) {
            logger.error(
                    "No se pudo registrar el fallo del evento de auditoria {}: {}",
                    idEvento,
                    registroError.getMessage());
        }
    }
}
