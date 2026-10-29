package com.pangestion.storage;

import com.pangestion.exception.BusinessException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** Imagen subida y ya validada por contenido (JPEG, PNG o WEBP) y por tamano. La usan productos y el logo de la empresa. */
public record UploadedImage(byte[] data, ImageType type) {

    public static UploadedImage read(MultipartFile file, long maxBytes) {
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("No se pudo leer la imagen");
        }
        if (data.length == 0 || data.length > maxBytes) {
            throw new BusinessException("La imagen debe ocupar como maximo " + (maxBytes / 1024 / 1024) + " MB");
        }
        ImageType type = ImageType.detect(data);
        if (type == null) {
            throw new BusinessException("Formato no valido: sube una imagen JPG, PNG o WEBP");
        }
        return new UploadedImage(data, type);
    }
}
