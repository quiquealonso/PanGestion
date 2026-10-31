package com.pangestion.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.pangestion.model.Role;
import com.pangestion.security.TokenService;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Seguridad de la API:
 * - /api/auth/login y /api/public/** son publicos (login, catalogo y cuentas de clientes). Con las cuentas de cliente
 *   activas (app.customer-accounts.enabled), hacer un pedido y ver la cuenta necesitan el token de un cliente (CLIENTE).
 * - Los clientes (CLIENTE) no pueden usar nada de la API de gestion.
 * - /api/companies (alta de panaderias): solo SUPERADMIN.
 * - /api/users: SUPERADMIN (todos) y ADMIN (los de su empresa).
 * - Datos de la propia empresa (PUT /api/company): solo ADMIN.
 * - Lectura (GET) para cualquier usuario autenticado; escritura para ADMIN y GESTOR.
 * Cada usuario solo ve los datos de su empresa: los servicios filtran por el companyId del token.
 */
@Configuration
public class SecurityConfig {

    /** Roles de gestion (todos menos los clientes de la web publica) */
    private static final String[] STAFF_ROLES = Arrays.stream(Role.values()).map(Role::name).toArray(String[]::new);

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.customer-accounts.enabled:false}")
    private boolean customerAccountsEnabled;

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin())) // consola H2
                .authorizeHttpRequests(auth -> auth
                        // Envios posteriores de una respuesta asincrona (avisos SSE): la peticion ya se autorizo al abrirla
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Clientes de la web publica: con cuentas activas, pedir y su cuenta solo con su token
                        .requestMatchers(HttpMethod.POST, "/api/public/orders").access(customerAccountsEnabled
                                ? AuthorityAuthorizationManager.hasRole(TokenService.CUSTOMER_ROLE)
                                : (authentication, context) -> new AuthorizationDecision(true))
                        .requestMatchers("/api/public/account/me").hasRole(TokenService.CUSTOMER_ROLE)
                        .requestMatchers("/api/auth/login", "/api/public/**", "/uploads/**", "/h2-console/**", "/error").permitAll()
                        .requestMatchers("/api/companies/**").hasRole("SUPERADMIN")
                        .requestMatchers("/api/users/**").hasAnyRole("SUPERADMIN", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/company").hasRole("ADMIN")
                        .requestMatchers("/api/company/logo").hasRole("ADMIN")
                        .requestMatchers("/api/reports/**").hasAnyRole("ADMIN", "GESTOR", "VISOR")
                        .requestMatchers("/api/kitchen/**").hasAnyRole("ADMIN", "GESTOR", "CAMARERO", "COCINA")
                        .requestMatchers("/api/delivery/**").hasAnyRole("ADMIN", "GESTOR", "REPARTIDOR")
                        .requestMatchers("/api/events/**", "/api/auth/**").hasAnyRole(STAFF_ROLES)
                        // Cocina y repartidor solo usan su pantalla: el resto de la API (ni siquiera para leer) no es para ellos
                        .requestMatchers(HttpMethod.GET, "/api/**").access(AuthorizationManagers.allOf(
                                AuthenticatedAuthorizationManager.authenticated(),
                                AuthorizationManagers.not(AuthorityAuthorizationManager.hasAnyRole("COCINA", "REPARTIDOR",
                                        TokenService.CUSTOMER_ROLE))))
                        .requestMatchers("/api/service/**").hasAnyRole("ADMIN", "GESTOR", "CAMARERO")
                        .requestMatchers("/api/**").hasAnyRole("ADMIN", "GESTOR")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    /** Convierte el claim "role" del token en la autoridad ROLE_xxx. */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    private SecretKey secretKey() {
        return new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey()));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(secretKey()).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
