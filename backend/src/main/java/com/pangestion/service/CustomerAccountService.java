package com.pangestion.service;

import com.pangestion.dto.AccountDto;
import com.pangestion.exception.BusinessException;
import com.pangestion.exception.EmailNotVerifiedException;
import com.pangestion.mail.MailService;
import com.pangestion.model.CustomerAccount;
import com.pangestion.repository.CustomerAccountRepository;
import com.pangestion.security.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Cuentas de los clientes de la web publica: registro con confirmacion por email, login y datos de la cuenta.
 * Sin SMTP configurado (MailService desactivado) la cuenta se activa al registrarse, sin confirmar el email.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CustomerAccountService {

    /** El enlace de confirmacion caduca a las 24 horas */
    private static final long VERIFICATION_HOURS = 24;
    /** El enlace para restablecer la contrasena caduca a los 60 minutos */
    private static final long RESET_MINUTES = 60;
    private static final String CHECK_EMAIL = "Te hemos enviado un email. Abre el enlace que contiene para activar tu cuenta.";

    private final CustomerAccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final MailService mailService;
    private final SecureRandom random = new SecureRandom();

    /** Web publica, para el enlace del email (https://usuario.github.io/PanGestion) */
    @Value("${app.public-web-url}")
    private String publicWebUrl;

    /** Mismo uso que en AuthService: comparar siempre un BCrypt para no revelar que emails existen por el tiempo */
    private String dummyHash;

    public AccountDto.RegisterResponse register(AccountDto.RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        Optional<CustomerAccount> existing = accountRepository.findByEmailIgnoreCase(email);

        if (!mailService.isEnabled()) {
            // Sin email no se puede demostrar que el correo es suyo: se activa directamente
            if (existing.isPresent()) {
                throw new BusinessException("Ya existe una cuenta con ese email. Inicia sesion.");
            }
            CustomerAccount account = accountRepository.save(newAccount(request, email, true));
            return new AccountDto.RegisterResponse(false, "Cuenta creada", session(account));
        }

        if (existing.isPresent() && existing.get().isVerified()) {
            // Misma respuesta que con un email nuevo (no se revela si existe); el aviso le llega a su correo
            mailService.send(email, "Ya tienes una cuenta en PanGestion",
                    "Hola " + existing.get().getName() + ",\n\nAlguien (seguramente tu) ha intentado crear una cuenta con este email, "
                            + "pero ya tienes una. Inicia sesion en " + publicWebUrl + "/pedido/\n\n"
                            + "Si no has sido tu, no tienes que hacer nada.");
            return new AccountDto.RegisterResponse(true, CHECK_EMAIL, null);
        }

        // Cuenta nueva, o sin confirmar: se guardan los datos de este registro y se envia un enlace nuevo
        CustomerAccount account = existing.orElseGet(() -> newAccount(request, email, false));
        account.setName(request.name().trim());
        account.setPhone(blankToNull(request.phone()));
        account.setPassword(passwordEncoder.encode(request.password()));
        sendVerification(accountRepository.save(account));
        return new AccountDto.RegisterResponse(true, CHECK_EMAIL, null);
    }

    /** Abre el enlace del email: activa la cuenta y deja la sesion iniciada. */
    public AccountDto.LoginResponse verify(AccountDto.VerifyRequest request) {
        CustomerAccount account = accountRepository.findByVerificationTokenHash(sha256(request.token()))
                .filter(a -> a.getVerificationExpiresAt() != null && a.getVerificationExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new BusinessException("El enlace no es valido o ha caducado. Pide otro desde la pantalla de acceso."));
        account.setVerified(true);
        account.setVerificationTokenHash(null);
        account.setVerificationExpiresAt(null);
        account.setLastLogin(LocalDateTime.now());
        return session(account);
    }

    public AccountDto.LoginResponse login(AccountDto.LoginRequest request) {
        Optional<CustomerAccount> found = accountRepository.findByEmailIgnoreCase(request.email().trim());
        String hash = found.map(CustomerAccount::getPassword).orElseGet(this::dummyHash);
        boolean matches = passwordEncoder.matches(request.password(), hash);
        CustomerAccount account = found.filter(a -> matches)
                .orElseThrow(() -> new BadCredentialsException("Email o contrasena incorrectos"));
        if (!account.isVerified()) {
            throw new EmailNotVerifiedException();
        }
        account.setLastLogin(LocalDateTime.now());
        return session(account);
    }

    /** Envia otro enlace de confirmacion. Siempre responde lo mismo, exista o no la cuenta. */
    public AccountDto.MessageResponse resend(AccountDto.EmailRequest request) {
        accountRepository.findByEmailIgnoreCase(request.email().trim())
                .filter(a -> !a.isVerified())
                .ifPresent(this::sendVerification);
        return new AccountDto.MessageResponse("Si la cuenta existe y no esta confirmada, te hemos enviado otro enlace.");
    }

    /**
     * "He olvidado mi contrasena": envia un enlace para poner otra, de un solo uso y que caduca a la hora.
     * Siempre responde lo mismo, exista o no la cuenta (no revela que emails tienen cuenta).
     */
    public AccountDto.MessageResponse forgotPassword(AccountDto.EmailRequest request) {
        accountRepository.findByEmailIgnoreCase(request.email().trim()).ifPresent(account -> {
            String token = newToken();
            account.setResetTokenHash(sha256(token));
            account.setResetExpiresAt(LocalDateTime.now().plusMinutes(RESET_MINUTES));
            mailService.send(account.getEmail(), "Restablecer tu contrasena de PanGestion",
                    "Hola " + account.getName() + ",\n\n"
                            + "Para poner una contrasena nueva, abre este enlace:\n\n"
                            + publicWebUrl + "/pedido/?restablecer=" + token + "\n\n"
                            + "El enlace caduca en " + RESET_MINUTES + " minutos y solo se puede usar una vez.\n"
                            + "Si no lo has pedido tu, ignora este email: tu contrasena no cambia.");
        });
        return new AccountDto.MessageResponse("Si hay una cuenta con ese email, te hemos enviado un enlace para poner "
                + "una contrasena nueva. Caduca en " + RESET_MINUTES + " minutos.");
    }

    /** Pone la contrasena nueva con el enlace del email y deja la sesion iniciada. Cierra las demas sesiones. */
    public AccountDto.LoginResponse resetPassword(AccountDto.ResetPasswordRequest request) {
        CustomerAccount account = accountRepository.findByResetTokenHash(sha256(request.token()))
                .filter(a -> a.getResetExpiresAt() != null && a.getResetExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new BusinessException("El enlace no es valido o ha caducado. Pide otro."));
        account.setPassword(passwordEncoder.encode(request.password()));
        account.setResetTokenHash(null);
        account.setResetExpiresAt(null);
        // Abrir el enlace demuestra que el email es suyo
        account.setVerified(true);
        account.setVerificationTokenHash(null);
        account.setVerificationExpiresAt(null);
        // Los tokens emitidos antes de este momento dejan de valer (precision de segundos, como el "iat" del token)
        account.setPasswordChangedAt(LocalDateTime.now().withNano(0));
        account.setLastLogin(LocalDateTime.now());
        return session(account);
    }

    @Transactional(readOnly = true)
    public AccountDto.Response me(Jwt jwt) {
        return AccountDto.Response.from(getVerified(jwt));
    }

    /**
     * Cuenta del token de cliente (para hacer un pedido o ver la cuenta): confirmada y con un token emitido despues
     * del ultimo cambio de contrasena. Si no, 401 y el cliente tiene que volver a entrar.
     */
    @Transactional(readOnly = true)
    public CustomerAccount getVerified(Jwt jwt) {
        LocalDateTime issuedAt = jwt.getIssuedAt() == null ? null
                : LocalDateTime.ofInstant(jwt.getIssuedAt(), ZoneId.systemDefault());
        return accountRepository.findByEmailIgnoreCase(jwt.getSubject())
                .filter(CustomerAccount::isVerified)
                .filter(a -> a.getPasswordChangedAt() == null || (issuedAt != null && !issuedAt.isBefore(a.getPasswordChangedAt())))
                .orElseThrow(() -> new BadCredentialsException("Sesion caducada: vuelve a entrar"));
    }

    private CustomerAccount newAccount(AccountDto.RegisterRequest request, String email, boolean verified) {
        CustomerAccount account = new CustomerAccount();
        account.setName(request.name().trim());
        account.setEmail(email);
        account.setPhone(blankToNull(request.phone()));
        account.setPassword(passwordEncoder.encode(request.password()));
        account.setVerified(verified);
        return account;
    }

    /** Enlace aleatorio de 256 bits (en la BBDD solo se guarda su SHA-256) */
    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void sendVerification(CustomerAccount account) {
        String token = newToken();
        account.setVerificationTokenHash(sha256(token));
        account.setVerificationExpiresAt(LocalDateTime.now().plusHours(VERIFICATION_HOURS));
        mailService.send(account.getEmail(), "Confirma tu cuenta de PanGestion",
                "Hola " + account.getName() + ",\n\n"
                        + "Para activar tu cuenta y poder hacer pedidos, abre este enlace:\n\n"
                        + publicWebUrl + "/pedido/?verificar=" + token + "\n\n"
                        + "El enlace caduca en " + VERIFICATION_HOURS + " horas.\n"
                        + "Si no has creado una cuenta en PanGestion, ignora este email: la cuenta no se activara.");
    }

    private AccountDto.LoginResponse session(CustomerAccount account) {
        return new AccountDto.LoginResponse(tokenService.generateCustomerToken(account),
                TokenService.CUSTOMER_EXPIRATION_DAYS * 24 * 60, AccountDto.Response.from(account));
    }

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode("contrasena-que-no-coincide-con-ninguna");
        }
        return dummyHash;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
