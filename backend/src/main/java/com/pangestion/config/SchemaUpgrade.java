package com.pangestion.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Ajustes de esquema que ddl-auto=update no hace solo. Hibernate limita la columna users.role a los roles
 * que existian al crearla: en PostgreSQL con un CHECK y en H2 con un tipo ENUM. Al anadir los roles CAMARERO y COCINA,
 * las bases de datos ya creadas lo rechazan. Al arrancar se quita ese limite (el enum de Java ya controla
 * los valores): se borra el CHECK antiguo o se pasa la columna a texto.
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class SchemaUpgrade implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(String... args) {
        widenEnumColumn("users", "role", "REPARTIDOR", 12);
        widenEnumColumn("orders", "status", "TRANSPORTANDO", 15);
    }

    /**
     * Hibernate crea las columnas de enums como ENUM (H2) o con un CHECK (PostgreSQL) con los valores que existian, y
     * ddl-auto=update no las amplia. Si a la columna le falta el valor mas nuevo, se quita el CHECK y pasa a texto.
     */
    private void widenEnumColumn(String table, String column, String newestValue, int length) {
        List<String> outdated = jdbc.queryForList("""
                select tc.constraint_name from information_schema.table_constraints tc
                join information_schema.check_constraints cc on cc.constraint_name = tc.constraint_name
                where lower(tc.table_name) = ? and tc.constraint_type = 'CHECK'
                  and lower(cc.check_clause) like ? and cc.check_clause not like ?
                  and lower(cc.check_clause) not like '% is not null'
                """, String.class, table, "%" + column + "%", "%" + newestValue + "%");
        for (String name : outdated) {
            jdbc.execute("alter table " + table + " drop constraint \"" + name.replace("\"", "") + "\"");
            log.info("Esquema: quitado el CHECK antiguo de {}.{} ({})", table, column, name);
        }

        Integer narrow = jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where lower(table_name) = ? and lower(column_name) = ?
                  and (lower(data_type) = 'enum' or character_maximum_length < ?)
                """, Integer.class, table, column, length);
        if (narrow != null && narrow > 0) {
            jdbc.execute("alter table " + table + " alter column " + column + " set data type varchar(" + length + ")");
            log.info("Esquema: {}.{} pasa a varchar({}) para admitir {}", table, column, length, newestValue);
        }
    }
}
