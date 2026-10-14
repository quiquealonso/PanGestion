package com.pangestion.controller;

import com.pangestion.dto.AuthDto;
import com.pangestion.dto.UserDto;
import com.pangestion.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public AuthDto.LoginResponse login(@Valid @RequestBody AuthDto.LoginRequest request) {
        return authService.login(request);
    }

    /** Datos del usuario del token actual. */
    @GetMapping("/me")
    public UserDto.Response me(@AuthenticationPrincipal Jwt jwt) {
        return authService.me(jwt.getSubject());
    }
}
