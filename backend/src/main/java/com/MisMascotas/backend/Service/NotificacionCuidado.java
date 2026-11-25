package com.MisMascotas.backend.Service;

import java.time.Instant;
import java.util.UUID;
import com.MisMascotas.backend.Entity.PerfilCompartido;

/** Datos planos capturados dentro de la transacción; el envío no vuelve a consultar JPA. */
public record NotificacionCuidado(Tipo tipo, UUID invitacionId, String emailDestino,
        String nombrePropietario, String nombreInvitado, PerfilCompartido perfil, Instant expiraEn) {
    public enum Tipo { INVITACION, ACEPTADA, RECHAZADA }
}
