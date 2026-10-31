package com.pangestion.dto;

import com.pangestion.model.CustomerAccount;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Cuentas de cliente de la web publica de pedidos (/api/public/account). */
public final class AccountDto {

    private AccountDto() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 120) String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @Pattern(regexp = "^[0-9 +]{9,20}$", message = "Telefono no valido") String phone) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record VerifyRequest(@NotBlank @Size(max = 100) String token) {
    }

    /** Nueva contrasena con el enlace del email "restablecer contrasena" */
    public record ResetPasswordRequest(@NotBlank @Size(max = 100) String token,
                                       @NotBlank @Size(min = 8, max = 100) String password) {
    }

    public record EmailRequest(@NotBlank @Email @Size(max = 120) String email) {
    }

    public record Response(Long id, String name, String email, String phone) {

        public static Response from(CustomerAccount account) {
            return new Response(account.getId(), account.getName(), account.getEmail(), account.getPhone());
        }
    }

    public record LoginResponse(String token, long expiresInMinutes, Response account) {
    }

    /**
     * Respuesta al registrarse. Si hay que confirmar el email, session es null y message explica que mirar el correo.
     * Si el servidor no tiene SMTP configurado la cuenta queda activa y session ya trae el token.
     */
    public record RegisterResponse(boolean verificationRequired, String message, LoginResponse session) {
    }

    public record MessageResponse(String message) {
    }
}
