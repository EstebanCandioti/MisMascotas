package com.MisMascotas.backend.Service;

import static com.MisMascotas.backend.Service.CuidadoCompartidoMapper.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import com.MisMascotas.backend.DTO.CuidadoCompartidoDTO.*;
import com.MisMascotas.backend.Entity.*;
import com.MisMascotas.backend.Exception.*;
import com.MisMascotas.backend.Repository.*;

/** Escrituras del vínculo: propietario primero, recurso después y auditoría en la misma transacción. */
@Service
@RequiredArgsConstructor
@Transactional
public class CuidadoCompartidoService {
    private static final Duration VIGENCIA = Duration.ofHours(48);
    private final UsuarioRepository usuarios;
    private final MascotaRepository mascotas;
    private final InvitacionRepository invitaciones;
    private final AccesoCompartidoRepository accesos;
    private final EstadoRepository estados;
    private final CupoCuidadoService cupos;
    private final EventoAuditoriaPendienteService auditoria;
    private final EntityManager entidades;
    private final Clock reloj;
    private final ApplicationEventPublisher eventos;

    public InvitacionDetalle crear(UUID actorId, NuevaInvitacion solicitud) {
        var propietario = bloquearPropietario(actorId);
        exigirActorActivo(propietario);
        return crearBloqueada(propietario, solicitud, reloj.instant());
    }

    public Aceptacion aceptar(UUID actorId, UUID id) {
        var actor = actor(actorId);
        var i = bloquearInvitacion(actor, id, false);
        var ahora = reloj.instant();
        if ("ACEPTADA".equals(estado(i, ahora))) {
            var anterior = i.getAccesoCompartido();
            if (anterior == null || !anterior.getPropietario().getIdUsuario().equals(i.getPropietario().getIdUsuario())
                    || !anterior.getInvitado().getIdUsuario().equals(actorId))
                throw conflicto("INVITACION_REQUIERE_REVISION", "La aceptacion historica requiere revision");
            entidades.refresh(anterior);
            return new Aceptacion(invitacion(i, ahora), acceso(anterior));
        }
        exigirPendiente(i, ahora);
        if (!i.getPropietario().isActivo()) throw conflicto("PROPIETARIO_INACTIVO", "El propietario no esta activo");
        var propietarioId = i.getPropietario().getIdUsuario();
        if (accesos.findByPropietario_IdUsuarioAndInvitado_IdUsuarioAndActivoTrue(propietarioId, actorId).isPresent())
            throw conflicto("ACCESO_YA_EXISTENTE", "La persona ya tiene un acceso activo");
        cupos.exigirLugar(propietarioId, ahora, true);
        var antes = invitacion(i, ahora);
        var nuevo = accesos.saveAndFlush(AccesoCompartido.builder().propietario(i.getPropietario())
                .invitado(actor).perfil(i.getPerfil()).fechaIngreso(ahora).activo(true).build());
        i.setAccesoCompartido(nuevo);
        i.setEstado(estadoCatalogo("ACEPTADA"));
        invitaciones.saveAndFlush(i);
        var despues = invitacion(i, ahora);
        auditar(actorId, "acceso_compartido", TipoAccionAuditoria.SHARE, nuevo.getIdAcceso(), null, acceso(nuevo), "ACEPTACION");
        auditar(actorId, "invitacion", TipoAccionAuditoria.UPDATE, id, antes, despues, "ACEPTACION");
        notificar(i, NotificacionCuidado.Tipo.ACEPTADA, actor);
        return new Aceptacion(despues, acceso(nuevo));
    }

    public InvitacionDetalle rechazar(UUID actorId, UUID id) {
        var i = bloquearInvitacion(actor(actorId), id, false);
        return cerrarInvitacion(actorId, i, "RECHAZADA", reloj.instant(), "RECHAZO");
    }

    public InvitacionDetalle cancelar(UUID actorId, UUID id) {
        var i = bloquearInvitacion(actor(actorId), id, true);
        return cerrarInvitacion(actorId, i, "CANCELADA", reloj.instant(), "CANCELACION");
    }

