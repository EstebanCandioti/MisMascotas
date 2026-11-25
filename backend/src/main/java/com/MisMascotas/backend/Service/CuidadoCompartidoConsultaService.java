package com.MisMascotas.backend.Service;

import static com.MisMascotas.backend.Service.CuidadoCompartidoMapper.*;
import java.time.Clock;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import com.MisMascotas.backend.DTO.CuidadoCompartidoDTO.*;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Exception.*;
import com.MisMascotas.backend.Repository.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CuidadoCompartidoConsultaService {
    private final UsuarioRepository usuarios;
    private final InvitacionRepository invitaciones;
    private final AccesoCompartidoRepository accesos;
    private final CupoCuidadoService cupos;
    private final Clock reloj;

    public Bandeja consultar(UUID actorId) {
        var actor = actor(actorId);
        var ahora = reloj.instant();
        return new Bandeja(
                invitaciones.findByPropietario_IdUsuarioOrderByCreadoEnDesc(actorId).stream().map(i -> invitacion(i, ahora)).toList(),
                invitaciones.findByEmailInvitadoOrderByCreadoEnDesc(actor.getEmail()).stream().map(i -> invitacion(i, ahora)).toList(),
                accesos.findByPropietario_IdUsuarioAndActivoTrueOrderByFechaIngresoDesc(actorId).stream().map(CuidadoCompartidoMapper::acceso).toList(),
                accesos.findByInvitado_IdUsuarioAndActivoTrueOrderByFechaIngresoDesc(actorId).stream().map(CuidadoCompartidoMapper::acceso).toList(),
                cupos.consultar(actorId, ahora));
    }

    public InvitacionDetalle consultarInvitacion(UUID actorId, UUID id) {
        var actor = actor(actorId);
        var i = invitaciones.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Invitacion no encontrada"));
        if (!actorId.equals(i.getPropietario().getIdUsuario()) && !actor.getEmail().equals(i.getEmailInvitado()))
            throw new AccesoDenegadoException("No tienes acceso a esta invitacion");
        return invitacion(i, reloj.instant());
    }

    public AccesoDetalle consultarAcceso(UUID actorId, UUID id) {
        actor(actorId);
        var a = accesos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Acceso no encontrado"));
        if (!actorId.equals(a.getPropietario().getIdUsuario()) && !actorId.equals(a.getInvitado().getIdUsuario()))
            throw new AccesoDenegadoException("No tienes acceso a este vinculo");
        return acceso(a);
    }

    private Usuario actor(UUID id) {
        var usuario = usuarios.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
        if (!usuario.isActivo()) throw new BadCredentialsException("La cuenta no esta activa");
        return usuario;
    }
}
