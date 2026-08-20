package com.MisMascotas.backend.Entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "badge")
public class Badge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_badge", updatable = false, nullable = false)
    private UUID idBadge;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mascota_id", nullable = false)
    private Mascota mascota;

    @NotBlank
    @Column(name = "texto", nullable = false, length = 100)
    private String texto;

    @Column(name = "emoji", length = 10)
    private String emoji;
}


