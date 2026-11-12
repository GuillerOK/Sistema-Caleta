# Combos, bebidas y recetas de inventario

## 1. Poblar la tabla `combos` que está vacía

En Neon SQL Editor, ejecutar `vincular-combos-neon.sql`. El script primero copia desde `productos` los artículos existentes en la categoría Combos y las promociones que describen pizzas/bebidas/acompañamientos a la tabla `combos`. Después sincroniza el catálogo sin borrar registros. Así la tabla `combos` deja de estar vacía y las filas existentes se conservan.

El script usa las columnas confirmadas: `id`, `nombre`, `descripcion`, `precio`, `imagen_url` y `activo`. Si una tabla tiene políticas de seguridad o restricciones adicionales, Neon podría necesitar permisos de escritura para el usuario de la conexión.

## 2. Completar recetas faltantes

Ejecutar `completar-recetas-pizzas.sql` después de confirmar que los nombres de insumos de `insumos` coinciden con los que utiliza el script. El SQL añade base e ingredientes adicionales por perfil a las pizzas sin receta o con receta parcial, sin duplicar pares producto/insumo ya existentes. Las cantidades son valores iniciales estimados y deben validarse contra la receta de cocina real antes de producción.

## 3. Cómo se descuenta un combo personalizado

El combo no debe tener una receta fija de pizza: el cliente puede elegir otra pizza en cada pedido. `PedidoService` registra la selección de pizza y bebida en las personalizaciones; descuenta stock y receta de la pizza seleccionada y stock de la bebida elegida. Para los acompañamientos fijos que aparecen en la descripción del combo, el SQL agrega al combo la receta de insumos del acompañamiento existente (por ejemplo, Alitas BBQ o Palitos de Ajo). No se copia una receta fija de pizza al combo, para evitar doble descuento.

## 4. Bebidas en el selector

El selector de bebida dentro del combo filtra por presentación requerida en la descripción del combo, pero muestra solamente el sabor/marca (por ejemplo, `Inca Kola`, no `Inca Kola 1.5L`). El precio individual de las opciones de configuración no se muestra; el precio del combo permanece en el producto principal.

## 5. Revisiones recomendadas

```sql
SELECT id, nombre, descripcion, precio, imagen_url, activo FROM combos ORDER BY id;
SELECT p.id, p.nombre, count(r.id) AS lineas_receta
FROM productos p LEFT JOIN receta_insumos r ON r.producto_id=p.id
GROUP BY p.id,p.nombre ORDER BY p.nombre;
```
