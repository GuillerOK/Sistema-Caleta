package com.lacaleta.service;

import com.lacaleta.model.Producto;
import com.lacaleta.repository.ComboRepository;
import com.lacaleta.repository.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductoServiceCapacityTests {
    @Mock private ProductoRepository productoRepository;
    @Mock private ComboRepository comboRepository;
    @Mock private DisponibilidadCatalogoService disponibilidadCatalogo;
    @InjectMocks private ProductoService productoService;

    @Test
    void promocionesSeAgrupanPorCapacidadYMantienenCompatibilidadConProductosAntiguos() {
        when(productoRepository.findAll()).thenReturn(List.of(
                producto(1, "Promo individual", "1 persona", null, true),
                producto(2, "Promo dúo", "2 personas", null, true),
                producto(3, "Promo grupo", "3-4 personas", null, true),
                producto(4, "Promo familiar", "5 a más", null, true),
                producto(5, "Promo personal antigua", null, "Personal", true),
                producto(6, "Dúo antiguo", null, "Dúo", true),
                producto(7, "Trío antiguo", null, "Trío", true),
                producto(8, "Banquete antiguo", null, "Familiar", true),
                producto(9, "Promo oculta", "1 persona", null, false)));

        assertEquals(List.of(1, 5), ids(productoService.obtenerPromos1Persona()));
        assertEquals(List.of(2, 6), ids(productoService.obtenerPromosDuo()));
        assertEquals(List.of(3, 7), ids(productoService.obtenerPromosTrio()));
        assertEquals(List.of(4, 8), ids(productoService.obtenerPromosFamiliares()));
    }

    private Producto producto(Integer id, String nombre, String capacidad, String tamano, boolean disponible) {
        Producto producto = new Producto();
        producto.setId(id);
        producto.setNombre(nombre);
        producto.setCapacidadPersonas(capacidad);
        producto.setTamano(tamano);
        producto.setDisponible(disponible);
        producto.setStock(1);
        return producto;
    }

    private List<Integer> ids(List<Producto> productos) {
        return productos.stream().map(Producto::getId).toList();
    }
}
