package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import com.MisMascotas.backend.Entity.PerfilCompartido;

class NotificacionCuidadoTests {
    @Test @SuppressWarnings("unchecked")
    void sinTransporteSmtpDevuelveFalsoParaConservarLaOperacion() {
        var proveedor = (ObjectProvider<JavaMailSender>) mock(ObjectProvider.class);
        var aviso = new NotificacionCuidado(NotificacionCuidado.Tipo.INVITACION, UUID.randomUUID(),
                "invitado@example.test", "Propietario", "Invitado", PerfilCompartido.Familia, Instant.now());
        assertThat(new EmailService(proveedor).enviarNotificacionCuidado(aviso, "https://web.example.test/cuidado")).isFalse();
    }

    @ParameterizedTest @ValueSource(strings = {"/ruta-relativa", "ftp://web.example.test/cuidado", "https://usuario@web.example.test/cuidado",
            "https://web.example.test/cuidado?aceptar=true", "https://web.example.test/cuidado#fragmento"})
    void rechazaUrlWebAmbiguaOQueInyecteAcciones(String url) {
        assertThatThrownBy(() -> new NotificacionCuidadoListener(mock(EmailService.class), url)).isInstanceOf(IllegalArgumentException.class);
    }
}
