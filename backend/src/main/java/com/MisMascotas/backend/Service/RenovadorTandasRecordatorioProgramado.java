package com.MisMascotas.backend.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.MisMascotas.backend.Recordatorios.CalculadorOcurrencias;
import com.MisMascotas.backend.Repository.RecordatorioRepository;
import com.MisMascotas.backend.Repository.RecordatorioRepository.SerieParaRenovar;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "recordatorios.tandas.scheduler-habilitado", havingValue = "true", matchIfMissing = true)
public class RenovadorTandasRecordatorioProgramado {

    private static final Logger logger = LoggerFactory.getLogger(RenovadorTandasRecordatorioProgramado.class);
    private static final int TAMANO_LOTE = 100;

    private final RecordatorioRepository recordatorios;
    private final RecordatorioService servicio;
    private final Clock reloj;

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 0 0 * * *", zone = CalculadorOcurrencias.ID_ZONA)
    public void ejecutar() {
        try {
            renovarPendientes(reloj.instant().atZone(CalculadorOcurrencias.ZONA).toLocalDate());
        } catch (RuntimeException error) {
            logger.error("No se pudieron consultar las tandas pendientes de recordatorios", error);
        }
    }

    private void renovarPendientes(LocalDate hoy) {
        LocalDate fechaCursor = null;
        UUID idCursor = null;
        List<SerieParaRenovar> lote;
        do {
            lote = recordatorios.buscarSeriesParaRenovar(hoy, fechaCursor, idCursor, TAMANO_LOTE);
            for (var serie : lote) {
                idCursor = serie.getIdRecordatorio();
                fechaCursor = serie.getUltimaFechaGenerada();
                try {
                    servicio.renovarTanda(idCursor, hoy);
                } catch (RuntimeException error) {
                    logger.error("No se pudo renovar la tanda del recordatorio {}", idCursor, error);
                }
            }
        } while (lote.size() == TAMANO_LOTE);
    }
}
