package com.pangestion.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Guardar, borrar y publicar imagenes en una carpeta del disco (una carpeta temporal en el test). */
@DisplayName("Unidad · LocalImageStorage")
class LocalImageStorageTest {

    @TempDir
    Path root;

    private LocalImageStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalImageStorage(root.toString());
    }

    @Test
    @DisplayName("guarda la imagen con un nombre aleatorio dentro de su carpeta")
    void savesWithRandomName() throws Exception {
        String path = storage.save("1/products", ImageTypeTest.PNG, ImageType.PNG);

        assertThat(path).startsWith("1/products/").endsWith(".png");
        assertThat(Files.readAllBytes(root.resolve(path))).isEqualTo(ImageTypeTest.PNG);
        assertThat(storage.save("1/products", ImageTypeTest.PNG, ImageType.PNG)).isNotEqualTo(path);
    }

    @Test
    @DisplayName("borra la imagen; borrar una ruta nula o que no existe no falla")
    void deletes() {
        String path = storage.save("1/products", ImageTypeTest.JPEG, ImageType.JPEG);

        storage.delete(path);

        assertThat(root.resolve(path)).doesNotExist();
        assertThatCode(() -> storage.delete(null)).doesNotThrowAnyException();
        assertThatCode(() -> storage.delete("1/products/no-existe.png")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("la URL pública es /uploads/{ruta}; sin imagen, null")
    void url() {
        assertThat(storage.url("1/products/a.webp")).isEqualTo("/uploads/1/products/a.webp");
        assertThat(storage.url(null)).isNull();
    }

    @Test
    @DisplayName("no deja salir de la carpeta de imágenes (../)")
    void blocksPathTraversal() {
        assertThatThrownBy(() -> storage.save("../fuera", ImageTypeTest.PNG, ImageType.PNG))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.delete("../../secreto.txt"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
