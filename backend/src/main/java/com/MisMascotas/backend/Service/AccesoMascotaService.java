package com.MisMascotas.backend.Service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.MisMascotas.backend.Entity.Mascota;
import com.MisMascotas.backend.Exception.AccesoDenegadoException;
import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Repository.MascotaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccesoMascotaService {

    private final MascotaRepository mascotaRepository;

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
        return mascotaRepository.findByPropietario_IdUsuarioAndFechaEliminacionIsNull(usuarioAutenticadoId);
    }
}
