package com.pangestion.storage;

/**
 * Formatos de imagen aceptados. Se detectan por los primeros bytes del fichero (su "firma"),
 * no por la extension ni por el tipo que dice el navegador, que se pueden falsear.
 */
public enum ImageType {
    JPEG("jpg"),
    PNG("png"),
    WEBP("webp");

    private final String extension;

    ImageType(String extension) {
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }

    /** Devuelve el formato de la imagen, o null si no es un JPEG, PNG o WEBP. */
    public static ImageType detect(byte[] data) {
        if (data.length >= 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return JPEG;
        }
        if (data.length >= 8 && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return PNG;
        }
        if (data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return WEBP;
        }
        return null;
    }
}
