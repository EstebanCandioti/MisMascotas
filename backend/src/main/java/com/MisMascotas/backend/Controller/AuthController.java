package com.MisMascotas.backend.Controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.MisMascotas.backend.DTO.AuthResponseDTO;
import com.MisMascotas.backend.DTO.LoginRequestDTO;
import com.MisMascotas.backend.DTO.LoginResponseDTO;
import com.MisMascotas.backend.DTO.RegistroRequestDTO;
import com.MisMascotas.backend.DTO.VerificarCodigoRequestDTO;
import com.MisMascotas.backend.Service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> registrar(@Valid @RequestBody RegistroRequestDTO request) {
        return ResponseEntity.ok(authService.registrar(request));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        return ResponseEntity.ok(new LoginResponseDTO(
                authService.autenticarCredenciales(request),
                "Codigo de verificacion enviado"));
    }

    @PostMapping("/login/verificar-codigo")
    public ResponseEntity<AuthResponseDTO> verificarCodigo(@Valid @RequestBody VerificarCodigoRequestDTO request) {
        return ResponseEntity.ok(authService.validarCodigoYCrearSesion(request));
    }
}