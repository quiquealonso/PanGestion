package com.pangestion.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Detectar el formato de una imagen por sus primeros bytes, no por la extension. */
@DisplayName("Unidad · ImageType")
class ImageTypeTest {

    /** Cabeceras minimas de cada formato. */
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

    @Test
    @DisplayName("reconoce JPEG, PNG y WEBP")
    void detectsSupportedFormats() {
        assertThat(ImageType.detect(JPEG)).isEqualTo(ImageType.JPEG);
        assertThat(ImageType.detect(PNG)).isEqualTo(ImageType.PNG);
        assertThat(ImageType.detect(WEBP)).isEqualTo(ImageType.WEBP);
    }

    @Test
    @DisplayName("cada formato tiene su extensión")
    void extensions() {
        assertThat(ImageType.JPEG.extension()).isEqualTo("jpg");
        assertThat(ImageType.PNG.extension()).isEqualTo("png");
        assertThat(ImageType.WEBP.extension()).isEqualTo("webp");
    }

    @Test
    @DisplayName("rechaza otros ficheros (texto, GIF, PDF) y ficheros vacíos o cortados")
    void rejectsOtherFiles() {
        assertThat(ImageType.detect("hola, no soy una imagen".getBytes())).isNull();
        assertThat(ImageType.detect("GIF89a....".getBytes())).isNull();
        assertThat(ImageType.detect("%PDF-1.7".getBytes())).isNull();
        assertThat(ImageType.detect(new byte[0])).isNull();
        assertThat(ImageType.detect(new byte[]{(byte) 0xFF, (byte) 0xD8})).isNull();
        assertThat(ImageType.detect(new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'A', 'V', 'I', ' '})).isNull();
    }
}
