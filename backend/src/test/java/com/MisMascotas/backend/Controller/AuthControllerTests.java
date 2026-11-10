package com.MisMascotas.backend.Controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.MisMascotas.backend.DTO.LoginResponseDTO;
import com.MisMascotas.backend.Service.AuthService;
import com.MisMascotas.backend.Exception.GlobalExceptionHandler;

@ExtendWith(MockitoExtension.class)
class AuthControllerTests {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void emailDuplicadoConMayusculasYEspaciosDevuelveConflict() throws Exception {
        when(authService.registrar(argThat(request ->
                "existente@mismascotas.test".equals(request.email()))))
                .thenThrow(new IllegalArgumentException("El email ya esta registrado"));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRegistro("  EXISTENTE@MISMASCOTAS.TEST  ")))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("\"codigo\":\"ERROR_NEGOCIO\"")));
    }

    @Test
    void emailInvalidoDespuesDeNormalizarDevuelveBadRequest() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRegistro("  EMAIL-INVALIDO  ")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("\"codigo\":\"ERROR_VALIDACION\"")))
                .andExpect(content().string(containsString("email: El email no es valido")));

        verifyNoInteractions(authService);
    }

    @Test
    void emailNuevoValidoContinuaAlDesafioDosFactores() throws Exception {
        when(authService.registrar(argThat(request ->
                "nuevo@mismascotas.test".equals(request.email()))))
                .thenReturn(new LoginResponseDTO("token-pre-auth", "Codigo de verificacion enviado"));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRegistro("  NUEVO@MISMASCOTAS.TEST  ")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"token\":\"token-pre-auth\"")))
                .andExpect(content().string(containsString("Codigo de verificacion enviado")));
    }

    private String cuerpoRegistro(String email) {
        return """
                {
                  "nombre": "Usuario QA",
                  "email": "%s",
                  "password": "ClaveQa2026"
                }
                """.formatted(email);
    }
}