    public InvitacionDetalle reenviar(UUID actorId, UUID id) {
        var i = bloquearInvitacion(actor(actorId), id, true);
        var ahora = reloj.instant();
        var anteriorEstado = estado(i, ahora);
        if ("ACEPTADA".equals(anteriorEstado))
            throw conflicto("INVITACION_NO_PENDIENTE", "Crea una nueva invitacion para volver a compartir el cuidado");
        // La reserva anterior solo se cierra si la nueva creación también puede confirmarse.
        if ("PENDIENTE".equals(i.getEstado().getNombre())) {
            var antes = invitacion(i, ahora);
            i.setEstado(estadoCatalogo("EXPIRADA".equals(anteriorEstado) ? "EXPIRADA" : "CANCELADA"));
            invitaciones.saveAndFlush(i);
            auditar(actorId, "invitacion", TipoAccionAuditoria.UPDATE, id, antes, invitacion(i, ahora), "REENVIO");
        }
        return crearBloqueada(i.getPropietario(), new NuevaInvitacion(i.getEmailInvitado(), i.getPerfil()), ahora);
    }

    public AccesoDetalle cambiarPerfil(UUID actorId, UUID id, CambiarPerfil solicitud) {
        var a = bloquearAcceso(actor(actorId), id, true);
        if (!a.isActivo()) throw conflicto("ACCESO_INACTIVO", "El acceso ya no esta activo");
        if (a.getPerfil() != solicitud.perfil()) {
            var antes = acceso(a);
            a.setPerfil(solicitud.perfil()); accesos.saveAndFlush(a);
            auditar(actorId, "acceso_compartido", TipoAccionAuditoria.UPDATE, id, antes, acceso(a), "CAMBIO_PERFIL");
        }
        return acceso(a);
    }

    public AccesoDetalle revocar(UUID actorId, UUID id) {
        return desactivar(actorId, bloquearAcceso(actor(actorId), id, true), "REVOCACION_PROPIETARIO");
    }

    public AccesoDetalle salir(UUID actorId, UUID id) {
        return desactivar(actorId, bloquearAcceso(actor(actorId), id, false), "SALIDA_INVITADO");
    }

    private InvitacionDetalle crearBloqueada(Usuario propietario, NuevaInvitacion solicitud, Instant ahora) {
        if (!propietario.isActivo()) throw conflicto("PROPIETARIO_INACTIVO", "El propietario no esta activo");
        var propietarioId = propietario.getIdUsuario();
        if (mascotas.countByPropietario_IdUsuarioAndFechaEliminacionIsNull(propietarioId) == 0)
            throw conflicto("PROPIETARIO_SIN_MASCOTAS", "Debes tener al menos una mascota activa para invitar");
        var destinatario = usuarios.findByEmail(solicitud.emailInvitado());
        if (destinatario == null || !destinatario.isActivo())
            throw new RecursoNoEncontradoException("La cuenta destinataria no esta disponible");
        if (destinatario.getIdUsuario().equals(propietarioId)) throw conflicto("AUTO_INVITACION", "No puedes invitarte a tu propio cuidado");
        if (accesos.findByPropietario_IdUsuarioAndInvitado_IdUsuarioAndActivoTrue(propietarioId, destinatario.getIdUsuario()).isPresent())
            throw conflicto("ACCESO_YA_EXISTENTE", "La persona ya tiene un acceso activo");
        if (invitaciones.existePendienteVigente(propietarioId, solicitud.emailInvitado(), ahora))
            throw conflicto("INVITACION_YA_PENDIENTE", "Ya existe una invitacion pendiente para esta persona");
        cupos.exigirLugar(propietarioId, ahora, false);
        var i = invitaciones.saveAndFlush(Invitacion.builder().propietario(propietario)
                .emailInvitado(solicitud.emailInvitado()).perfil(solicitud.perfil()).estado(estadoCatalogo("PENDIENTE"))
                .creadoEn(ahora).expiraEn(ahora.plus(VIGENCIA)).build());
        var resultado = invitacion(i, ahora);
        auditar(propietarioId, "invitacion", TipoAccionAuditoria.CREATE, i.getIdInvitacion(), null, resultado, "INVITACION");
        notificar(i, NotificacionCuidado.Tipo.INVITACION, destinatario);
        return resultado;
    }

    private InvitacionDetalle cerrarInvitacion(UUID actorId, Invitacion i, String destino, Instant ahora, String origen) {
        if (destino.equals(estado(i, ahora))) return invitacion(i, ahora);
        exigirPendiente(i, ahora);
        var antes = invitacion(i, ahora);
        i.setEstado(estadoCatalogo(destino)); invitaciones.saveAndFlush(i);
        var despues = invitacion(i, ahora);
        auditar(actorId, "invitacion", TipoAccionAuditoria.UPDATE, i.getIdInvitacion(), antes, despues, origen);
        if ("RECHAZADA".equals(destino)) notificar(i, NotificacionCuidado.Tipo.RECHAZADA, usuarios.getReferenceById(actorId));
        return despues;
    }

