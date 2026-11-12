-- Ejecutar en Neon SQL Editor. No elimina registros existentes.
-- La columna estado ya existe en la aplicación.
ALTER TABLE pedidos ALTER COLUMN estado TYPE VARCHAR(20);
CREATE INDEX IF NOT EXISTS idx_pedidos_estado_fecha ON pedidos (estado, fecha);
-- Pedidos anteriores no cambian de estado.
-- La aplicación cambia EN_PROCESO a REALIZADO al transcurrir 5 minutos.
