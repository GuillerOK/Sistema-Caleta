package com.lacaleta.dto;

import java.util.List;

public record BloquePersonalizacionView(
        String codigo,
        String titulo,
        String rol,
        boolean multiple,
        int minSeleccion,
        int maxSeleccion,
        List<OpcionPersonalizacionView> opciones
) {
    public BloquePersonalizacionView {
        opciones = List.copyOf(opciones);
        if (minSeleccion < 0 || maxSeleccion < minSeleccion || maxSeleccion > opciones.size()) {
            throw new IllegalArgumentException("Rango de selección inválido para " + codigo);
        }
    }
}
