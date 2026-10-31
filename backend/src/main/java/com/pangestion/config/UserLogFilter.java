package com.pangestion.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Dentro de la cadena de seguridad (tras validar el token): apunta quien hace la peticion, "email (ROL)",
 * para la linea de log de RequestLogFilter. Se registra en SecurityConfig, no como @Component.
 */
public class UserLogFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            request.setAttribute(RequestLogFilter.USER_ATTRIBUTE, jwt.getSubject() + " (" + jwt.getClaimAsString("role") + ")");
        }
        chain.doFilter(request, response);
    }
}
