package com.MisMascotas.backend.Service;

import com.MisMascotas.backend.DTO.MascotaRequestDTO;
import com.MisMascotas.backend.DTO.MascotaResponseDTO;
import com.MisMascotas.backend.Entity.Estado;
import com.MisMascotas.backend.Entity.Mascota;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Repository.MascotaRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MascotaService {

    private final MascotaRepository mascotaRepository;
    private final EntityManager entityManager;

    @Transactional
    public MascotaResponseDTO crear(MascotaRequestDTO request, UUID propietarioId) {
        Usuario propietarioRef = entityManager.getReference(Usuario.class, propietarioId);

        Mascota mascota = new Mascota();
        mascota.setPropietario(propietarioRef);
        mascota.setNombre(request.nombre());
        mascota.setEspecie(request.especie());
        mascota.setRaza(request.raza());
        mascota.setFechaNacimiento(request.fechaNacimiento());
        mascota.setFechaAproximada(request.fechaAproximada());
        mascota.setEdadValor(request.edadValor());
        mascota.setEdadUnidad(request.edadUnidad());
        mascota.setFotoPerfil(request.fotoPerfil());
        mascota.setPesoActual(request.pesoActual());
        mascota.setNotas(request.notas());

        if (request.estadoId() != null) {
            Estado estadoRef = entityManager.getReference(Estado.class, request.estadoId());
            mascota.setEstado(estadoRef);
        }

        Mascota guardada = mascotaRepository.save(mascota);
        return mapToResponse(guardada);
    }

    @Transactional(readOnly = true)
    public List<MascotaResponseDTO> listarPorPropietario(UUID propietarioId) {
        return mascotaRepository.findByPropietarioIdAndFechaEliminacionIsNull(propietarioId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MascotaResponseDTO obtenerPorId(UUID id) {
        Mascota mascota = mascotaRepository.findByIdMascotaAndFechaEliminacionIsNull(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mascota no encontrada con ID: " + id));
        return mapToResponse(mascota);
    }

    @Transactional
    public MascotaResponseDTO editar(UUID id, MascotaRequestDTO request) {
        Mascota mascota = mascotaRepository.findByIdMascotaAndFechaEliminacionIsNull(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mascota no encontrada con ID: " + id));

        mascota.setNombre(request.nombre());
        mascota.setEspecie(request.especie());
        mascota.setRaza(request.raza());
        mascota.setFechaNacimiento(request.fechaNacimiento());
        mascota.setFechaAproximada(request.fechaAproximada());
        mascota.setEdadValor(request.edadValor());
        mascota.setEdadUnidad(request.edadUnidad());
        mascota.setFotoPerfil(request.fotoPerfil());
        mascota.setPesoActual(request.pesoActual());
        mascota.setNotas(request.notas());

        if (request.estadoId() != null) {
            Estado estadoRef = entityManager.getReference(Estado.class, request.estadoId());
            mascota.setEstado(estadoRef);
        }

        Mascota actualizada = mascotaRepository.save(mascota);
        return mapToResponse(actualizada);
    }

    @Transactional
    public void eliminar(UUID id) {
        Mascota mascota = mascotaRepository.findByIdMascotaAndFechaEliminacionIsNull(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mascota no encontrada con ID: " + id));

        mascota.setFechaEliminacion(LocalDateTime.now());
        mascotaRepository.save(mascota);
    }

    private MascotaResponseDTO mapToResponse(Mascota mascota) {
        return new MascotaResponseDTO(
                mascota.getIdMascota(),
                mascota.getPropietario() != null ? mascota.getPropietario().getIdUsuario() : null,
                mascota.getNombre(),
                mascota.getEspecie(),
                mascota.getRaza(),
                mascota.getFechaNacimiento(),
                mascota.isFechaAproximada(),
                mascota.getEdadValor(),
                mascota.getEdadUnidad(),
                mascota.getFotoPerfil(),
                mascota.getPesoActual(),
                mascota.getNotas(),
                mascota.getEstado() != null ? (UUID) null : null,
                mascota.getCreadoEn()
        );
    }
}