package com.pangestion.web;

import com.pangestion.model.Company;
import com.pangestion.model.Product;
import com.pangestion.model.Role;
import com.pangestion.model.User;
import com.pangestion.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fotos de los productos: subir, sustituir, quitar y servirlas en /uploads. */
@DisplayName("API · Imágenes de productos")
class ProductImageApiTest extends ApiTestSupport {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 4, 5, 6};

    @Value("${app.uploads.dir}")
    String uploadsDir;

    private User gestor;
    private Product barra;

    @BeforeEach
    void setUp() {
        Company forn = company("Forn");
        gestor = user(forn, Role.GESTOR);
        barra = product(category(forn, "Pans"), "Barra", "1.10");
    }

    private MockMultipartFile file(byte[] data, String name) {
        return new MockMultipartFile("file", name, "image/png", data);
    }

    private Path onDisk(String imageUrl) {
        return Path.of(uploadsDir).resolve(imageUrl.substring("/uploads/".length()));
    }

    private String upload(byte[] data) throws Exception {
        String json = mvc.perform(multipart("/api/products/{id}/image", barra.getId()).file(file(data, "foto.png")).with(as(gestor)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.replaceAll(".*\"imageUrl\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    @Test
    @DisplayName("subir: se guarda en la carpeta de la empresa y se sirve sin login en /uploads")
    void upload() throws Exception {
        String url = upload(PNG);

        assertThat(url).startsWith("/uploads/" + barra.getCompany().getId() + "/products/").endsWith(".png");
        assertThat(onDisk(url)).exists();
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().bytes(PNG));
    }

    @Test
    @DisplayName("sustituir: la imagen anterior se borra del disco")
    void replace() throws Exception {
        String first = upload(PNG);
        String second = upload(JPEG);

        assertThat(second).endsWith(".jpg").isNotEqualTo(first);
        assertThat(onDisk(first)).doesNotExist();
        assertThat(onDisk(second)).exists();
    }

    @Test
    @DisplayName("quitar: el producto se queda sin imagen y se borra el fichero")
    void remove() throws Exception {
        String url = upload(PNG);

        mvc.perform(deleteRequest("/api/products/{id}/image", barra.getId()).with(as(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").doesNotExist());
        assertThat(onDisk(url)).doesNotExist();
    }

    @Test
    @DisplayName("un fichero que no es imagen (aunque se llame .png): 409")
    void rejectsNonImages() throws Exception {
        mvc.perform(multipart("/api/products/{id}/image", barra.getId())
                        .file(file("<script>alert(1)</script>".getBytes(), "virus.png")).with(as(gestor)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Formato no valido: sube una imagen JPG, PNG o WEBP"));
    }

    @Test
    @DisplayName("un fichero vacío o de más de 2 MB: 409")
    void rejectsSize() throws Exception {
        mvc.perform(multipart("/api/products/{id}/image", barra.getId()).file(file(new byte[0], "vacia.png")).with(as(gestor)))
                .andExpect(status().isConflict());

        byte[] big = new byte[2 * 1024 * 1024 + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        mvc.perform(multipart("/api/products/{id}/image", barra.getId()).file(file(big, "grande.png")).with(as(gestor)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("La imagen debe ocupar como maximo 2 MB"));
    }

    @Test
    @DisplayName("al borrar el producto se borra también su imagen")
    void deleteProductDeletesImage() throws Exception {
        String url = upload(PNG);

        mvc.perform(deleteRequest("/api/products/{id}", barra.getId()).with(as(gestor))).andExpect(status().isNoContent());

        assertThat(Files.exists(onDisk(url))).isFalse();
    }
}
