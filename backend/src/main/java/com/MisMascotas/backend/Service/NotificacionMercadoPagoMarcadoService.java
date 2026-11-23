package com.MisMascotas.backend.Service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.MisMascotas.backend.Repository.NotificacionMercadoPagoRepository;

@Service
public class NotificacionMercadoPagoMarcadoService {
    private final NotificacionMercadoPagoRepository notificaciones;

    public NotificacionMercadoPagoMarcadoService(NotificacionMercadoPagoRepository notificaciones) {
        this.notificaciones = notificaciones;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean marcarProcesada(String idNotificacion) {
        return notificaciones.marcarProcesada(idNotificacion);
    }
}
