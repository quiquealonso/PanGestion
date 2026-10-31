package com.pangestion.controller;

import com.pangestion.dto.AccountDto;
import com.pangestion.security.RateLimiter;
import com.pangestion.service.CustomerAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/**
 * Cuentas de cliente de la web publica: para hacer un pedido hay que tener cuenta (con el email confirmado).
 * Todo es publico salvo /me, que necesita el token de cliente. Con limite de intentos contra fuerza bruta y spam.
 * Solo existe con app.customer-accounts.enabled=true (desactivado: 404).
 */
@ConditionalOnProperty(name = "app.customer-accounts.enabled", havingValue = "true")
@RestController
@RequestMapping("/api/public/account")
@RequiredArgsConstructor
public class CustomerAccountController {

    private final CustomerAccountService accountService;
    private final RateLimiter rateLimiter;

    /** 202: hay que confirmar el email; 201: cuenta activa (servidor sin SMTP) */
    @PostMapping("/register")
    public ResponseEntity<AccountDto.RegisterResponse> register(
            @Valid @RequestBody AccountDto.RegisterRequest request, HttpServletRequest http) {
        rateLimiter.check("account-register-ip:" + http.getRemoteAddr(), 5, Duration.ofHours(1));
        AccountDto.RegisterResponse response = accountService.register(request);
        return ResponseEntity
                .status(response.verificationRequired() ? HttpStatus.ACCEPTED : HttpStatus.CREATED).body(response);
    }

    @PostMapping("/verify")
    public AccountDto.LoginResponse verify(@Valid @RequestBody AccountDto.VerifyRequest request, HttpServletRequest http) {
        rateLimiter.check("account-verify-ip:" + http.getRemoteAddr(), 20, Duration.ofMinutes(10));
        return accountService.verify(request);
    }

    @PostMapping("/login")
    public AccountDto.LoginResponse login(@Valid @RequestBody AccountDto.LoginRequest request, HttpServletRequest http) {
        rateLimiter.check("account-login-ip:" + http.getRemoteAddr(), 20, Duration.ofMinutes(5));
        rateLimiter.check("account-login-email:" + request.email().trim().toLowerCase(), 10, Duration.ofMinutes(15));
        return accountService.login(request);
    }

    @PostMapping("/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AccountDto.MessageResponse resend(@Valid @RequestBody AccountDto.EmailRequest request, HttpServletRequest http) {
        rateLimiter.check("account-resend-ip:" + http.getRemoteAddr(), 5, Duration.ofHours(1));
        rateLimiter.check("account-resend-email:" + request.email().trim().toLowerCase(), 3, Duration.ofHours(1));
        return accountService.resend(request);
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AccountDto.MessageResponse forgotPassword(@Valid @RequestBody AccountDto.EmailRequest request, HttpServletRequest http) {
        rateLimiter.check("account-forgot-ip:" + http.getRemoteAddr(), 10, Duration.ofHours(1));
        rateLimiter.check("account-forgot-email:" + request.email().trim().toLowerCase(), 3, Duration.ofHours(1));
        return accountService.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    public AccountDto.LoginResponse resetPassword(@Valid @RequestBody AccountDto.ResetPasswordRequest request,
                                                  HttpServletRequest http) {
        rateLimiter.check("account-reset-ip:" + http.getRemoteAddr(), 20, Duration.ofMinutes(10));
        return accountService.resetPassword(request);
    }

    @GetMapping("/me")
    public AccountDto.Response me(@AuthenticationPrincipal Jwt jwt) {
        return accountService.me(jwt);
    }
}
