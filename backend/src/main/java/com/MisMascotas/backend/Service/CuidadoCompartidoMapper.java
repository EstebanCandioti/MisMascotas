package com.MisMascotas.backend.Service;

import java.time.Instant;
import java.util.Set;
import com.MisMascotas.backend.DTO.CuidadoCompartidoDTO.*;
import com.MisMascotas.backend.Entity.*;
import com.MisMascotas.backend.Exception.CuidadoCompartidoException;

final class CuidadoCompartidoMapper {
    private CuidadoCompartidoMapper() {}
    private static final Set<String> ESTADOS = Set.of("PENDIENTE", "ACEPTADA", "RECHAZADA", "CANCELADA", "EXPIRADA");

    static String estado(Invitacion invitacion, Instant ahora) {
        var estado = invitacion.getEstado();
        if (estado == null || !"INVITACION".equals(estado.getEntidad()) || !ESTADOS.contains(estado.getNombre()))
            throw new CuidadoCompartidoException("INVITACION_REQUIERE_REVISION", "La invitacion requiere revision");
        return "PENDIENTE".equals(estado.getNombre()) && !invitacion.getExpiraEn().isAfter(ahora)
                ? "EXPIRADA" : estado.getNombre();
    }

    static InvitacionDetalle invitacion(Invitacion i, Instant ahora) {
        return new InvitacionDetalle(i.getIdInvitacion(), i.getPropietario().getIdUsuario(), i.getPropietario().getNombre(),
                i.getEmailInvitado(), i.getPerfil(), estado(i, ahora), i.getCreadoEn(), i.getExpiraEn());
    }

    static AccesoDetalle acceso(AccesoCompartido a) {
        return new AccesoDetalle(a.getIdAcceso(), a.getPropietario().getIdUsuario(), a.getPropietario().getNombre(),
                a.getInvitado().getIdUsuario(), a.getInvitado().getNombre(), a.getInvitado().getEmail(),
                a.getPerfil(), a.isActivo(), a.getFechaIngreso());
    }
}
