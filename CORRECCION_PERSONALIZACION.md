# Corrección de personalización de La Caleta

## Cambios
- El selector de tamaño de pizza busca las presentaciones disponibles del mismo sabor (por ejemplo, La Cómplice Personal y La Cómplice Familiar), incluso cuando están en la categoría Promociones.
- El selector de tamaño muestra `Personal`, `Familiar`, `Mediana` o `Grande`, no la descripción completa del producto.
- Las categorías de acompañamientos y bebidas se localizan por códigos que incluyan acompañamiento/adicional/entrada o bebida/gaseosa/refresco, con o sin tildes.
- Los bloques sin opciones no se muestran ni bloquean el botón de continuar.
- Se agregaron pruebas unitarias para variantes de tamaño y bloques sin opciones.

## Aplicación
1. Reemplaza el proyecto por el contenido de este ZIP o copia estos archivos a tu proyecto actual.
2. Reinicia Spring Boot.
3. En el navegador realiza una recarga completa (Ctrl+F5).
4. Verifica que el catálogo tenga productos activos de tamaño Personal/Familiar y productos activos de acompañamientos/bebidas.

No requiere ejecutar scripts SQL nuevos en Neon.
