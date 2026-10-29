package com.pangestion.web;

import com.pangestion.model.Company;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Logo de la empresa: lo sube el ADMIN desde "Mi empresa" y lo reciben todos sus usuarios al entrar. */
@DisplayName("API · Logo de la empresa")
class CompanyLogoApiTest extends ApiTestSupport {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

    @Value("${app.uploads.dir}")
    String uploadsDir;

    private Company forn;
    private User admin;

    @BeforeEach
    void setUp() {
        forn = company("Forn");
        admin = user(forn, Role.ADMIN);
    }

    private MockMultipartFile file(byte[] data) {
        return new MockMultipartFile("file", "logo.png", "image/png", data);
    }

    private String uploadLogo() throws Exception {
        String json = mvc.perform(multipart("/api/company/logo").file(file(PNG)).with(as(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.replaceAll(".*\"logoUrl\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    private Path onDisk(String url) {
        return Path.of(uploadsDir).resolve(url.substring("/uploads/".length()));
    }

    @Test
    @DisplayName("subir (ADMIN): se guarda en {empresa}/logo y sale en la empresa y en el usuario de cualquier rol")
    void upload() throws Exception {
        String url = uploadLogo();

        assertThat(url).startsWith("/uploads/" + forn.getId() + "/logo/").endsWith(".png");
        assertThat(onDisk(url)).exists();
        mvc.perform(getJson("/api/company").with(as(admin))).andExpect(jsonPath("$.logoUrl").value(url));
        mvc.perform(getJson("/api/auth/me").with(as(user(forn, Role.CAMARERO))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyLogoUrl").value(url));
    }

    @Test
    @DisplayName("quitar: se borra del disco y la empresa queda sin logo")
    void remove() throws Exception {
        String url = uploadLogo();

        mvc.perform(delete("/api/company/logo").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoUrl").doesNotExist());
        assertThat(Files.exists(onDisk(url))).isFalse();
    }

    @Test
    @DisplayName("solo el ADMIN: un gestor recibe 403; un fichero que no es imagen, 409")
    void rules() throws Exception {
        mvc.perform(multipart("/api/company/logo").file(file(PNG)).with(as(user(forn, Role.GESTOR))))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/company/logo").file(file("hola".getBytes())).with(as(admin)))
                .andExpect(status().isConflict());
    }
}
