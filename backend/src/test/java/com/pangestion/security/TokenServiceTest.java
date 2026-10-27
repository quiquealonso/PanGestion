package com.pangestion.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.pangestion.model.Company;
import com.pangestion.model.Role;
import com.pangestion.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El token JWT lleva quien es el usuario, su rol y su empresa, y caduca. */
@DisplayName("Unidad · TokenService")
class TokenServiceTest {

    private static final SecretKey KEY = key("clave-de-pruebas-del-token-de-32-caracteres");

    private final TokenService tokenService = new TokenService(new NimbusJwtEncoder(new ImmutableSecret<>(KEY)), 60);
    private final JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build();

    private static SecretKey key(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private static User user(Role role, Long companyId) {
        User user = new User();
        user.setName("Ana");
        user.setEmail("ana@test.es");
        user.setRole(role);
        if (companyId != null) {
            Company company = new Company();
            company.setId(companyId);
            user.setCompany(company);
        }
        return user;
    }

    @Test
    @DisplayName("lleva email, nombre, rol y empresa")
    void claims() {
        Jwt jwt = decoder.decode(tokenService.generateToken(user(Role.ADMIN, 7L)));

        assertThat(jwt.getSubject()).isEqualTo("ana@test.es");
        assertThat(jwt.getClaimAsString("name")).isEqualTo("Ana");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
        assertThat(((Number) jwt.getClaim("companyId")).longValue()).isEqualTo(7L);
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("pangestion");
    }

    @Test
    @DisplayName("un SUPERADMIN no lleva empresa")
    void superAdminWithoutCompany() {
        Jwt jwt = decoder.decode(tokenService.generateToken(user(Role.SUPERADMIN, null)));

        assertThat(jwt.hasClaim("companyId")).isFalse();
        assertThat(jwt.getClaimAsString("role")).isEqualTo("SUPERADMIN");
    }

    @Test
    @DisplayName("caduca a los minutos configurados")
    void expiration() {
        Jwt jwt = decoder.decode(tokenService.generateToken(user(Role.GESTOR, 1L)));

        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(60));
        assertThat(tokenService.getExpirationMinutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("un token firmado con otra clave no se acepta")
    void rejectsOtherKey() {
        JwtDecoder otherDecoder = NimbusJwtDecoder.withSecretKey(key("otra-clave-distinta-tambien-de-32-caracteres"))
                .macAlgorithm(MacAlgorithm.HS256).build();
        String token = tokenService.generateToken(user(Role.ADMIN, 1L));

        assertThatThrownBy(() -> otherDecoder.decode(token)).isInstanceOf(JwtException.class);
    }
}
