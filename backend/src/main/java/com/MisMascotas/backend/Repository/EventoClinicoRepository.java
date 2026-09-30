<<<<<<< Updated upstream
=======
package com.MisMascotas.backend.Repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.MisMascotas.backend.Entity.EventoClinico;

@Repository
public interface EventoClinicoRepository extends JpaRepository<EventoClinico, UUID> {

    // Listar historial ordenado por fecha descendente (CU9)
    List<EventoClinico> findByMascotaIdOrderByFechaDesc(UUID mascotaId);

    // Listar historial filtrado por tipo y ordenado por fecha descendente (CU9)
    List<EventoClinico> findByMascotaIdAndTipoOrderByFechaDesc(UUID mascotaId, String tipo);
}
>>>>>>> Stashed changes
