package com.MisMascotas.backend.Controller;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import lombok.RequiredArgsConstructor;
import com.MisMascotas.backend.DTO.CuidadoCompartidoDTO.*;
import com.MisMascotas.backend.Exception.ErrorResponse;
import com.MisMascotas.backend.Service.*;

@RestController
@RequestMapping("/cuidado-compartido")
@RequiredArgsConstructor
public class CuidadoCompartidoController {
    private final CuidadoCompartidoService gestion;
    private final CuidadoCompartidoConsultaService consultas;
    private final UsuarioAutenticadoService autenticado;

    @PostMapping("/invitaciones")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitacionDetalle crear(@Valid @RequestBody NuevaInvitacion solicitud, Principal principal) {
        return gestion.crear(autenticado.obtenerIdUsuario(principal), solicitud);
    }

    @GetMapping("/me")
    public Bandeja bandeja(Principal principal) { return consultas.consultar(autenticado.obtenerIdUsuario(principal)); }

    @GetMapping("/invitaciones/{id}")
    public InvitacionDetalle invitacion(@PathVariable UUID id, Principal principal) {
        return consultas.consultarInvitacion(autenticado.obtenerIdUsuario(principal), id);
    }

    @PostMapping("/invitaciones/{id}/aceptar")
    public Aceptacion aceptar(@PathVariable UUID id, Principal principal) {
        return gestion.aceptar(autenticado.obtenerIdUsuario(principal), id);
    }

    @PostMapping("/invitaciones/{id}/rechazar")
    public InvitacionDetalle rechazar(@PathVariable UUID id, Principal principal) {
        return gestion.rechazar(autenticado.obtenerIdUsuario(principal), id);
    }

    @PostMapping("/invitaciones/{id}/cancelar")
    public InvitacionDetalle cancelar(@PathVariable UUID id, Principal principal) {
        return gestion.cancelar(autenticado.obtenerIdUsuario(principal), id);
    }

    @PostMapping("/invitaciones/{id}/reenviar")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitacionDetalle reenviar(@PathVariable UUID id, Principal principal) {
        return gestion.reenviar(autenticado.obtenerIdUsuario(principal), id);
    }

    @GetMapping("/accesos/{id}")
    public AccesoDetalle acceso(@PathVariable UUID id, Principal principal) {
        return consultas.consultarAcceso(autenticado.obtenerIdUsuario(principal), id);
    }

    @PatchMapping("/accesos/{id}/perfil")
    public AccesoDetalle perfil(@PathVariable UUID id, @Valid @RequestBody CambiarPerfil solicitud, Principal principal) {
        return gestion.cambiarPerfil(autenticado.obtenerIdUsuario(principal), id, solicitud);
    }

    @DeleteMapping("/accesos/{id}")
    public AccesoDetalle revocar(@PathVariable UUID id, Principal principal) {
        return gestion.revocar(autenticado.obtenerIdUsuario(principal), id);
    }

    @PostMapping("/accesos/{id}/salir")
    public AccesoDetalle salir(@PathVariable UUID id, Principal principal) {
        return gestion.salir(autenticado.obtenerIdUsuario(principal), id);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> solicitudInvalida() {
        return ResponseEntity.badRequest().body(new ErrorResponse("ERROR_VALIDACION",
                "El JSON, perfil o identificador no tiene un formato valido", Instant.now()));
    }
}
