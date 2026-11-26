package com.MisMascotas.backend.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.MisMascotas.backend.Audit.Auditable;
import com.MisMascotas.backend.DTO.MascotaRequestDTO;
import com.MisMascotas.backend.DTO.MascotaResponseDTO;
import com.MisMascotas.backend.Entity.Mascota;
import com.MisMascotas.backend.Entity.TipoAccionAuditoria;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Exception.LimiteMascotasAlcanzadoException;
import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Repository.EventoClinicoRepository;
import com.MisMascotas.backend.Repository.MascotaRepository;
import com.MisMascotas.backend.Repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MascotaService {

    private static final int LIMITE_MASCOTAS_PLAN_GRATUITO = 5;

    private final MascotaRepository mascotaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AccesoMascotaService accesoMascotaService;
    private final EventoClinicoRepository eventoClinicoRepository;
    private final EventoAuditoriaPendienteService eventoAuditoriaPendienteService;
    private final PremiumService premiumService;

    @Auditable(entidad = "mascota", accion = TipoAccionAuditoria.CREATE)
    @Transactional
    public MascotaResponseDTO crear(MascotaRequestDTO request, UUID propietarioId) {
        // Serializa las altas del propietario para que dos solicitudes no superen juntas el cupo Free.
        Usuario propietario = usuarioRepository.findByIdConBloqueo(propietarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado con ID: " + propietarioId));

        validarLimiteMascotasPlan(propietarioId);
        validarCambioFotoPerfil(request, null, propietario.getIdUsuario());

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

        Mascota guardada = mascotaRepository.save(mascota);
        return mapToResponse(guardada);
    }

    @Transactional(readOnly = true)
    public List<MascotaResponseDTO> listarPorPropietario(UUID propietarioId) {
        return accesoMascotaService.obtenerMascotasAccesibles(propietarioId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MascotaResponseDTO obtenerPorId(UUID id, UUID usuarioAutenticadoId) {
        Mascota mascota = accesoMascotaService.verificarLectura(id, usuarioAutenticadoId);
        return mapToResponse(mascota);
    }

    /**
     * Uso exclusivo del aspecto de auditoria para capturar el estado anterior.
     * Los controllers deben usar la variante que recibe usuarioAutenticadoId.
     */
    @Deprecated
    @Transactional(readOnly = true)
    public MascotaResponseDTO obtenerPorId(UUID id) {
        Mascota mascota = mascotaRepository.findByIdMascotaAndFechaEliminacionIsNull(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mascota no encontrada con ID: " + id));
        return mapToResponse(mascota);
    }

    @Transactional
    public MascotaResponseDTO editar(UUID id, MascotaRequestDTO request, UUID usuarioAutenticadoId) {
        Mascota mascota = accesoMascotaService.verificarAcceso(id, usuarioAutenticadoId);
        validarCambioFotoPerfil(request, mascota.getFotoPerfil(), mascota.getPropietario().getIdUsuario());
        MascotaResponseDTO valorAnterior = mapToResponse(mascota);
        var ultimaMedicionPeso = eventoClinicoRepository
                .findFirstByMascota_IdMascotaAndTipoAndFechaEliminacionIsNullOrderByFechaDesc(id, "PESO");

        mascota.setNombre(request.nombre());
        mascota.setEspecie(request.especie());
        mascota.setRaza(request.raza());
        mascota.setFechaNacimiento(request.fechaNacimiento());
        mascota.setFechaAproximada(request.fechaAproximada());
        mascota.setEdadValor(request.edadValor());
        mascota.setEdadUnidad(request.edadUnidad());
        if (request.fotoPerfilInformada()) {
            mascota.setFotoPerfil(request.fotoPerfil());
        }
        mascota.setPesoActual(ultimaMedicionPeso.isPresent()
                ? ultimaMedicionPeso.get().getValorPeso()
                : request.pesoActual());
        mascota.setNotas(request.notas());

        Mascota actualizada = mascotaRepository.save(mascota);
        MascotaResponseDTO valorNuevo = mapToResponse(actualizada);
        eventoAuditoriaPendienteService.encolar(
                usuarioAutenticadoId,
                "mascota",
                TipoAccionAuditoria.UPDATE,
                id,
                valorAnterior,
                valorNuevo);
        return valorNuevo;
    }

    @Auditable(entidad = "mascota", accion = TipoAccionAuditoria.DELETE)
    @Transactional
    public void eliminar(UUID id, UUID usuarioAutenticadoId) {
        Mascota mascota = accesoMascotaService.verificarAcceso(id, usuarioAutenticadoId);

        mascota.setFechaEliminacion(Instant.now());
        mascotaRepository.save(mascota);
    }

    private void validarLimiteMascotasPlan(UUID propietarioId) {
        if (premiumService.esPremium(propietarioId)) {
            return;
        }

        long mascotasActivas = mascotaRepository.countByPropietario_IdUsuarioAndFechaEliminacionIsNull(propietarioId);
        if (mascotasActivas >= LIMITE_MASCOTAS_PLAN_GRATUITO) {
            throw new LimiteMascotasAlcanzadoException(
                    "Alcanzaste el limite de 5 mascotas del plan gratuito. Actualiza al plan premium para registrar mas mascotas.");
        }
    }

    private void validarCambioFotoPerfil(MascotaRequestDTO request, String almacenada, UUID propietarioId) {
        // Ausencia conserva; null explícito retira; una URL sin cambios no exige renovar Premium.
        if (request.fotoPerfilInformada() && request.fotoPerfil() != null
                && !Objects.equals(request.fotoPerfil(), almacenada)) {
            premiumService.exigirPremium(propietarioId);
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
                mascota.getCreadoEn()
        );
    }
}
