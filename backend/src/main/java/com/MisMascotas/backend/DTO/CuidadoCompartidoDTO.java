package com.MisMascotas.backend.DTO;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.*;
import com.MisMascotas.backend.Entity.PerfilCompartido;
import com.MisMascotas.backend.Util.NormalizadorEmail;

public final class CuidadoCompartidoDTO {
    private CuidadoCompartidoDTO() {}

    public record NuevaInvitacion(
            @NotBlank(message = "El email es obligatorio") @Email(message = "El email no es valido")
            @Size(max = 255, message = "El email es demasiado largo") String emailInvitado,
            @NotNull(message = "El perfil es obligatorio") PerfilCompartido perfil) {
        public NuevaInvitacion { emailInvitado = NormalizadorEmail.normalizar(emailInvitado); }
    }

    public record CambiarPerfil(@NotNull(message = "El perfil es obligatorio") PerfilCompartido perfil) {}

    public record InvitacionDetalle(UUID idInvitacion, UUID propietarioId, String nombrePropietario,
            String emailInvitado, PerfilCompartido perfil, String estado, Instant creadoEn, Instant expiraEn) {}

    public record AccesoDetalle(UUID idAcceso, UUID propietarioId, String nombrePropietario,
            UUID invitadoId, String nombreInvitado, String emailInvitado, PerfilCompartido perfil,
            boolean activo, Instant fechaIngreso) {}

    public record Aceptacion(InvitacionDetalle invitacion, AccesoDetalle acceso) {}

    public record Cupo(boolean esPremium, Integer limite, long accesosActivos, long invitacionesPendientes,
            long ocupados, Long disponibles) {}

    public record Bandeja(List<InvitacionDetalle> invitacionesEnviadas, List<InvitacionDetalle> invitacionesRecibidas,
            List<AccesoDetalle> accesosOtorgados, List<AccesoDetalle> cuidadosRecibidos, Cupo cupo) {}
}
