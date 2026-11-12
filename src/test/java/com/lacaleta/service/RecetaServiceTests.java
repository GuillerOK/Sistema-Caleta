package com.lacaleta.service;

import com.lacaleta.model.Insumo;
import com.lacaleta.model.Producto;
import com.lacaleta.repository.InsumoRepository;
import com.lacaleta.repository.ProductoRepository;
import com.lacaleta.repository.RecetaInsumoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecetaServiceTests {
    @Mock private ProductoRepository productos;
    @Mock private InsumoRepository insumos;
    @Mock private RecetaInsumoRepository recetas;
    @InjectMocks private RecetaService recetaService;

    @Test
    void reemplazaRecetaYActualizaStockConElIngredienteLimitante() {
        Producto producto = new Producto();
        producto.setId(7);
        Insumo queso = new Insumo();
        queso.setId(3);
        queso.setStockActual(10.0);
        Insumo salsa = new Insumo();
        salsa.setId(4);
        salsa.setStockActual(9.0);
        when(productos.bloquearPorId(7)).thenReturn(Optional.of(producto));
        when(insumos.findById(3)).thenReturn(Optional.of(queso));
        when(insumos.findById(4)).thenReturn(Optional.of(salsa));
        when(recetas.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        int stock = recetaService.guardar(7, List.of(
                new RecetaService.Linea(3, new BigDecimal("2")),
                new RecetaService.Linea(4, new BigDecimal("3"))));

        assertEquals(3, stock);
        assertEquals(3, producto.getStock());
        verify(recetas).deleteByProductoId(7);
        verify(recetas).flush();
        verify(recetas).saveAllAndFlush(anyList());
        verify(productos).save(producto);
    }
}
