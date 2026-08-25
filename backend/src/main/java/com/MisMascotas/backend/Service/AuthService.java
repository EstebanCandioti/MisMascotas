package com.MisMascotas.backend.Service;

import java.util.UUID;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.MisMascotas.backend.DTO.AuthResponseDTO;
import com.MisMascotas.backend.DTO.LoginRequestDTO;
import com.MisMascotas.backend.DTO.LoginResponseDTO;
import com.MisMascotas.backend.DTO.RegistroRequestDTO;
import com.MisMascotas.backend.DTO.VerificarCodigoRequestDTO;
import com.MisMascotas.backend.Entity.CodigoVerificacion;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Exception.CodigoExpiradoException;
import com.MisMascotas.backend.Exception.CodigoInvalidoException;
import com.MisMascotas.backend.Repository.UsuarioRepository;
import com.MisMascotas.backend.Security.JwtService;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final CodigoVerificacionService codigoVerificacionService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UsuarioRepository usuarioRepository,
            CodigoVerificacionService codigoVerificacionService,
            EmailService emailService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.codigoVerificacionService = codigoVerificacionService;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponseDTO registrar(RegistroRequestDTO request) {
        String emailNormalizado = request.email().trim().toLowerCase();
        if (usuarioRepository.existsByEmail(emailNormalizado)) {
            throw new IllegalArgumentException("El email ya esta registrado");
        }

        Usuario usuario = new Usuario();
        usuario.setNombre(request.nombre().trim());
        usuario.setEmail(emailNormalizado);
        usuario.setPasswordHash(passwordEncoder.encode(request.password()));
        usuario.setActivo(true);
        usuario.setEsPremium(false);

        Usuario usuarioGuardado = usuarioRepository.save(usuario);
        String token = jwtService.generarToken(usuarioGuardado);

        return new AuthResponseDTO(token);
    }

    public LoginResponseDTO autenticarCredenciales(LoginRequestDTO request) {
        if (request.email() == null || request.email().trim().isEmpty()
                || request.password() == null || request.password().isEmpty()) {
            throw new BadCredentialsException("Credenciales invalidas");
        }

        Usuario usuario = usuarioRepository.findByEmail(request.email().trim().toLowerCase());
        if (usuario == null || !usuario.isActivo()
                || !passwordEncoder.matches(request.password(), usuario.getPasswordHash())) {
            throw new BadCredentialsException("Credenciales invalidas");
        }

        generarYEnviarCodigoVerificacion(usuario);
        String tokenPreAuth = jwtService.generarTokenPreAuth(usuario);
        return new LoginResponseDTO(tokenPreAuth, "Codigo de verificacion enviado");
    }

    public AuthResponseDTO validarCodigoYCrearSesion(String tokenPreAuth, VerificarCodigoRequestDTO request) {
        UUID usuarioId = jwtService.validarTokenPreAuthYExtraerUsuarioId(tokenPreAuth);
        if (usuarioId == null) {
            throw new BadCredentialsException("Token de verificacion invalido");
        }

        try {
            Usuario usuario = codigoVerificacionService.validarCodigo(usuarioId, request.codigo());
            String tokenSesion = jwtService.generarToken(usuario);
            return new AuthResponseDTO(tokenSesion);
        } catch (CodigoExpiradoException ex) {
            Usuario usuario = usuarioRepository.findById(usuarioId)
                    .orElseThrow(() -> new CodigoInvalidoException("El codigo no es valido"));
            generarYEnviarCodigoVerificacion(usuario);
            throw ex;
        }
    }

    private void generarYEnviarCodigoVerificacion(Usuario usuario) {
        CodigoVerificacion codigoVerificacion = codigoVerificacionService.generarParaUsuario(usuario);
        emailService.enviarCodigoVerificacion(usuario.getEmail(), codigoVerificacion.getCodigo());
    }
}