package com.MisMascotas.backend.Controller;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.MisMascotas.backend.DTO.IniciarSuscripcionRequestDTO;
import com.MisMascotas.backend.DTO.SuscripcionPropiaResponseDTO;
import com.MisMascotas.backend.Service.SuscripcionConsultaService;
import com.MisMascotas.backend.Exception.ErrorResponse;
import com.MisMascotas.backend.Service.SuscripcionService;
import com.MisMascotas.backend.Service.SuscripcionService.ResultadoAltaSuscripcion;
import com.MisMascotas.backend.Service.SuscripcionService.ResultadoContinuacionSuscripcion;
import com.MisMascotas.backend.Service.SuscripcionService.ResultadoReemplazoSuscripcion;
import com.MisMascotas.backend.Service.SuscripcionCobroTransaccionalService.ResultadoCobro;
import com.MisMascotas.backend.Service.UsuarioAutenticadoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class SuscripcionController {

    private final SuscripcionService suscripcionService;
    private final UsuarioAutenticadoService usuarioAutenticadoService;
    private final SuscripcionConsultaService consultaService;
    private final com.MisMascotas.backend.Service.SuscripcionCancelacionService cancelacionService;

    @GetMapping("/suscripciones/planes")
    public ResponseEntity<java.util.List<com.MisMascotas.backend.Service.PlanSuscripcion>> listarPlanes() {
        return ResponseEntity.ok(suscripcionService.listarPlanes());
    }

    @PostMapping("/suscripciones/{idSuscripcion}/cancelar")
    public ResponseEntity<com.MisMascotas.backend.DTO.CancelarSuscripcionResponseDTO> cancelar(
            @PathVariable UUID idSuscripcion, Principal principal) {
        UUID usuarioId = usuarioAutenticadoService.obtenerIdUsuario(principal);
        return ResponseEntity.ok(cancelacionService.cancelar(usuarioId, idSuscripcion));
    }

    @GetMapping("/suscripciones/me")
    public ResponseEntity<SuscripcionPropiaResponseDTO> consultarPropia(Principal principal) {
        UUID usuarioId = usuarioAutenticadoService.obtenerIdUsuario(principal);
        return ResponseEntity.ok(consultaService.consultar(usuarioId));
    }

    @PostMapping("/suscripciones/iniciar")
    public ResponseEntity<ResultadoAltaSuscripcion> iniciar(
            @Valid @RequestBody IniciarSuscripcionRequestDTO request,
            Principal principal) {
        UUID usuarioId = usuarioAutenticadoService.obtenerIdUsuario(principal);
        return ResponseEntity.ok(suscripcionService.iniciarAlta(usuarioId, request.tipoPlan()));
    }

    @PostMapping("/suscripciones/{idSuscripcion}/continuar")
    public ResponseEntity<ResultadoContinuacionSuscripcion> continuar(
            @PathVariable UUID idSuscripcion,
            Principal principal) {
        UUID usuarioId = usuarioAutenticadoService.obtenerIdUsuario(principal);
        return ResponseEntity.ok(suscripcionService.continuarAlta(usuarioId, idSuscripcion));
    }

    @PostMapping("/suscripciones/{idSuscripcion}/reemplazar")
    public ResponseEntity<ResultadoReemplazoSuscripcion> reemplazar(
            @PathVariable UUID idSuscripcion,
            Principal principal) {
        UUID usuarioId = usuarioAutenticadoService.obtenerIdUsuario(principal);
        return ResponseEntity.ok(suscripcionService.reemplazarAlta(usuarioId, idSuscripcion));
    }

    @PostMapping("/suscripciones/{idSuscripcion}/confirmar-primer-cobro")
    public ResponseEntity<ResultadoCobro> confirmarPrimerCobro(
            @PathVariable UUID idSuscripcion,
            Principal principal) {
        UUID usuarioId = usuarioAutenticadoService.obtenerIdUsuario(principal);
        return ResponseEntity.ok(suscripcionService.confirmarPrimerCobro(usuarioId, idSuscripcion));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> manejarUuidInvalido() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(
                "ERROR_VALIDACION", "idSuscripcion: El identificador debe ser un UUID valido", Instant.now()));
    }
}
