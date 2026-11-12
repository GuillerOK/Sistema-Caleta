-- Sincroniza la tabla combos con el catálogo existente de La Caleta.
-- IMPORTANTE: antes estaba vacía; primero se COPIAN a combos los combos que ya existen en productos.
-- Luego se asegura que cada fila de combos tenga su producto de catálogo para detalle/carrito.
-- No borra productos ni combos existentes. Ejecutar en Neon SQL Editor.
BEGIN;

-- Asegurar que existe la categoría antes de leer el catálogo.
INSERT INTO categorias (codigo, nombre, descripcion, icono)
SELECT 'combos', 'Combos & Promociones', 'Pizzas con complementos', 'bi-tag'
WHERE NOT EXISTS (SELECT 1 FROM categorias WHERE lower(trim(codigo)) IN ('combos','combo'));

-- 1) Poblar combos desde productos existentes cuando combos está vacía o le faltan registros.
INSERT INTO combos (nombre, descripcion, precio, imagen_url, activo)
SELECT DISTINCT ON (lower(trim(p.nombre)))
       p.nombre,
       p.descripcion,
       p.precio::numeric,
       p.imagen_url,
       COALESCE(p.disponible, true)
FROM productos p
JOIN categorias cat ON cat.id = p.categoria_id
WHERE (lower(trim(cat.codigo)) IN ('combos', 'combo')
         OR (lower(trim(cat.codigo)) IN ('promociones', 'promocion')
             AND (lower(coalesce(p.descripcion,'')) LIKE '%pizza%' OR lower(coalesce(p.descripcion,'')) LIKE '%bebida%'
                  OR lower(coalesce(p.descripcion,'')) LIKE '%gaseosa%' OR lower(coalesce(p.descripcion,'')) LIKE '%alitas%'
                  OR lower(coalesce(p.descripcion,'')) LIKE '%palitos%')))
  AND NULLIF(trim(p.nombre), '') IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM combos c
      WHERE lower(trim(c.nombre)) = lower(trim(p.nombre))
  )
ORDER BY lower(trim(p.nombre)), p.id;

-- 3) Crear en productos solo los combos que estén en combos pero aún no estén en el catálogo.
INSERT INTO productos (nombre, descripcion, precio, imagen_url, categoria_id, tamano, disponible, stock, version)
SELECT c.nombre, c.descripcion, c.precio::double precision, c.imagen_url,
       cat.id, 'Combo', c.activo, 1000, 0
FROM combos c
CROSS JOIN LATERAL (
    SELECT id FROM categorias WHERE lower(trim(codigo)) IN ('combos','combo') ORDER BY id LIMIT 1
) cat
WHERE NOT EXISTS (
    SELECT 1 FROM productos p
    WHERE lower(trim(p.nombre)) = lower(trim(c.nombre))
);

-- 4) Mantener precio, descripción, imagen y estado sincronizados desde combos hacia productos.
UPDATE productos p
SET descripcion = c.descripcion,
    precio = c.precio::double precision,
    imagen_url = COALESCE(NULLIF(c.imagen_url, ''), p.imagen_url),
    disponible = c.activo
FROM combos c
JOIN categorias cat ON lower(trim(cat.codigo)) IN ('combos','combo','promociones','promocion')
WHERE p.categoria_id = cat.id
  AND lower(trim(p.nombre)) = lower(trim(c.nombre));

COMMIT;

-- Comprobación posterior:
-- SELECT id, nombre, descripcion, precio, imagen_url, activo FROM combos ORDER BY id;
