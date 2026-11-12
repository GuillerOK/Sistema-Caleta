-- Ejecutar UNA VEZ antes de iniciar la versión actualizada.
-- Los pedidos históricos conservan false para evitar doble descuento al confirmarlos.
ALTER TABLE pedidos ADD COLUMN IF NOT EXISTS stock_reservado BOOLEAN NOT NULL DEFAULT FALSE;
