-- Ejecutar UNA VEZ en Neon antes de guardar acompañamientos.
-- Conserva todos los registros existentes.
BEGIN;
ALTER TABLE combo_items DROP CONSTRAINT IF EXISTS combo_items_tipo_item_check;
ALTER TABLE combo_items ADD CONSTRAINT combo_items_tipo_item_check
 CHECK (tipo_item IN ('PIZZA', 'BEBIDA', 'ACOMPANAMIENTO'));
COMMIT;
