-- Ejecutar una sola vez en el SQL Editor de Neon, antes de subir nuevas imágenes.
CREATE TABLE IF NOT EXISTS imagenes_productos (
    id VARCHAR(36) PRIMARY KEY,
    tipo_mime VARCHAR(32) NOT NULL CHECK (tipo_mime IN ('image/png','image/jpeg')),
    contenido BYTEA NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
-- Las imágenes subidas anteriormente al disco de Render no se recuperan desde esta tabla.
-- Esas imágenes deben volver a cargarse si ya no están disponibles en el servidor.
