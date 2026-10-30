package com.pangestion.config;

import com.pangestion.security.FieldEncryptor;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Al arrancar: fija la clave de cifrado, ensancha las columnas cifradas (el texto cifrado ocupa mas que el original)
 * y cifra los datos personales que se guardaron antes de existir el cifrado.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class SensitiveDataMigration implements CommandLineRunner {

    /** tabla -> columnas cifradas, con la longitud que necesitan */
    private static final Map<String, Map<String, Integer>> ENCRYPTED = Map.of(
            "customers", Map.of("phone", 100, "tax_id", 100),
            "orders", Map.of("delivery_address", 800));

    private final JdbcTemplate jdbc;

    @Value("${app.encryption.key}")
    private String encryptionKey;

    /** Se fija al crear el bean, antes de que se ejecute ningun CommandLineRunner (datos de ejemplo). */
    @PostConstruct
    void initKey() {
        FieldEncryptor.setKey(encryptionKey);
    }

    @Override
    public void run(String... args) {
        ENCRYPTED.forEach((table, columns) -> columns.forEach((column, length) -> {
            widen(table, column, length);
            encryptExisting(table, column);
        }));
    }

    private void widen(String table, String column, int length) {
        Integer narrow = jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where lower(table_name) = ? and lower(column_name) = ? and character_maximum_length < ?
                """, Integer.class, table, column, length);
        if (narrow != null && narrow > 0) {
            jdbc.execute("alter table " + table + " alter column " + column + " set data type varchar(" + length + ")");
            log.info("Esquema: {}.{} pasa a varchar({}) para guardarlo cifrado", table, column, length);
        }
    }

    private void encryptExisting(String table, String column) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id, " + column + " as val from " + table + " where " + column + " is not null and " + column + " not like ?",
                FieldEncryptor.PREFIX + "%");
        for (Map<String, Object> row : rows) {
            jdbc.update("update " + table + " set " + column + " = ? where id = ?",
                    FieldEncryptor.encrypt((String) row.get("val")), row.get("id"));
        }
        if (!rows.isEmpty()) {
            log.info("Cifrados {} valores de {}.{}", rows.size(), table, column);
        }
    }
}
