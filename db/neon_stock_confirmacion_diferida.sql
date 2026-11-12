-- Ejecutar una sola vez en Neon ANTES de arrancar la nueva versión.
ALTER TABLE pedidos ADD COLUMN IF NOT EXISTS stock_productos_pendiente TEXT;
ALTER TABLE pedidos ADD COLUMN IF NOT EXISTS stock_insumos_pendiente TEXT;
-- Los pedidos EN_PROCESO antiguos no contienen un desglose pendiente;
-- no se reconstruye ni se descuenta su stock para evitar dobles consumos.
