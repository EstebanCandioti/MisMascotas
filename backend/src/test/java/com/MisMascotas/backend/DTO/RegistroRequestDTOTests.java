package com.MisMascotas.backend.DTO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

class RegistroRequestDTOTests {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void normalizaEmailAntesDeValidarlo() {
        RegistroRequestDTO request = new RegistroRequestDTO(
                "Usuario QA",
                "  USUARIO.QA@MISMASCOTAS.TEST  ",
                "ClaveQa2026");

        Set<ConstraintViolation<RegistroRequestDTO>> errores = validator.validate(request);

        assertEquals("usuario.qa@mismascotas.test", request.email());
        assertTrue(errores.isEmpty());
    }

    @Test
    void conservaElRechazoSiElEmailSigueSiendoInvalido() {
        RegistroRequestDTO request = new RegistroRequestDTO(
                "Usuario QA",
                "  EMAIL-INVALIDO  ",
                "ClaveQa2026");

        Set<ConstraintViolation<RegistroRequestDTO>> errores = validator.validate(request);

        assertEquals("email-invalido", request.email());
        assertTrue(errores.stream().anyMatch(error -> "email".equals(error.getPropertyPath().toString())));
    }
}
