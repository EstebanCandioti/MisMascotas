package com.MisMascotas.backend.Entity;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "album")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Album {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_album", updatable = false, nullable = false)
    private UUID idAlbum;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creado_por_id", nullable = false)
    private Usuario creadoPor;

    @NotBlank
    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "descripcion", columnDefinition = "text")
    private String descripcion;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "id_cliente", unique = true)
    private UUID idCliente;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    @Column(name = "fecha_eliminacion")
    private LocalDateTime fechaEliminacion;

    @PrePersist
    protected void onCreate() {
        if (creadoEn == null) {
            creadoEn = Instant.now();
        }
        if (actualizadoEn == null) {
            actualizadoEn = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        actualizadoEn = LocalDateTime.now();
    }
}

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
class AlbumMascotaId implements Serializable {

    private UUID idAlbum;

    private UUID idMascota;
}

@Entity
@Table(name = "album_mascota")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class AlbumMascota {

    @EmbeddedId
    private AlbumMascotaId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("idAlbum")
    @JoinColumn(name = "id_album", nullable = false)
    private Album album;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("idMascota")
    @JoinColumn(name = "id_mascota", nullable = false)
    private Mascota mascota;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    @Column(name = "fecha_eliminacion")
    private LocalDateTime fechaEliminacion;

    @PrePersist
    protected void onCreate() {
        if (actualizadoEn == null) {
            actualizadoEn = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        actualizadoEn = LocalDateTime.now();
    }
}

@Entity
@Table(name = "foto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class Foto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_foto", updatable = false, nullable = false)
    private UUID idFoto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "album_id", nullable = false)
    private Album album;

    @NotBlank
    @Column(name = "url_archivo", nullable = false, columnDefinition = "text")
    private String urlArchivo;

    @Column(name = "formato", length = 20)
    private String formato;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subida_por_id")
    private Usuario subidaPor;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "id_cliente", unique = true)
    private UUID idCliente;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    @Column(name = "fecha_eliminacion")
    private LocalDateTime fechaEliminacion;

    @PrePersist
    protected void onCreate() {
        if (creadoEn == null) {
            creadoEn = Instant.now();
        }
        if (actualizadoEn == null) {
            actualizadoEn = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        actualizadoEn = LocalDateTime.now();
    }
}