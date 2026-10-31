package com.pangestion.web;

import com.pangestion.model.CustomerAccount;
import com.pangestion.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cuentas de cliente con SMTP: hay que confirmar el email con el enlace que se envia (el SMTP es un doble de prueba). */
@DisplayName("API · Cuentas de cliente (confirmación por email)")
@TestPropertySource(properties = {"app.customer-accounts.enabled=true", "spring.mail.host=smtp.test",
        "app.public-web-url=https://web.test/PanGestion"})
class CustomerAccountEmailApiTest extends ApiTestSupport {

    private static final Pattern LINK = Pattern.compile("https://web\\.test/PanGestion/pedido/\\?verificar=([A-Za-z0-9_-]+)");
    private static final Pattern RESET_LINK = Pattern.compile("https://web\\.test/PanGestion/pedido/\\?restablecer=([A-Za-z0-9_-]+)");

    @MockitoBean
    private JavaMailSender mailSender;

    private SimpleMailMessage lastEmail() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(captor.capture());
        return captor.getValue();
    }

    private String register() throws Exception {
        mvc.perform(postJson("/api/public/account/register", """
                        {"name": "Lola", "email": "lola@correo.es", "password": "unaclave123"}
                        """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.verificationRequired").value(true))
                .andExpect(jsonPath("$.session").doesNotExist());
        SimpleMailMessage email = lastEmail();
        assertThat(email.getTo()).containsExactly("lola@correo.es");
        Matcher link = LINK.matcher(email.getText());
        assertThat(link.find()).as("el email lleva el enlace de confirmacion").isTrue();
        return link.group(1);
    }

    @Test
    @DisplayName("registro → email con enlace; sin confirmar no entra (403); al confirmar entra; el enlace solo vale una vez")
    void verifyFlow() throws Exception {
        String token = register();
        CustomerAccount saved = customerAccountRepository.findByEmailIgnoreCase("lola@correo.es").orElseThrow();
        assertThat(saved.isVerified()).isFalse();
        assertThat(saved.getVerificationTokenHash()).isNotEqualTo(token).hasSize(64);

        String login = "{\"email\": \"lola@correo.es\", \"password\": \"unaclave123\"}";
        mvc.perform(postJson("/api/public/account/login", login)).andExpect(status().isForbidden());

        mvc.perform(postJson("/api/public/account/verify", "{\"token\": \"%s\"}".formatted(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.account.email").value("lola@correo.es"));
        mvc.perform(postJson("/api/public/account/login", login)).andExpect(status().isOk());

        mvc.perform(postJson("/api/public/account/verify", "{\"token\": \"%s\"}".formatted(token))).andExpect(status().isConflict());
        mvc.perform(postJson("/api/public/account/verify", "{\"token\": \"inventado\"}")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("pedir otro enlace: el anterior deja de valer; con una cuenta ya confirmada no se envía nada")
    void resend() throws Exception {
        String first = register();
        mvc.perform(postJson("/api/public/account/resend", "{\"email\": \"lola@correo.es\"}")).andExpect(status().isAccepted());
        Matcher second = LINK.matcher(lastEmail().getText());
        assertThat(second.find()).isTrue();
        assertThat(second.group(1)).isNotEqualTo(first);
        mvc.perform(postJson("/api/public/account/verify", "{\"token\": \"%s\"}".formatted(first))).andExpect(status().isConflict());
        mvc.perform(postJson("/api/public/account/verify", "{\"token\": \"%s\"}".formatted(second.group(1)))).andExpect(status().isOk());

        clearInvocations(mailSender);
        mvc.perform(postJson("/api/public/account/resend", "{\"email\": \"lola@correo.es\"}")).andExpect(status().isAccepted());
        mvc.perform(postJson("/api/public/account/resend", "{\"email\": \"nadie@correo.es\"}")).andExpect(status().isAccepted());
        verify(mailSender, never()).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("registrarse con un email que ya tiene cuenta: misma respuesta (202) y aviso a su correo; no cambia su contraseña")
    void registerExisting() throws Exception {
        CustomerAccount existing = account("Lola", "lola@correo.es", null);
        String hash = existing.getPassword();
        mvc.perform(postJson("/api/public/account/register", """
                        {"name": "Otra", "email": "lola@correo.es", "password": "otraclave123"}
                        """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.verificationRequired").value(true));
        assertThat(lastEmail().getSubject()).isEqualTo("Ya tienes una cuenta en PanGestion");
        assertThat(customerAccountRepository.findById(existing.getId()).orElseThrow().getPassword()).isEqualTo(hash);
    }

    private String requestReset(String email) throws Exception {
        clearInvocations(mailSender);
        mvc.perform(postJson("/api/public/account/forgot-password", "{\"email\": \"%s\"}".formatted(email)))
                .andExpect(status().isAccepted());
        SimpleMailMessage mail = lastEmail();
        assertThat(mail.getSubject()).isEqualTo("Restablecer tu contrasena de PanGestion");
        Matcher link = RESET_LINK.matcher(mail.getText());
        assertThat(link.find()).isTrue();
        return link.group(1);
    }

    @Test
    @DisplayName("contraseña olvidada: enlace por email de un solo uso; la nueva entra, la vieja no, y las sesiones anteriores se cierran")
    void resetPassword() throws Exception {
        CustomerAccount lola = account("Lola", "lola@correo.es", null);
        String oldSession = tokenService.generateCustomerToken(lola);
        mvc.perform(getJson("/api/public/account/me").header("Authorization", "Bearer " + oldSession)).andExpect(status().isOk());
        Thread.sleep(1100); // el token lleva la hora en segundos: el cambio de contrasena tiene que ser posterior

        String token = requestReset("lola@correo.es");
        assertThat(customerAccountRepository.findById(lola.getId()).orElseThrow().getResetTokenHash()).isNotEqualTo(token).hasSize(64);
        mvc.perform(postJson("/api/public/account/reset-password", "{\"token\": \"%s\", \"password\": \"nuevaclave123\"}".formatted(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());

        mvc.perform(postJson("/api/public/account/login", "{\"email\": \"lola@correo.es\", \"password\": \"nuevaclave123\"}"))
                .andExpect(status().isOk());
        mvc.perform(postJson("/api/public/account/login", "{\"email\": \"lola@correo.es\", \"password\": \"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isUnauthorized());
        // El enlace ya no vale y la sesion que habia abierta tampoco
        mvc.perform(postJson("/api/public/account/reset-password", "{\"token\": \"%s\", \"password\": \"otraclave123\"}".formatted(token)))
                .andExpect(status().isConflict());
        mvc.perform(getJson("/api/public/account/me").header("Authorization", "Bearer " + oldSession)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("contraseña olvidada: enlace caducado 409; email sin cuenta, misma respuesta y ningún email")
    void resetPasswordExpiredAndUnknown() throws Exception {
        CustomerAccount lola = account("Lola", "lola@correo.es", null);
        String token = requestReset("lola@correo.es");
        CustomerAccount saved = customerAccountRepository.findById(lola.getId()).orElseThrow();
        saved.setResetExpiresAt(java.time.LocalDateTime.now().minusMinutes(1));
        customerAccountRepository.save(saved);
        mvc.perform(postJson("/api/public/account/reset-password", "{\"token\": \"%s\", \"password\": \"nuevaclave123\"}".formatted(token)))
                .andExpect(status().isConflict());

        clearInvocations(mailSender);
        mvc.perform(postJson("/api/public/account/forgot-password", "{\"email\": \"nadie@correo.es\"}"))
                .andExpect(status().isAccepted());
        verify(mailSender, never()).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
    }
}
