package com.MisMascotas.backend.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.MisMascotas.backend.DTO.FotoRequestDTO;
import com.MisMascotas.backend.DTO.FotoResponseDTO;
import com.MisMascotas.backend.Entity.Album;
import com.MisMascotas.backend.Entity.Foto;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Repository.AlbumRepository;
import com.MisMascotas.backend.Repository.FotoRepository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FotoService {

    private final FotoRepository fotoRepository;
    private final AlbumRepository albumRepository;
    private final EntityManager entityManager;

    @Transactional
    public FotoResponseDTO crear(UUID albumId, FotoRequestDTO request, UUID subidaPorId) {
        Album album = albumRepository.findByIdAlbumAndFechaEliminacionIsNull(albumId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Álbum no encontrado con ID: " + albumId));

        Usuario usuarioRef = subidaPorId != null ? entityManager.getReference(Usuario.class, subidaPorId) : null;

        Foto foto = Foto.builder()
                .album(album)
                .urlArchivo(request.urlArchivo())
                .formato(request.formato())
                .subidaPor(usuarioRef)
                .build();

        Foto guardada = fotoRepository.save(foto);

        return mapToResponse(guardada);
    }

    @Transactional(readOnly = true)
    public List<FotoResponseDTO> listarPorAlbum(UUID albumId) {
        albumRepository.findByIdAlbumAndFechaEliminacionIsNull(albumId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Álbum no encontrado con ID: " + albumId));

        List<Foto> fotos = fotoRepository.findFotosActivasPorAlbum(albumId);
        return fotos.stream().map(this::mapToResponse).toList();
    }

    @Transactional(readOnly = true)
    public FotoResponseDTO obtenerPorId(UUID id) {
        Foto foto = fotoRepository.findById(id)
                .filter(f -> f.getFechaEliminacion() == null)
                .orElseThrow(() -> new RecursoNoEncontradoException("Foto no encontrada con ID: " + id));

        return mapToResponse(foto);
    }

    @Transactional
    public void eliminar(UUID id) {
        Foto foto = fotoRepository.findById(id)
                .filter(f -> f.getFechaEliminacion() == null)
                .orElseThrow(() -> new RecursoNoEncontradoException("Foto no encontrada con ID: " + id));

        foto.setFechaEliminacion(LocalDateTime.now());
        fotoRepository.save(foto);
    }

    private FotoResponseDTO mapToResponse(Foto foto) {
        return new FotoResponseDTO(
                foto.getIdFoto(),
                foto.getAlbum().getIdAlbum(),
                foto.getUrlArchivo(),
                foto.getFormato(),
                foto.getSubidaPor() != null ? foto.getSubidaPor().getIdUsuario() : null,
                foto.getCreadoEn()
        );
    }
}
