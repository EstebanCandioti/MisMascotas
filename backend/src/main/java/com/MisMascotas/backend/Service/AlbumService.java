package com.MisMascotas.backend.Service;

import com.MisMascotas.backend.DTO.AlbumRequestDTO;
import com.MisMascotas.backend.DTO.AlbumResponseDTO;
import com.MisMascotas.backend.Entity.*;
import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Repository.AlbumMascotaRepository;
import com.MisMascotas.backend.Repository.AlbumRepository;
import com.MisMascotas.backend.Repository.FotoRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AlbumService {

    private final AlbumRepository albumRepository;
    private final AlbumMascotaRepository albumMascotaRepository;
    private final FotoRepository fotoRepository;
    private final EntityManager entityManager;

    @Transactional
    public AlbumResponseDTO crear(AlbumRequestDTO request, UUID creadoPorId) {
        Usuario usuarioRef = entityManager.getReference(Usuario.class, creadoPorId);
        Mascota mascotaRef = entityManager.getReference(Mascota.class, request.mascotaId());

        Album album = Album.builder()
                .creadoPor(usuarioRef)
                .nombre(request.nombre())
                .descripcion(request.descripcion())
                .build();

        Album albumGuardado = albumRepository.save(album);

        AlbumMascotaId albumMascotaId = new AlbumMascotaId(albumGuardado.getIdAlbum(), request.mascotaId());
        AlbumMascota albumMascota = AlbumMascota.builder()
                .id(albumMascotaId)
                .album(albumGuardado)
                .mascota(mascotaRef)
                .build();

        albumMascotaRepository.save(albumMascota);

        return mapToResponse(albumGuardado, request.mascotaId(), 0);
    }

    @Transactional(readOnly = true)
    public List<AlbumResponseDTO> listarPorMascota(UUID mascotaId) {
        List<AlbumMascota> relaciones = albumMascotaRepository.findByMascotaIdActivos(mascotaId);

        return relaciones.stream()
                .map(rel -> {
                    Album album = rel.getAlbum();
                    int fotosCount = fotoRepository.countFotosActivasPorAlbum(album.getIdAlbum());
                    return mapToResponse(album, mascotaId, fotosCount);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public AlbumResponseDTO obtenerPorId(UUID id) {
        Album album = albumRepository.findByIdAlbumAndFechaEliminacionIsNull(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Álbum no encontrado con ID: " + id));

        UUID mascotaId = albumMascotaRepository.findByAlbumIdActivo(id)
                .map(rel -> rel.getId().getIdMascota())
                .orElse(null);

        int fotosCount = fotoRepository.countFotosActivasPorAlbum(id);

        return mapToResponse(album, mascotaId, fotosCount);
    }

    @Transactional
    public AlbumResponseDTO editar(UUID id, AlbumRequestDTO request) {
        Album album = albumRepository.findByIdAlbumAndFechaEliminacionIsNull(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Álbum no encontrado con ID: " + id));

        album.setNombre(request.nombre());
        album.setDescripcion(request.descripcion());

        Album actualizado = albumRepository.save(album);

        UUID mascotaId = albumMascotaRepository.findByAlbumIdActivo(id)
                .map(rel -> rel.getId().getIdMascota())
                .orElse(null);

        int fotosCount = fotoRepository.countFotosActivasPorAlbum(id);

        return mapToResponse(actualizado, mascotaId, fotosCount);
    }

    @Transactional
    public void eliminar(UUID id) {
        Album album = albumRepository.findByIdAlbumAndFechaEliminacionIsNull(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Álbum no encontrado con ID: " + id));

        LocalDateTime ahora = LocalDateTime.now();
        album.setFechaEliminacion(ahora);
        albumRepository.save(album);

        albumMascotaRepository.findByAlbumIdActivo(id)
                .ifPresent(rel -> {
                    rel.setFechaEliminacion(ahora);
                    albumMascotaRepository.save(rel);
                });

        List<Foto> fotos = fotoRepository.findFotosActivasPorAlbum(id);
        for (Foto foto : fotos) {
            foto.setFechaEliminacion(ahora);
            fotoRepository.save(foto);
        }
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
