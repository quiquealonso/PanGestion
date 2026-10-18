package com.pangestion.security;

import com.pangestion.exception.BusinessException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Datos del usuario que hace la peticion, sacados de su token JWT.
 * Los servicios lo usan para trabajar siempre con los datos de SU empresa.
 */
@Component
public class CurrentUser {

    /** Empresa del usuario. Falla si no tiene (SUPERADMIN), porque esa accion es de una empresa concreta. */
    public Long companyId() {
        Object companyId = jwt().getClaim("companyId");
        if (companyId == null) {
            throw new BusinessException("Esta accion es de una empresa concreta: entra con un usuario de esa empresa");
        }
        return ((Number) companyId).longValue();
    }

    public String email() {
        return jwt().getSubject();
    }

    public boolean isSuperAdmin() {
        return "SUPERADMIN".equals(jwt().getClaimAsString("role"));
    }

    private Jwt jwt() {
        return (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
