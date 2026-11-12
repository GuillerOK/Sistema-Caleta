package com.lacaleta.dto;

import java.util.List;
import java.util.Map;

public record CheckoutRequest(String email, List<Item> items, String metodoPago,
                              String tipoEntrega, String direccionEntrega, String claveCompra,
                              String codigoCupon) {
    public CheckoutRequest(String email, List<Item> items, String metodoPago,
                           String tipoEntrega, String direccionEntrega, String claveCompra) {
        this(email, items, metodoPago, tipoEntrega, direccionEntrega, claveCompra, null);
    }
    public record Item(Integer productoId, Integer cantidad, Map<String, List<String>> personalizaciones) {
        public Item(Integer productoId, Integer cantidad) { this(productoId, cantidad, Map.of()); }
    }
}
