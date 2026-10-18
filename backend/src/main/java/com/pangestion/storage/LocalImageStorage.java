package com.pangestion.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Guarda las imagenes en una carpeta del disco del servidor (por defecto ./uploads). */
@Slf4j
@Component
public class LocalImageStorage implements ImageStorage {

    private final Path root;

    public LocalImageStorage(@Value("${app.uploads.dir}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    @Override
    public String save(String folder, byte[] data, ImageType type) {
        // Nombre aleatorio: nunca se usa el nombre del fichero que sube el usuario
        String path = folder + "/" + UUID.randomUUID() + "." + type.extension();
        Path target = resolve(path);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, data);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la imagen", e);
        }
        return path;
    }

    @Override
    public void delete(String path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(path));
        } catch (IOException e) {
            log.warn("No se pudo borrar la imagen {}", path, e);
        }
    }

    @Override
    public String url(String path) {
        return path == null ? null : "/uploads/" + path;
    }

    /** Ruta en disco, comprobando que no se sale de la carpeta de imagenes. */
    private Path resolve(String path) {
        Path resolved = root.resolve(path).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Ruta de imagen no valida");
        }
        return resolved;
    }
}
