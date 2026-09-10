package com.MisMascotas.backend.Repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.MisMascotas.backend.Entity.Foto;

@Repository
public interface FotoRepository extends JpaRepository<Foto, UUID> {

    @Query("SELECT COUNT(f) FROM Foto f WHERE f.album.idAlbum = :albumId AND f.fechaEliminacion IS NULL")
    int countFotosActivasPorAlbum(@Param("albumId") UUID albumId);

    @Query("SELECT f FROM Foto f WHERE f.album.idAlbum = :albumId AND f.fechaEliminacion IS NULL")
    List<Foto> findFotosActivasPorAlbum(@Param("albumId") UUID albumId);
}