package com.lacaleta.dto;

import java.math.BigDecimal;

public record OpcionPersonalizacionView(
        String clave,
        Integer productoId,
        String etiqueta,
        String descripcion,
        BigDecimal precio,
        String imagenUrl
) {
}
