package com.MisMascotas.backend.Service;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import com.MisMascotas.backend.DTO.CuidadoCompartidoDTO.Cupo;
import com.MisMascotas.backend.Exception.CuidadoCompartidoException;
import com.MisMascotas.backend.Repository.*;

/** Regla única de cupo; los escritores la consultan bajo bloqueo del propietario. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CupoCuidadoService {
    private static final int LIMITE_FREE = 3;
    private final PremiumService premium;
    private final AccesoCompartidoRepository accesos;
    private final InvitacionRepository invitaciones;

    public Cupo consultar(UUID propietarioId, Instant ahora) {
        boolean esPremium = premium.esPremium(propietarioId);
        long activos = accesos.countByPropietario_IdUsuarioAndActivoTrue(propietarioId);
        long pendientes = invitaciones.contarPendientesVigentes(propietarioId, ahora);
        long ocupados = activos + pendientes;
        return new Cupo(esPremium, esPremium ? null : LIMITE_FREE, activos, pendientes, ocupados,
                esPremium ? null : Math.max(0L, LIMITE_FREE - ocupados));
    }

    public void exigirLugar(UUID propietarioId, Instant ahora, boolean reemplazaReserva) {
        var cupo = consultar(propietarioId, ahora);
        if (!cupo.esPremium() && (cupo.ocupados() > cupo.limite()
                || (!reemplazaReserva && cupo.ocupados() == cupo.limite())))
            throw new CuidadoCompartidoException("LIMITE_INVITADOS_ALCANZADO",
                    "El plan Free permite hasta 3 accesos e invitaciones pendientes vigentes");
    }
}
