package com.pangestion.security;

import com.pangestion.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Genera los tokens JWT que el frontend envia en la cabecera Authorization. */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final long expirationMinutes;

    public TokenService(JwtEncoder encoder, @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.encoder = encoder;
        this.expirationMinutes = expirationMinutes;
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("pangestion")
                .issuedAt(now)
                .expiresAt(now.plus(expirationMinutes, ChronoUnit.MINUTES))
                .subject(user.getEmail())
                .claim("name", user.getName())
                .claim("role", user.getRole().name());
        if (user.getCompany() != null) {
            // La empresa del usuario viaja en el token: todas las consultas se filtran por ella
            claims.claim("companyId", user.getCompany().getId());
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    public long getExpirationMinutes() {
        return expirationMinutes;
    }
}
