package com.MisMascotas.backend.Service;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.MisMascotas.backend.DTO.AuthResponseDTO;
import com.MisMascotas.backend.DTO.LoginRequestDTO;
import com.MisMascotas.backend.DTO.LoginResponseDTO;
import com.MisMascotas.backend.DTO.RefreshTokenRequestDTO;
import com.MisMascotas.backend.DTO.RegistroRequestDTO;
import com.MisMascotas.backend.DTO.VerificarCodigoRequestDTO;
import com.MisMascotas.backend.Entity.CodigoVerificacion;
import com.MisMascotas.backend.Entity.TipoAccionAuditoria;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Exception.CodigoExpiradoException;
import com.MisMascotas.backend.Exception.CodigoInvalidoException;
import com.MisMascotas.backend.Repository.UsuarioRepository;
import com.MisMascotas.backend.Security.JwtService;
import com.MisMascotas.backend.Security.JwtService.TokenAcceso;
import com.MisMascotas.backend.Service.RefreshTokenService.RefreshTokenEmitido;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private static final String TIPO_BEARER = "Bearer";

    private final UsuarioRepository usuarioRepository;
    private final CodigoVerificacionService codigoVerificacionService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final LogAuditoriaService logAuditoriaService;

    public AuthService(
            UsuarioRepository usuarioRepository,
            CodigoVerificacionService codigoVerificacionService,
            EmailService emailService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            LogAuditoriaService logAuditoriaService) {
        this.usuarioRepository = usuarioRepository;
        this.codigoVerificacionService = codigoVerificacionService;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.logAuditoriaService = logAuditoriaService;
    }

    public LoginResponseDTO registrar(RegistroRequestDTO request) {
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
        registrarAuditoriaUsuario(
                usuarioGuardado,
                TipoAccionAuditoria.CREATE,
                construirValorUsuarioCreado(usuarioGuardado));

        generarYEnviarCodigoVerificacion(usuarioGuardado);
        String tokenPreAuth = jwtService.generarTokenPreAuth(usuarioGuardado);
        return new LoginResponseDTO(tokenPreAuth, "Codigo de verificacion enviado");
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
            registrarAuditoriaUsuario(usuario, TipoAccionAuditoria.LOGIN, null);
            return emitirSesion(usuario);
        } catch (CodigoExpiradoException ex) {
            Usuario usuario = usuarioRepository.findById(usuarioId)
                    .orElseThrow(() -> new CodigoInvalidoException("El codigo no es valido"));
            generarYEnviarCodigoVerificacion(usuario);
            throw ex;
        }
    }

    public AuthResponseDTO renovarSesion(RefreshTokenRequestDTO request) {
        RefreshTokenEmitido refreshToken = refreshTokenService.renovar(request.refreshToken());
        return emitirSesion(refreshToken.usuario(), refreshToken);
    }

    public void cerrarSesion(RefreshTokenRequestDTO request) {
        Usuario usuario = refreshTokenService.revocarTokenActual(request.refreshToken());
        registrarAuditoriaUsuario(usuario, TipoAccionAuditoria.LOGOUT, null);
    }

    private AuthResponseDTO emitirSesion(Usuario usuario) {
        RefreshTokenEmitido refreshToken = refreshTokenService.emitirParaUsuario(usuario);
        return emitirSesion(usuario, refreshToken);
    }

    private AuthResponseDTO emitirSesion(Usuario usuario, RefreshTokenEmitido refreshToken) {
        TokenAcceso accessToken = jwtService.generarTokenAcceso(usuario);
        return new AuthResponseDTO(
                accessToken.token(),
                refreshToken.token(),
                TIPO_BEARER,
                accessToken.expiraEn());
    }

    private void generarYEnviarCodigoVerificacion(Usuario usuario) {
        CodigoVerificacion codigoVerificacion = codigoVerificacionService.generarParaUsuario(usuario);
        emailService.enviarCodigoVerificacion(usuario.getEmail(), codigoVerificacion.getCodigo());
    }

    private void registrarAuditoriaUsuario(Usuario usuario, TipoAccionAuditoria accion, String valorNuevo) {
        Authentication autenticacionAnterior = SecurityContextHolder.getContext().getAuthentication();

        try {
            UsernamePasswordAuthenticationToken autenticacionTemporal = new UsernamePasswordAuthenticationToken(
                    usuario.getEmail(),
                    null,
                    List.of());
            SecurityContextHolder.getContext().setAuthentication(autenticacionTemporal);

            logAuditoriaService.registrar(
                    "usuario",
                    accion,
                    usuario.getIdUsuario(),
                    null,
                    valorNuevo);
        } catch (Exception ex) {
            logger.warn(
                    "No se pudo registrar auditoria de auth {} para usuario {}: {}",
                    accion,
                    usuario.getIdUsuario(),
                    ex.getMessage());
        } finally {
            SecurityContextHolder.getContext().setAuthentication(autenticacionAnterior);
        }
    }

    private String construirValorUsuarioCreado(Usuario usuario) {
        return String.format(
                "{\"idUsuario\":\"%s\",\"email\":\"%s\",\"nombre\":\"%s\",\"activo\":%s,\"esPremium\":%s}",
                usuario.getIdUsuario(),
                escaparJson(usuario.getEmail()),
                escaparJson(usuario.getNombre()),
                usuario.isActivo(),
                usuario.isEsPremium());
    }

    private String escaparJson(String valor) {
        if (valor == null) {
            return "";
        }

        return valor.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
