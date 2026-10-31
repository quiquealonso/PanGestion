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
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    /** Hash de una contrasena cualquiera: si el email no existe se compara igual, para tardar lo mismo que con uno real. */
    private String dummyHash;

    public AuthDto.LoginResponse login(AuthDto.LoginRequest request) {
        Optional<User> found = userRepository.findByEmailIgnoreCase(request.email().trim()).filter(User::isActive);
        // Siempre se calcula un BCrypt: asi el tiempo de respuesta no revela que emails existen
        String hash = found.map(User::getPassword).orElseGet(this::dummyHash);
        boolean matches = passwordEncoder.matches(request.password(), hash);
        User user = found.filter(candidate -> matches)
                .orElseThrow(() -> new BadCredentialsException("Email o contrasena incorrectos"));
        user.setLastLogin(LocalDateTime.now());
        return new AuthDto.LoginResponse(tokenService.generateToken(user), tokenService.getExpirationMinutes(),
                UserDto.Response.from(user));
    }

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode("contrasena-que-no-coincide-con-ninguna");
        }
        return dummyHash;
    }

    @Transactional(readOnly = true)
    public UserDto.Response me(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .filter(User::isActive)
                .map(UserDto.Response::from)
                .orElseThrow(() -> new BadCredentialsException("Usuario no valido"));
    }
}
