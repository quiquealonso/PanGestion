package com.pangestion.model;

import com.pangestion.security.EncryptedString;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Cuenta de un cliente de la web publica de pedidos. Es una sola para todos los negocios: al pedir en uno,
 * se crea o se usa su ficha de cliente (Customer) en ese negocio con el mismo email.
 */
@Entity
@Table(name = "customer_accounts")
@Getter
@Setter
@NoArgsConstructor
public class CustomerAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    /** Siempre en minusculas */
    @Column(nullable = false, unique = true, length = 120)
    private String email;

    /** Cifrado en la base de datos (EncryptedString) */
    @Convert(converter = EncryptedString.class)
    @Column(length = 100)
    private String phone;

    /** Hash BCrypt */
    @Column(nullable = false)
    private String password;

    /** true cuando ha confirmado el email con el enlace que se le envia */
    @Column(nullable = false)
    private boolean verified;

    /** SHA-256 del enlace de confirmacion pendiente (el enlace en claro solo va en el email) */
    @Column(name = "verification_token_hash", length = 64)
    private String verificationTokenHash;

    @Column(name = "verification_expires_at")
    private LocalDateTime verificationExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
