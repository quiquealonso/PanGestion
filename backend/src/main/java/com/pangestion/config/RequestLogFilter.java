package com.pangestion.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Log basico de la API: cada peticion recibe un id corto (cabecera X-Request-Id y %X{requestId} en todas las lineas
 * de log que escriba) y al terminar se registra una linea: metodo, ruta, estado, milisegundos, usuario e IP.
 * No se registran cuerpos, consultas ni cabeceras (pueden llevar datos personales o tokens).
 * Errores 5xx en ERROR, 4xx en WARN y el resto en INFO. Las imagenes (/uploads) no se registran.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLogFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID = "requestId";
    /** Atributo con "email (ROL)" que pone UserLogFilter dentro de la cadena de seguridad */
    public static final String USER_ATTRIBUTE = "pangestion.log.user";

    private final SecureRandom random = new SecureRandom();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        byte[] bytes = new byte[4];
        random.nextBytes(bytes);
        String requestId = HexFormat.of().formatHex(bytes);
        MDC.put(REQUEST_ID, requestId);
        response.setHeader("X-Request-Id", requestId);
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            int status = response.getStatus();
            Object user = request.getAttribute(USER_ATTRIBUTE);
            String line = "{} {} -> {} ({} ms) usuario={} ip={}";
            Object[] args = {request.getMethod(), request.getRequestURI(), status, ms, user == null ? "-" : user,
                    request.getRemoteAddr()};
            if (status >= 500) {
                log.error(line, args);
            } else if (status >= 400) {
                log.warn(line, args);
            } else {
                log.info(line, args);
            }
            MDC.remove(REQUEST_ID);
        }
    }
}
