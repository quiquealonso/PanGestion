package com.pangestion.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Columna de texto cifrada en la base de datos: @Convert(converter = EncryptedString.class). */
@Converter
public class EncryptedString implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return FieldEncryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return FieldEncryptor.decrypt(dbData);
    }
}
