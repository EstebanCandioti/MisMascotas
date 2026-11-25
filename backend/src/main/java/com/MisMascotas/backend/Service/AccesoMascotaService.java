package com.MisMascotas.backend.Service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.MisMascotas.backend.Entity.Mascota;
import com.MisMascotas.backend.Entity.PerfilCompartido;
import com.MisMascotas.backend.Exception.AccesoDenegadoException;
import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Repository.MascotaRepository;
import com.MisMascotas.backend.Repository.AccesoCompartidoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccesoMascotaService {

    private final MascotaRepository mascotaRepository;
    private final AccesoCompartidoRepository accesos;

    @Transactional(readOnly = true)
    public Mascota verificarAcceso(UUID mascotaId, UUID usuarioAutenticadoId) {
        Mascota mascota = mascotaRepository.findByIdMascotaAndFechaEliminacionIsNull(mascotaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mascota no encontrada con ID: " + mascotaId));

        verificarAcceso(mascota, usuarioAutenticadoId);
        return mascota;
    }

    public void verificarAcceso(Mascota mascota, UUID usuarioAutenticadoId) {
        if (mascota == null || mascota.getFechaEliminacion() != null) {
            throw new RecursoNoEncontradoException("Mascota no encontrada");
        }

        UUID propietarioId = mascota.getPropietario() != null
                ? mascota.getPropietario().getIdUsuario()
                : null;

        if (!usuarioAutenticadoId.equals(propietarioId)) {
            throw new AccesoDenegadoException("No tenes permisos para realizar esta accion sobre esta mascota");
        }
    }

    @Transactional(readOnly = true)
    public List<Mascota> obtenerMascotasAccesibles(UUID usuarioAutenticadoId) {
        return mascotaRepository.findAccesibles(usuarioAutenticadoId);
    }

    @Transactional(readOnly = true)
    public Mascota verificarLectura(UUID mascotaId, UUID actorId) {
        var mascota = buscar(mascotaId); verificarLectura(mascota, actorId); return mascota;
    }

    public void verificarLectura(Mascota mascota, UUID actorId) { verificarCompartido(mascota, actorId, false); }

    @Transactional(readOnly = true)
    public Mascota verificarGestionSalud(UUID mascotaId, UUID actorId) {
        var mascota = buscar(mascotaId); verificarGestionSalud(mascota, actorId); return mascota;
    }

    public void verificarGestionSalud(Mascota mascota, UUID actorId) { verificarCompartido(mascota, actorId, true); }

    public boolean esCuidador(Mascota mascota, UUID actorId) {
        validarMascota(mascota);
        return !actorId.equals(mascota.getPropietario().getIdUsuario())
                && perfil(mascota, actorId) == PerfilCompartido.Cuidador;
    }

    private void verificarCompartido(Mascota mascota, UUID actorId, boolean gestionSalud) {
        validarMascota(mascota);
        if (actorId.equals(mascota.getPropietario().getIdUsuario())) return;
        var perfil = perfil(mascota, actorId);
        if (gestionSalud && perfil != PerfilCompartido.Familia)
            throw new AccesoDenegadoException("El perfil no permite gestionar salud o recordatorios");
    }

    private PerfilCompartido perfil(Mascota mascota, UUID actorId) {
        var acceso = accesos.findByPropietario_IdUsuarioAndInvitado_IdUsuarioAndActivoTrue(
                mascota.getPropietario().getIdUsuario(), actorId)
                .orElseThrow(() -> new AccesoDenegadoException("No tienes acceso a esta mascota"));
        if (!acceso.getPropietario().isActivo() || !acceso.getInvitado().isActivo())
            throw new AccesoDenegadoException("El acceso compartido no esta disponible");
        return acceso.getPerfil();
    }

    private Mascota buscar(UUID id) {
        return mascotaRepository.findByIdMascotaAndFechaEliminacionIsNull(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mascota no encontrada"));
    }

    private void validarMascota(Mascota mascota) {
        if (mascota == null || mascota.getFechaEliminacion() != null || mascota.getPropietario() == null)
            throw new RecursoNoEncontradoException("Mascota no encontrada");
    }
}
