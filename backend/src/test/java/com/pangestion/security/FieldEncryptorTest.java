package com.pangestion.security;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cifrado AES-GCM de los datos personales que se guardan en la base de datos. */
@DisplayName("Unidad · FieldEncryptor")
class FieldEncryptorTest {

    @BeforeAll
    static void key() {
        FieldEncryptor.setKey(Base64.getEncoder().encodeToString(new byte[32]));
    }

    @Test
    @DisplayName("cifra y descifra; el texto guardado no contiene el original y cambia en cada cifrado")
    void roundTrip() {
        String encrypted = FieldEncryptor.encrypt("C/ Mayor 1, València");
        assertThat(encrypted).startsWith(FieldEncryptor.PREFIX).doesNotContain("Mayor");
        assertThat(FieldEncryptor.decrypt(encrypted)).isEqualTo("C/ Mayor 1, València");
        assertThat(FieldEncryptor.encrypt("C/ Mayor 1, València")).isNotEqualTo(encrypted);
    }

    @Test
    @DisplayName("nulos y datos antiguos sin cifrar se devuelven tal cual; no se cifra dos veces")
    void legacyAndNull() {
        assertThat(FieldEncryptor.encrypt(null)).isNull();
        assertThat(FieldEncryptor.decrypt(null)).isNull();
        assertThat(FieldEncryptor.decrypt("600000000")).isEqualTo("600000000");
        String encrypted = FieldEncryptor.encrypt("600000000");
        assertThat(FieldEncryptor.encrypt(encrypted)).isEqualTo(encrypted);
    }

    @Test
    @DisplayName("un texto cifrado manipulado no se descifra")
    void tampered() {
        String encrypted = FieldEncryptor.encrypt("600000000");
        char last = encrypted.charAt(encrypted.length() - 3);
        String tampered = encrypted.substring(0, encrypted.length() - 3) + (last == 'A' ? 'B' : 'A') + encrypted.substring(encrypted.length() - 2);
        assertThatThrownBy(() -> FieldEncryptor.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("la clave debe ser de 32 bytes")
    void keyLength() {
        assertThatThrownBy(() -> FieldEncryptor.setKey(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
