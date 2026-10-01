package com.MisMascotas.backend.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.MisMascotas.backend.Audit.Auditable;
import com.MisMascotas.backend.DTO.FotoRequestDTO;
import com.MisMascotas.backend.DTO.FotoResponseDTO;
import com.MisMascotas.backend.Entity.Album;
import com.MisMascotas.backend.Entity.AlbumMascota;
import com.MisMascotas.backend.Entity.Foto;
import com.MisMascotas.backend.Entity.Mascota;
import com.MisMascotas.backend.Entity.TipoAccionAuditoria;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Exception.AccesoDenegadoException;
import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Repository.AlbumMascotaRepository;
import com.MisMascotas.backend.Repository.AlbumRepository;
import com.MisMascotas.backend.Repository.FotoRepository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FotoService {

    private final FotoRepository fotoRepository;
    private final AlbumRepository albumRepository;
    private final AlbumMascotaRepository albumMascotaRepository;
    private final EntityManager entityManager;

    @Auditable(entidad = "foto", accion = TipoAccionAuditoria.CREATE)
    @Transactional
    public FotoResponseDTO crear(UUID albumId, FotoRequestDTO request, UUID subidaPorId) {
        Album album = buscarAlbumActivo(albumId);
        validarAccesoAlbum(album, subidaPorId);

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
    public List<FotoResponseDTO> listarPorAlbum(UUID albumId, UUID usuarioAutenticadoId) {
        Album album = buscarAlbumActivo(albumId);
        validarAccesoAlbum(album, usuarioAutenticadoId);

        List<Foto> fotos = fotoRepository.findFotosActivasPorAlbum(albumId);
        return fotos.stream().map(this::mapToResponse).toList();
    }

    @Transactional(readOnly = true)
    public FotoResponseDTO obtenerPorId(UUID id, UUID usuarioAutenticadoId) {
        Foto foto = buscarFotoActiva(id);
        validarAccesoAlbum(foto.getAlbum(), usuarioAutenticadoId);
        return mapToResponse(foto);
    }

    /**
     * Uso exclusivo del aspecto de auditoria para capturar el estado anterior.
     * Los controllers deben usar la variante que recibe usuarioAutenticadoId.
     */
    @Deprecated
    @Transactional(readOnly = true)
    public FotoResponseDTO obtenerPorId(UUID id) {
        return mapToResponse(buscarFotoActiva(id));
    }

    @Auditable(entidad = "foto", accion = TipoAccionAuditoria.DELETE)
    @Transactional
    public void eliminar(UUID id, UUID usuarioAutenticadoId) {
        Foto foto = buscarFotoActiva(id);
        validarAccesoAlbum(foto.getAlbum(), usuarioAutenticadoId);

        foto.setFechaEliminacion(Instant.now());
        fotoRepository.save(foto);
    }

    private Album buscarAlbumActivo(UUID id) {
        return albumRepository.findByIdAlbumAndFechaEliminacionIsNull(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Album no encontrado con ID: " + id));
    }

    private Foto buscarFotoActiva(UUID id) {
        return fotoRepository.findByIdFotoAndFechaEliminacionIsNull(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Foto no encontrada con ID: " + id));
    }

    private void validarAccesoAlbum(Album album, UUID usuarioAutenticadoId) {
        List<AlbumMascota> relaciones = albumMascotaRepository.findAllByAlbumIdActivos(album.getIdAlbum());
        if (relaciones.isEmpty()) {
            throw new RecursoNoEncontradoException("Album no encontrado con ID: " + album.getIdAlbum());
        }

        boolean todasLasMascotasSonDelUsuario = relaciones.stream()
                .map(AlbumMascota::getMascota)
                .allMatch(mascota -> usuarioAutenticadoId.equals(obtenerPropietarioId(mascota)));

        if (!todasLasMascotasSonDelUsuario) {
            throw new AccesoDenegadoException("No tenes permisos para realizar esta accion sobre este album");
        }
    }

    private UUID obtenerPropietarioId(Mascota mascota) {
        return mascota != null && mascota.getPropietario() != null ? mascota.getPropietario().getIdUsuario() : null;
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