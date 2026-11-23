package com.MisMascotas.backend.Service;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.MisMascotas.backend.Entity.TipoAccionAuditoria;
import com.MisMascotas.backend.Repository.*;
import com.MisMascotas.backend.Exception.EstadoNoConfiguradoException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SuscripcionVencimientoService {
    private final UsuarioRepository usuarios;
    private final SuscripcionRepository suscripciones;
    private final PagoRepository pagos;
    private final EstadoRepository estados;
    private final EventoAuditoriaPendienteService auditoria;
    private final Clock reloj;

    @Transactional
    public void vencer(UUID usuarioId) {
        var usuario = usuarios.findByIdConBloqueo(usuarioId).orElse(null);
        if (usuario == null) return;
        var ahora = reloj.instant();
        var propias = suscripciones.findAllByUsuario_IdUsuario(usuarioId);
        boolean cobertura = false;
        boolean hayPagas = false;
        for (var s : propias) {
            if (!pagos.existePagoAprobado(s.getIdSuscripcion())) continue;
            hayPagas = true;
            if (s.getFechaVencimiento() == null) throw new IllegalStateException("Suscripcion paga sin vencimiento; requiere revision");
            if (s.getFechaVencimiento().isAfter(ahora)) { cobertura = true; continue; }
            if (s.getEstadoCatalogo() != null && "ACTIVA".equals(s.getEstadoCatalogo().getNombre())) {
                s.setEstadoCatalogo(estados.findByEntidadAndNombre("SUSCRIPCION", "VENCIDA")
                        .orElseThrow(() -> new EstadoNoConfiguradoException("SUSCRIPCION", "VENCIDA")));
                suscripciones.saveAndFlush(s);
                auditoria.encolar(null, "suscripcion", TipoAccionAuditoria.DEACTIVATE, s.getIdSuscripcion(),
                        new Cambio("ACTIVA", "VENCIMIENTO_PROGRAMADO"), new Cambio("VENCIDA", "VENCIMIENTO_PROGRAMADO"));
            }
        }
        if (hayPagas && usuario.isEsPremium() != cobertura) {
            boolean anterior = usuario.isEsPremium();
            usuario.setEsPremium(cobertura); usuarios.saveAndFlush(usuario);
            auditoria.encolar(null, "usuario", cobertura ? TipoAccionAuditoria.ACTIVATE : TipoAccionAuditoria.DEACTIVATE,
                    usuarioId, new CambioPremium(anterior, "VENCIMIENTO_PROGRAMADO"),
                    new CambioPremium(cobertura, "VENCIMIENTO_PROGRAMADO"));
        }
    }
    private record Cambio(String estado, String origen) {}
    private record CambioPremium(boolean esPremium, String origen) {}
}
