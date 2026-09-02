package com.MisMascotas.backend.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.MisMascotas.backend.Entity.AlbumMascota;
import com.MisMascotas.backend.Entity.AlbumMascotaId;

@Repository
public interface AlbumMascotaRepository extends JpaRepository<AlbumMascota, AlbumMascotaId> {

    @Query("SELECT am FROM AlbumMascota am WHERE am.mascota.idMascota = :mascotaId AND am.fechaEliminacion IS NULL")
    List<AlbumMascota> findByMascotaIdActivos(@Param("mascotaId") UUID mascotaId);

    @Query("SELECT am FROM AlbumMascota am WHERE am.album.idAlbum = :albumId AND am.fechaEliminacion IS NULL")
    Optional<AlbumMascota> findByAlbumIdActivo(@Param("albumId") UUID albumId);
}
