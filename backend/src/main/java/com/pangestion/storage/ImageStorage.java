package com.pangestion.storage;

/**
 * Donde se guardan las imagenes. La base de datos solo guarda la ruta que devuelve save().
 * Hoy se guardan en disco (LocalImageStorage); para usar S3/R2 bastaria con otra implementacion.
 */
public interface ImageStorage {

    /**
     * Guarda una imagen ya validada.
     * @param folder carpeta logica, por ejemplo "1/products" (empresa/tipo)
     * @return ruta relativa para guardar en la base de datos, por ejemplo "1/products/uuid.webp"
     */
    String save(String folder, byte[] data, ImageType type);

    /** Borra la imagen; no hace nada si no existe. */
    void delete(String path);

    /** URL publica con la que el navegador pide la imagen. */
    String url(String path);
}
