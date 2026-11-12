-- Solo consulta: muestra la estructura de la tabla combos que ya existe en Neon.
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = 'combos'
ORDER BY ordinal_position;
