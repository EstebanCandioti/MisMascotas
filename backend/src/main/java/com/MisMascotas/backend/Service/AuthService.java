package com.MisMascotas.backend.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.MisMascotas.backend.DTO.AuthResponseDTO;
import com.MisMascotas.backend.DTO.LoginRequestDTO;
import com.MisMascotas.backend.DTO.RegistroRequestDTO;
import com.MisMascotas.backend.DTO.VerificarCodigoRequestDTO;
import com.MisMascotas.backend.Entity.CodigoVerificacion;
import com.MisMascotas.backend.Exception.CodigoExpiradoException;
import com.MisMascotas.backend.Exception.CodigoInvalidoException;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Repository.CodigoVerificacionRepository;
import com.MisMascotas.backend.Repository.UsuarioRepository;
import com.MisMascotas.backend.Security.JwtService;

@Service
public class AuthService {

    private static final int CODIGO_MIN = 100000;
    private static final int CODIGO_RANGO = 900000;
    private static final int MINUTOS_VALIDEZ_CODIGO = 10;

    private final UsuarioRepository usuarioRepository;
    private final CodigoVerificacionRepository codigoVerificacionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            UsuarioRepository usuarioRepository,
            CodigoVerificacionRepository codigoVerificacionRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.codigoVerificacionRepository = codigoVerificacionRepository;
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

    public UUID autenticarCredenciales(LoginRequestDTO request) {
        if (request.email() == null || request.email().trim().isEmpty()
                || request.password() == null || request.password().isEmpty()) {
            throw new BadCredentialsException("Credenciales invalidas");
        }

        Usuario usuario = usuarioRepository.findByEmail(request.email().trim().toLowerCase());
        if (usuario == null || !usuario.isActivo()
                || !passwordEncoder.matches(request.password(), usuario.getPasswordHash())) {
            throw new BadCredentialsException("Credenciales invalidas");
        }

        generarYEnviarCodigoVerificacion(usuario.getIdUsuario());
        return usuario.getIdUsuario();
    }

    public void generarYEnviarCodigoVerificacion(UUID usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("El usuario no existe"));

        CodigoVerificacion codigoVerificacion = new CodigoVerificacion();
        codigoVerificacion.setUsuario(usuario);
        codigoVerificacion.setCodigo(generarCodigo());
        codigoVerificacion.setExpiraEn(Instant.now().plus(MINUTOS_VALIDEZ_CODIGO, ChronoUnit.MINUTES));
        codigoVerificacion.setUsado(false);

        codigoVerificacionRepository.save(codigoVerificacion);
        enviarCodigoPorEmail(usuario, codigoVerificacion.getCodigo());
    }

    public AuthResponseDTO validarCodigoYCrearSesion(VerificarCodigoRequestDTO request) {
        CodigoVerificacion codigoVerificacion = codigoVerificacionRepository
                .findFirstByUsuario_IdUsuarioAndUsadoFalseOrderByCreadoEnDesc(request.usuarioId())
                .orElseThrow(() -> new CodigoInvalidoException("El codigo no es valido"));

        if (codigoVerificacion.getExpiraEn().isBefore(Instant.now())) {
            generarYEnviarCodigoVerificacion(request.usuarioId());
            throw new CodigoExpiradoException("El codigo expiro. Te enviamos uno nuevo");
        }

        if (!codigoVerificacion.getCodigo().equals(request.codigo())) {
            throw new CodigoInvalidoException("El codigo no es valido");
        }

        codigoVerificacion.setUsado(true);
        codigoVerificacionRepository.save(codigoVerificacion);

        String token = jwtService.generarToken(codigoVerificacion.getUsuario());
        return new AuthResponseDTO(token);
    }

    private String generarCodigo() {
        return String.valueOf(CODIGO_MIN + secureRandom.nextInt(CODIGO_RANGO));
    }

    private void enviarCodigoPorEmail(Usuario usuario, String codigo) {
        // Pendiente: integrar JavaMailSender cuando este configurado el SMTP.
    }

}