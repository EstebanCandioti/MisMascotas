package com.MisMascotas.backend.Service;

import com.MisMascotas.backend.Audit.Auditable;
import com.MisMascotas.backend.DTO.AlbumRequestDTO;
import com.MisMascotas.backend.DTO.AlbumResponseDTO;
import com.MisMascotas.backend.Entity.Album;
import com.MisMascotas.backend.Entity.AlbumMascota;
import com.MisMascotas.backend.Entity.Foto;
import com.MisMascotas.backend.Entity.Mascota;
import com.MisMascotas.backend.Entity.TipoAccionAuditoria;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Repository.AlbumMascotaRepository;
import com.MisMascotas.backend.Repository.AlbumRepository;
import com.MisMascotas.backend.Repository.FotoRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AlbumService {

    private final AlbumRepository albumRepository;
    private final AlbumMascotaRepository albumMascotaRepository;
    private final FotoRepository fotoRepository;
    private final AccesoMascotaService accesoMascotaService;
    private final PremiumService premiumService;
    private final EntityManager entityManager;

    @Auditable(entidad = "album", accion = TipoAccionAuditoria.CREATE)
    @Transactional
    public AlbumResponseDTO crear(AlbumRequestDTO request, UUID creadoPorId) {
        Mascota mascota = accesoMascotaService.verificarAcceso(request.mascotaId(), creadoPorId);
        premiumService.exigirPremium(mascota.getPropietario().getIdUsuario());

        Usuario usuarioRef = entityManager.getReference(Usuario.class, creadoPorId);

        Album album = Album.builder()
                .creadoPor(usuarioRef)
                .nombre(request.nombre())
                .descripcion(request.descripcion())
                .build();

        Album albumGuardado = albumRepository.save(album);

        AlbumMascota albumMascota = AlbumMascota.builder()
                .album(albumGuardado)
                .mascota(mascota)
                .build();

        albumMascotaRepository.save(albumMascota);

        return mapToResponse(albumGuardado, request.mascotaId(), 0);
    }

    @Transactional(readOnly = true)
    public List<AlbumResponseDTO> listarPorMascota(UUID mascotaId, UUID usuarioAutenticadoId) {
        Mascota mascota = accesoMascotaService.verificarLectura(mascotaId, usuarioAutenticadoId);

        List<AlbumMascota> relaciones = albumMascotaRepository.findByMascotaIdActivos(mascotaId);

        return relaciones.stream()
                .filter(rel -> rel.getAlbum().getFechaEliminacion() == null)
                .map(rel -> {
                    Album album = rel.getAlbum();
                    validarAccesoAlbum(album, usuarioAutenticadoId, false);
                    int fotosCount = fotoRepository.countFotosActivasPorAlbum(album.getIdAlbum());
                    return mapToResponse(album, mascotaId, fotosCount);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public AlbumResponseDTO obtenerPorId(UUID id, UUID usuarioAutenticadoId) {
        Album album = buscarAlbumActivo(id);
        validarAccesoAlbum(album, usuarioAutenticadoId, false);
        return mapToResponse(album, obtenerMascotaPrincipalId(id), fotoRepository.countFotosActivasPorAlbum(id));
    }

    /**
     * Uso exclusivo del aspecto de auditoria para capturar el estado anterior.
     * Los controllers deben usar la variante que recibe usuarioAutenticadoId.
     */
    @Deprecated
    @Transactional(readOnly = true)
    public AlbumResponseDTO obtenerPorId(UUID id) {
        Album album = buscarAlbumActivo(id);
        return mapToResponse(album, obtenerMascotaPrincipalId(id), fotoRepository.countFotosActivasPorAlbum(id));
    }

    @Auditable(entidad = "album", accion = TipoAccionAuditoria.UPDATE)
    @Transactional
    public AlbumResponseDTO editar(UUID id, AlbumRequestDTO request, UUID usuarioAutenticadoId) {
        Album album = buscarAlbumActivo(id);
        UUID propietarioId = validarAccesoAlbum(album, usuarioAutenticadoId);
        premiumService.exigirPremium(propietarioId);

        album.setNombre(request.nombre());
        album.setDescripcion(request.descripcion());

        Album actualizado = albumRepository.save(album);
        return mapToResponse(actualizado, obtenerMascotaPrincipalId(id), fotoRepository.countFotosActivasPorAlbum(id));
    }

    @Auditable(entidad = "album", accion = TipoAccionAuditoria.DELETE)
    @Transactional
    public void eliminar(UUID id, UUID usuarioAutenticadoId) {
        Album album = buscarAlbumActivo(id);
        validarAccesoAlbum(album, usuarioAutenticadoId);

        Instant ahora = Instant.now();
        album.setFechaEliminacion(ahora);
        albumRepository.save(album);

        albumMascotaRepository.findAllByAlbumIdActivos(id)
                .forEach(rel -> {
                    rel.setFechaEliminacion(ahora);
                    albumMascotaRepository.save(rel);
                });

        List<Foto> fotos = fotoRepository.findFotosActivasPorAlbum(id);
        for (Foto foto : fotos) {
            foto.setFechaEliminacion(ahora);
            fotoRepository.save(foto);
        }
    }

    private Album buscarAlbumActivo(UUID id) {
        return albumRepository.findByIdAlbumAndFechaEliminacionIsNull(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Album no encontrado con ID: " + id));
    }


    private UUID validarAccesoAlbum(Album album, UUID usuarioAutenticadoId) {
        return validarAccesoAlbum(album, usuarioAutenticadoId, true);
    }

    private UUID validarAccesoAlbum(Album album, UUID usuarioAutenticadoId, boolean escritura) {
        List<AlbumMascota> relaciones = albumMascotaRepository.findAllByAlbumIdActivos(album.getIdAlbum());
        if (relaciones.isEmpty()) {
            throw new RecursoNoEncontradoException("Album no encontrado con ID: " + album.getIdAlbum());
        }

        relaciones.forEach(rel -> {
            if (escritura) accesoMascotaService.verificarAcceso(rel.getMascota(), usuarioAutenticadoId);
            else accesoMascotaService.verificarLectura(rel.getMascota(), usuarioAutenticadoId);
        });
        // El propietario puede eliminar aunque su cobertura haya vencido.
        return relaciones.getFirst().getMascota().getPropietario().getIdUsuario();
    }


    private UUID obtenerMascotaPrincipalId(UUID albumId) {
        return albumMascotaRepository.findAllByAlbumIdActivos(albumId).stream()
                .map(rel -> rel.getMascota().getIdMascota()).sorted().findFirst()
                .orElse(null);
    }

    private AlbumResponseDTO mapToResponse(Album album, UUID mascotaId, int cantidadFotos) {
        return new AlbumResponseDTO(
                album.getIdAlbum(),
                album.getNombre(),
                album.getDescripcion(),
                mascotaId,
                album.getCreadoPor() != null ? album.getCreadoPor().getIdUsuario() : null,
                album.getCreadoEn(),
                cantidadFotos
        );
    }
}
