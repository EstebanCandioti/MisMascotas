package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.MisMascotas.backend.DTO.CancelarSuscripcionResponseDTO;
import com.MisMascotas.backend.DTO.CancelarSuscripcionResponseDTO.Resultado;
import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Integration.MercadoPagoGateway;
import com.MisMascotas.backend.Integration.MercadoPagoGateway.PreapprovalConsultado;
import com.MisMascotas.backend.Service.SuscripcionCancelacionTransaccionalService.Candidata;

class SuscripcionCancelacionServiceTests {
    final MercadoPagoGateway proveedor = mock(MercadoPagoGateway.class);
    final SuscripcionCancelacionTransaccionalService transacciones = mock(SuscripcionCancelacionTransaccionalService.class);
    final SuscripcionCancelacionService servicio = new SuscripcionCancelacionService(proveedor, transacciones);
    final UUID uid = UUID.randomUUID(), sid = UUID.randomUUID();
    final Candidata candidata = new Candidata(sid, "mp-qa", "PREMIUM_MENSUAL", true, null);
    @BeforeEach void preparar() { when(transacciones.buscar(uid, sid)).thenReturn(candidata); }

    @ParameterizedTest @ValueSource(strings = {"authorized", "paused"})
    void confirmaPorGetLuegoDelPut(String inicial) {
        var cancelada = remoto("cancelled");
        when(proveedor.consultarPreapproval("mp-qa")).thenReturn(remoto(inicial), cancelada);
        when(transacciones.confirmar(uid, candidata, cancelada)).thenReturn(
                new CancelarSuscripcionResponseDTO(sid, Resultado.CANCELADA, "CANCELADA", null, null));
        assertThat(servicio.cancelar(uid, sid).resultado()).isEqualTo(Resultado.CANCELADA);
        var orden = inOrder(proveedor, transacciones);
        orden.verify(transacciones).buscar(uid, sid);
        orden.verify(proveedor).consultarPreapproval("mp-qa");
        orden.verify(proveedor).cancelarPreapproval("mp-qa");
        orden.verify(proveedor).consultarPreapproval("mp-qa");
        orden.verify(transacciones).confirmar(uid, candidata, cancelada);
    }

    @Test void timeoutDelPutIgualmenteConsultaElResultadoRemoto() {
        when(proveedor.consultarPreapproval("mp-qa")).thenReturn(remoto("authorized"), remoto("cancelled"));
        doThrow(new IllegalStateException("Timeout simulado")).when(proveedor).cancelarPreapproval("mp-qa");
        servicio.cancelar(uid, sid);
        verify(proveedor, times(2)).consultarPreapproval("mp-qa");
        verify(transacciones).confirmar(eq(uid), eq(candidata), any());
    }

    @ParameterizedTest @ValueSource(strings = {"cancelled", "canceled"})
    void yaCanceladaEnProveedorNoEnviaOtroPut(String estado) {
        when(proveedor.consultarPreapproval("mp-qa")).thenReturn(remoto(estado));
        servicio.cancelar(uid, sid);
        verify(proveedor, never()).cancelarPreapproval(any());
        verify(transacciones).confirmar(eq(uid), eq(candidata), any());
    }

    @Test void respuestaAunActivaNoCambiaLaBase() {
        when(proveedor.consultarPreapproval("mp-qa")).thenReturn(remoto("authorized"));
        assertThat(servicio.cancelar(uid, sid).resultado()).isEqualTo(Resultado.CANCELACION_NO_CONFIRMADA);
        verify(transacciones, never()).confirmar(any(), any(), any());
    }

    @Test void errorDelGetNoSolicitaCancelacionNiCambiaLaBase() {
        when(proveedor.consultarPreapproval("mp-qa")).thenThrow(new IllegalStateException("Fallo simulado"));
        assertThat(servicio.cancelar(uid, sid).resultado()).isEqualTo(Resultado.CANCELACION_NO_CONFIRMADA);
        verify(proveedor, never()).cancelarPreapproval(any());
        verify(transacciones, never()).confirmar(any(), any(), any());
    }

    @ParameterizedTest @ValueSource(strings = {"pending", "desconocido"})
    void estadoRemotoNoAdmitidoNoSeCancela(String estado) {
        when(proveedor.consultarPreapproval("mp-qa")).thenReturn(remoto(estado));
        assertThat(servicio.cancelar(uid, sid).resultado()).isEqualTo(Resultado.REQUIERE_CONCILIACION);
        verify(proveedor, never()).cancelarPreapproval(any());
    }

    @Test void identidadRemotaAjenaNoSeCancela() {
        when(proveedor.consultarPreapproval("mp-qa")).thenReturn(new PreapprovalConsultado(
                "mp-ajeno", UUID.randomUUID().toString(), "authorized", null, null, null, 1, "months"));
        assertThat(servicio.cancelar(uid, sid).resultado()).isEqualTo(Resultado.REQUIERE_CONCILIACION);
        verify(proveedor, never()).cancelarPreapproval(any());
    }

    @Test void reservaPendienteOReemplazadaNoLlamaAlProveedor() {
        when(transacciones.buscar(uid, sid)).thenReturn(new Candidata(sid, "mp-qa", "PREMIUM_MENSUAL", false, null));
        assertThat(servicio.cancelar(uid, sid).resultado()).isEqualTo(Resultado.NO_CANCELABLE);
        verifyNoInteractions(proveedor);
    }

    @Test void sinIdRemotoRequiereConciliacion() {
        when(transacciones.buscar(uid, sid)).thenReturn(new Candidata(sid, null, "PREMIUM_MENSUAL", true, null));
        assertThat(servicio.cancelar(uid, sid).resultado()).isEqualTo(Resultado.REQUIERE_CONCILIACION);
        verifyNoInteractions(proveedor);
    }

    @Test void ajenaOInexistenteDevuelveErrorAntesDeConsultarMP() {
        when(transacciones.buscar(uid, sid)).thenThrow(new RecursoNoEncontradoException("Suscripcion no encontrada"));
        assertThatThrownBy(() -> servicio.cancelar(uid, sid)).isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(proveedor);
    }

    @Test void cancelacionRemotaSinCierreLocalNoSeAnunciaComoTerminada() {
        when(proveedor.consultarPreapproval("mp-qa")).thenReturn(remoto("cancelled"));
        when(transacciones.confirmar(any(), any(), any())).thenThrow(new IllegalStateException("Rollback simulado"));
        assertThat(servicio.cancelar(uid, sid).resultado()).isEqualTo(Resultado.REQUIERE_CONCILIACION);
    }

    private PreapprovalConsultado remoto(String estado) {
        return new PreapprovalConsultado("mp-qa", sid.toString(), estado, null, null, null, 1, "months");
    }
}