    private AccesoDetalle desactivar(UUID actorId, AccesoCompartido a, String origen) {
        if (a.isActivo()) {
            var antes = acceso(a);
            a.setActivo(false); accesos.saveAndFlush(a);
            auditar(actorId, "acceso_compartido", TipoAccionAuditoria.UNSHARE, a.getIdAcceso(), antes, acceso(a), origen);
        }
        return acceso(a);
    }

    private Invitacion bloquearInvitacion(Usuario actor, UUID id, boolean comoPropietario) {
        var inicial = invitaciones.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Invitacion no encontrada"));
        verificarActorInvitacion(actor, inicial, comoPropietario);
        bloquearPropietario(inicial.getPropietario().getIdUsuario());
        var actual = invitaciones.findByIdConBloqueo(id).orElseThrow(() -> new RecursoNoEncontradoException("Invitacion no encontrada"));
        entidades.refresh(actual, LockModeType.PESSIMISTIC_WRITE);
        verificarActorInvitacion(actor, actual, comoPropietario);
        return actual;
    }

    private AccesoCompartido bloquearAcceso(Usuario actor, UUID id, boolean comoPropietario) {
        var inicial = accesos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Acceso no encontrado"));
        verificarActorAcceso(actor, inicial, comoPropietario);
        bloquearPropietario(inicial.getPropietario().getIdUsuario());
        var actual = accesos.findByIdConBloqueo(id).orElseThrow(() -> new RecursoNoEncontradoException("Acceso no encontrado"));
        entidades.refresh(actual, LockModeType.PESSIMISTIC_WRITE);
        verificarActorAcceso(actor, actual, comoPropietario);
        return actual;
    }

    private Usuario bloquearPropietario(UUID id) {
        var usuario = usuarios.findByIdConBloqueo(id).orElseThrow(() -> new RecursoNoEncontradoException("Propietario no encontrado"));
        entidades.refresh(usuario, LockModeType.PESSIMISTIC_WRITE);
        return usuario;
    }

    private Usuario actor(UUID id) {
        var actor = usuarios.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
        entidades.refresh(actor);
        exigirActorActivo(actor);
        return actor;
    }

    private void exigirActorActivo(Usuario actor) {
        if (!actor.isActivo()) throw new BadCredentialsException("La cuenta no esta activa");
    }

    private void verificarActorInvitacion(Usuario actor, Invitacion i, boolean comoPropietario) {
        boolean permitido = comoPropietario ? actor.getIdUsuario().equals(i.getPropietario().getIdUsuario())
                : actor.getEmail().equals(i.getEmailInvitado());
        if (!permitido) throw new AccesoDenegadoException("No tienes permisos para realizar esta accion sobre la invitacion");
    }

    private void verificarActorAcceso(Usuario actor, AccesoCompartido a, boolean comoPropietario) {
        var permitido = comoPropietario ? a.getPropietario().getIdUsuario() : a.getInvitado().getIdUsuario();
        if (!actor.getIdUsuario().equals(permitido)) throw new AccesoDenegadoException("No tienes permisos para realizar esta accion sobre el acceso");
    }

    private void exigirPendiente(Invitacion i, Instant ahora) {
        var estado = estado(i, ahora);
        if ("EXPIRADA".equals(estado)) throw CuidadoCompartidoException.invitacionExpirada();
        if (!"PENDIENTE".equals(estado)) throw conflicto("INVITACION_NO_PENDIENTE", "La invitacion ya fue resuelta");
    }

    private Estado estadoCatalogo(String nombre) {
        return estados.findByEntidadAndNombre("INVITACION", nombre)
                .orElseThrow(() -> new EstadoNoConfiguradoException("INVITACION", nombre));
    }

    private CuidadoCompartidoException conflicto(String codigo, String mensaje) {
        return new CuidadoCompartidoException(codigo, mensaje);
    }

    private void auditar(UUID actor, String entidad, TipoAccionAuditoria accion, UUID id, Object antes, Object despues, String origen) {
        auditoria.encolar(actor, entidad, accion, id, antes == null ? null : new Cambio(origen, antes), new Cambio(origen, despues));
    }

    private record Cambio(String origen, Object detalle) {}

    private void notificar(Invitacion i, NotificacionCuidado.Tipo tipo, Usuario invitado) {
        eventos.publishEvent(new NotificacionCuidado(tipo, i.getIdInvitacion(),
                tipo == NotificacionCuidado.Tipo.INVITACION ? i.getEmailInvitado() : i.getPropietario().getEmail(),
                i.getPropietario().getNombre(), invitado.getNombre(), i.getPerfil(), i.getExpiraEn()));
    }
}
