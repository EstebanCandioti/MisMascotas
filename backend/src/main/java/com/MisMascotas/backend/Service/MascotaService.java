package com.MisMascotas.backend.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.MisMascotas.backend.DTO.MascotaRequestDTO;
import com.MisMascotas.backend.DTO.MascotaResponseDTO;
import com.MisMascotas.backend.Entity.Mascota;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Repository.MascotaRepository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

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
        mascota.setNotas(request.notas());

        Mascota guardada = mascotaRepository.save(mascota);
        return mapToResponse(guardada);
    }

    @Transactional(readOnly = true)
    public List<MascotaResponseDTO> listarPorUsuario(UUID usuarioId) {
        return mascotaRepository.findByPropietarioIdAndFechaEliminacionIsNull(usuarioId)
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
        mascota.setNotas(request.notas());

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
                mascota.getNombre(),
                mascota.getEspecie(),
                mascota.getRaza(),
                mascota.getFechaNacimiento(),
                mascota.isFechaAproximada(),
                mascota.getEdadValor(),
                mascota.getEdadUnidad(),
                mascota.getFotoPerfil(),
                mascota.getNotas(),
                mascota.getPropietario() != null ? mascota.getPropietario().getIdUsuario() : null,
                mascota.getFechaEliminacion() != null,
                mascota.getFechaEliminacion()
        );
    }
}