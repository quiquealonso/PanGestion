package com.pangestion.controller;

import com.pangestion.dto.AuthDto;
import com.pangestion.dto.UserDto;
import com.pangestion.security.RateLimiter;
import com.pangestion.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RateLimiter rateLimiter;

    /** Fuerza bruta: como mucho 20 intentos cada 5 min desde una IP y 10 cada 15 min contra un mismo email (429). */
    @PostMapping("/login")
    public AuthDto.LoginResponse login(@Valid @RequestBody AuthDto.LoginRequest request, HttpServletRequest http) {
        rateLimiter.check("login-ip:" + http.getRemoteAddr(), 20, Duration.ofMinutes(5));
        rateLimiter.check("login-email:" + request.email().trim().toLowerCase(), 10, Duration.ofMinutes(15));
        return authService.login(request);
    }

    /** Datos del usuario del token actual. */
    @GetMapping("/me")
    public UserDto.Response me(@AuthenticationPrincipal Jwt jwt) {
        return authService.me(jwt.getSubject());
    }
}
