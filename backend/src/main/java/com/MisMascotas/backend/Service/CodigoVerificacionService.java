package com.MisMascotas.backend.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.MisMascotas.backend.Entity.CodigoVerificacion;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Exception.CodigoExpiradoException;
import com.MisMascotas.backend.Exception.CodigoInvalidoException;
import com.MisMascotas.backend.Repository.CodigoVerificacionRepository;

@Service
public class CodigoVerificacionService {

    private static final int CODIGO_MIN = 100000;
    private static final int CODIGO_RANGO = 900000;

    @Value("${auth.codigo-verificacion.expiracion-minutos:10}")
    private long minutosValidezCodigo;

    private final CodigoVerificacionRepository codigoVerificacionRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public CodigoVerificacionService(CodigoVerificacionRepository codigoVerificacionRepository) {
        this.codigoVerificacionRepository = codigoVerificacionRepository;
    }

    public CodigoVerificacion generarParaUsuario(Usuario usuario) {
        codigoVerificacionRepository.findByUsuario_IdUsuarioAndUsadoFalse(usuario.getIdUsuario())
                .forEach(codigoAnterior -> {
                    codigoAnterior.setUsado(true);
                    codigoVerificacionRepository.save(codigoAnterior);
                });

        CodigoVerificacion codigoVerificacion = new CodigoVerificacion();
        codigoVerificacion.setUsuario(usuario);
        codigoVerificacion.setCodigo(generarCodigo());
        codigoVerificacion.setExpiraEn(Instant.now().plus(minutosValidezCodigo, ChronoUnit.MINUTES));
        codigoVerificacion.setUsado(false);

        return codigoVerificacionRepository.save(codigoVerificacion);
    }

    public Usuario validarCodigo(UUID usuarioId, String codigoIngresado) {
        CodigoVerificacion codigoVerificacion = codigoVerificacionRepository
                .findFirstByUsuario_IdUsuarioAndUsadoFalseOrderByCreadoEnDesc(usuarioId)
                .orElseThrow(() -> new CodigoInvalidoException("El codigo no es valido"));

        if (codigoVerificacion.getExpiraEn().isBefore(Instant.now())) {
            throw new CodigoExpiradoException("El codigo expiro. Te enviamos uno nuevo");
        }

        if (!codigoVerificacion.getCodigo().equals(codigoIngresado)) {
            throw new CodigoInvalidoException("El codigo no es valido");
        }

        codigoVerificacion.setUsado(true);
        codigoVerificacionRepository.save(codigoVerificacion);

        return codigoVerificacion.getUsuario();
    }

    private String generarCodigo() {
        return String.valueOf(CODIGO_MIN + secureRandom.nextInt(CODIGO_RANGO));
    }
}