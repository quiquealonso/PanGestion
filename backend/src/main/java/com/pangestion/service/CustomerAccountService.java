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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
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

    @Transactional(readOnly = true)
    public AccountDto.Response me(String email) {
        return AccountDto.Response.from(getVerified(email));
    }

    /** Cuenta confirmada del token (para hacer un pedido). */
    @Transactional(readOnly = true)
    public CustomerAccount getVerified(String email) {
        return accountRepository.findByEmailIgnoreCase(email)
                .filter(CustomerAccount::isVerified)
                .orElseThrow(() -> new BadCredentialsException("Cuenta no valida"));
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

    private void sendVerification(CustomerAccount account) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
