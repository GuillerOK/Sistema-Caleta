-- Ejecutar una sola vez en la consola SQL de Neon.
CREATE TABLE IF NOT EXISTS combo_items (
    id BIGSERIAL PRIMARY KEY,
    combo_producto_id INTEGER NOT NULL REFERENCES productos(id) ON DELETE CASCADE,
    tipo_item VARCHAR(20) NOT NULL CHECK (tipo_item IN ('PIZZA','BEBIDA')),
    producto_id INTEGER NULL REFERENCES productos(id) ON DELETE SET NULL,
    cantidad INTEGER NOT NULL DEFAULT 1 CHECK (cantidad BETWEEN 1 AND 20),
    modalidad VARCHAR(20) NOT NULL DEFAULT 'DEFINIDA' CHECK (modalidad IN ('DEFINIDA','PERSONALIZAR')),
    tamano VARCHAR(30),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_combo_items_combo_producto ON combo_items(combo_producto_id);
