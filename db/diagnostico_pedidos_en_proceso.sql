-- Solo consulta. Ejecutar en Neon SQL Editor.
SELECT id, estado, fecha,
       NOW() AT TIME ZONE 'America/Lima' AS ahora_lima,
       (NOW() AT TIME ZONE 'America/Lima') - fecha AS tiempo_transcurrido,
       stock_productos_pendiente,
       stock_insumos_pendiente
FROM pedidos
WHERE estado = 'EN_PROCESO'
ORDER BY fecha;
-- Si el pedido tiene más de cinco minutos, revisar en la consola Spring Boot
-- el mensaje 'No se pudo confirmar el pedido' y la excepción completa.
-- NO actualizar manualmente el estado: omitiría el descuento de stock y los puntos.
