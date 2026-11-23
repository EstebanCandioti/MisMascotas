package com.MisMascotas.backend.Service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcesadorEventosAuditoriaProgramadoTests {

    @Mock
    private ProcesadorEventoAuditoriaService procesador;

    @Test
    void unFalloQuedaRegistradoYElCicloSiguienteReintentaElEvento() {
        UUID eventoId = UUID.randomUUID();
        when(procesador.buscarIdsPendientes(10)).thenReturn(List.of(eventoId));
        doThrow(new IllegalStateException("log no disponible"))
                .doReturn(ProcesadorEventoAuditoriaService.ResultadoProcesamiento.PROCESADO)
                .when(procesador)
                .procesar(eventoId);
        when(procesador.registrarFallo(
                org.mockito.ArgumentMatchers.eq(eventoId),
                org.mockito.ArgumentMatchers.any(IllegalStateException.class)))
                .thenReturn(1);
        ProcesadorEventosAuditoriaProgramado programado =
                new ProcesadorEventosAuditoriaProgramado(procesador, 10);

        programado.procesarPendientes();
        programado.procesarPendientes();

        InOrder orden = inOrder(procesador);
        orden.verify(procesador).procesar(eventoId);
        orden.verify(procesador).registrarFallo(
                org.mockito.ArgumentMatchers.eq(eventoId),
                org.mockito.ArgumentMatchers.any(IllegalStateException.class));
        orden.verify(procesador).procesar(eventoId);
        verify(procesador, org.mockito.Mockito.times(2)).buscarIdsPendientes(10);
    }
}
