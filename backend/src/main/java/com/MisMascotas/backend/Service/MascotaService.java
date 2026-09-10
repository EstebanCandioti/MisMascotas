package com.MisMascotas.backend.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.MisMascotas.backend.Audit.Auditable;
import com.MisMascotas.backend.DTO.MascotaRequestDTO;
import com.MisMascotas.backend.DTO.MascotaResponseDTO;
import com.MisMascotas.backend.Entity.Estado;
import com.MisMascotas.backend.Entity.Mascota;
import com.MisMascotas.backend.Entity.TipoAccionAuditoria;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Exception.AccesoDenegadoException;
import com.MisMascotas.backend.Exception.LimiteMascotasAlcanzadoException;
import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Repository.EstadoRepository;
import com.MisMascotas.backend.Repository.MascotaRepository;
import com.MisMascotas.backend.Repository.UsuarioRepository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MascotaService {

    private static final int LIMITE_MASCOTAS_PLAN_GRATUITO = 5;
    private static final String ENTIDAD_MASCOTA = "mascota";
    private static final String ESTADO_ELIMINADA = "ELIMINADA";

    private final MascotaRepository mascotaRepository;
    private final UsuarioRepository usuarioRepository;
    private final EstadoRepository estadoRepository;
    private final EntityManager entityManager;

    @Auditable(entidad = "mascota", accion = TipoAccionAuditoria.CREATE)
    @Transactional
    public MascotaResponseDTO crear(MascotaRequestDTO request, UUID propietarioId) {
        Usuario propietario = usuarioRepository.findById(propietarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado con ID: " + propietarioId));

        validarLimiteMascotasPlan(propietario);

        Mascota mascota = new Mascota();
        mascota.setPropietario(propietario);
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
        return mascotaRepository.findByPropietario_IdUsuarioAndFechaEliminacionIsNull(propietarioId)
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

    @Auditable(entidad = "mascota", accion = TipoAccionAuditoria.UPDATE)
    @Transactional
    public MascotaResponseDTO editar(UUID id, MascotaRequestDTO request, UUID usuarioAutenticadoId) {
        Mascota mascota = mascotaRepository.findByIdMascotaAndFechaEliminacionIsNull(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mascota no encontrada con ID: " + id));

        validarPropietario(mascota, usuarioAutenticadoId);

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

    @Auditable(entidad = "mascota", accion = TipoAccionAuditoria.DELETE)
    @Transactional
    public void eliminar(UUID id, UUID usuarioAutenticadoId) {
        Mascota mascota = mascotaRepository.findByIdMascotaAndFechaEliminacionIsNull(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mascota no encontrada con ID: " + id));

        validarPropietario(mascota, usuarioAutenticadoId);

        Estado estadoEliminada = estadoRepository.findByEntidadIgnoreCaseAndNombreIgnoreCase(ENTIDAD_MASCOTA, ESTADO_ELIMINADA)
                .orElseThrow(() -> new RecursoNoEncontradoException("Estado ELIMINADA para mascota no encontrado"));

        mascota.setEstado(estadoEliminada);
        mascota.setFechaEliminacion(Instant.now());
        mascotaRepository.save(mascota);
    }

    private void validarPropietario(Mascota mascota, UUID usuarioAutenticadoId) {
        UUID propietarioId = mascota.getPropietario() != null ? mascota.getPropietario().getIdUsuario() : null;
        if (!usuarioAutenticadoId.equals(propietarioId)) {
            throw new AccesoDenegadoException("No tenes permisos para realizar esta accion sobre esta mascota");
        }
    }

    private void validarLimiteMascotasPlan(Usuario propietario) {
        if (propietario.isEsPremium()) {
            return;
        }

        long mascotasActivas = mascotaRepository.countByPropietario_IdUsuarioAndFechaEliminacionIsNull(propietario.getIdUsuario());
        if (mascotasActivas >= LIMITE_MASCOTAS_PLAN_GRATUITO) {
            throw new LimiteMascotasAlcanzadoException(
                    "Alcanzaste el limite de 5 mascotas del plan gratuito. Actualiza al plan premium para registrar mas mascotas.");
        }
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
                mascota.getEstado() != null ? mascota.getEstado().getIdEstado() : null,
                mascota.getCreadoEn()
        );
    }
}