package com.MisMascotas.backend.Controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
        return ResponseEntity.ok(authService.autenticarCredenciales(request));
    }

    @PostMapping("/verificar-codigo")
    public ResponseEntity<AuthResponseDTO> verificarCodigo(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @Valid @RequestBody VerificarCodigoRequestDTO request) {
        String tokenPreAuth = extraerBearerToken(authorizationHeader);
        return ResponseEntity.ok(authService.validarCodigoYCrearSesion(tokenPreAuth, request));
    }

    private String extraerBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Token de verificacion invalido");
        }

        return authorizationHeader.substring(7);
    }
}