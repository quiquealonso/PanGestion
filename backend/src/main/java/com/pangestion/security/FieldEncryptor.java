package com.pangestion.security;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifra datos personales (telefono, CIF/NIF, direccion) con AES-256-GCM antes de guardarlos en la base de datos.
 * El texto cifrado lleva el prefijo "enc:v1:" para distinguirlo de los datos antiguos sin cifrar, que se leen tal cual
 * hasta que SensitiveDataMigration los cifra.
 */
public final class FieldEncryptor {

    public static final String PREFIX = "enc:v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private static volatile SecretKeySpec key;
    private static final SecureRandom RANDOM = new SecureRandom();

    private FieldEncryptor() {
    }

    /** Clave en Base64 (32 bytes). La fija EncryptionConfig al arrancar. */
    public static void setKey(String base64Key) {
        byte[] bytes = Base64.getDecoder().decode(base64Key);
        if (bytes.length != 32) {
            throw new IllegalArgumentException("app.encryption.key debe ser de 32 bytes en Base64 (openssl rand -base64 32)");
        }
        key = new SecretKeySpec(bytes, "AES");
    }

    public static String encrypt(String plain) {
        if (plain == null || plain.startsWith(PREFIX)) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar el dato", e);
        }
    }

    public static String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return stored; // dato antiguo sin cifrar
        }
        try {
            byte[] data = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES));
            return new String(cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("No se pudo descifrar el dato (¿ha cambiado app.encryption.key?)", e);
        }
    }

    private static SecretKeySpec requireKey() {
        if (key == null) {
            throw new IllegalStateException("Falta app.encryption.key");
        }
        return key;
    }
}
