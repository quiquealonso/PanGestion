package com.pangestion.service;

import com.pangestion.dto.AuthDto;
import com.pangestion.dto.UserDto;
import com.pangestion.model.User;
import com.pangestion.repository.UserRepository;
import com.pangestion.security.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthDto.LoginResponse login(AuthDto.LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .filter(User::isActive)
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPassword()))
                .orElseThrow(() -> new BadCredentialsException("Email o contrasena incorrectos"));
        user.setLastLogin(LocalDateTime.now());
        return new AuthDto.LoginResponse(tokenService.generateToken(user), tokenService.getExpirationMinutes(),
                UserDto.Response.from(user));
    }

    @Transactional(readOnly = true)
    public UserDto.Response me(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .filter(User::isActive)
                .map(UserDto.Response::from)
                .orElseThrow(() -> new BadCredentialsException("Usuario no valido"));
    }
}
