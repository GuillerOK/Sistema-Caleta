package com.lacaleta.service;

import com.lacaleta.model.Categoria;
import com.lacaleta.model.Producto;
import com.lacaleta.repository.ComboItemRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PersonalizacionServiceTests {
    private final ProductoService catalogo = mock(ProductoService.class);
    private final ComboItemRepository comboItems = mock(ComboItemRepository.class);
    private final PersonalizacionService personalizacion = new PersonalizacionService(catalogo, comboItems);

    @Test
    void bebidasRenderizanSoloProductoConNombreYTamanoSecundario() {
        Producto bebida = producto(1, "Inca Kola", "1.5L", "bebidas", true);
        when(catalogo.listar()).thenReturn(List.of(bebida));

        var bloques = personalizacion.bloquesPara(bebida);

        assertEquals(1, bloques.size());
        assertEquals("BASE", bloques.get(0).rol());
        assertEquals("Inca Kola", bloques.get(0).opciones().get(0).etiqueta());
        assertTrue(bloques.get(0).opciones().get(0).descripcion().startsWith("1.5L"));
        verify(catalogo, never()).obtenerAcompanamientos();
        verify(catalogo, never()).obtenerBebidas();
    }

    @Test
    void combosSinSubitemsNoRecibenOpcionesNiExtrasDeOtrasCategorias() {
        Producto combo = producto(2, "Pacto Caleta", "Combo", "combos", true);
        when(catalogo.listar()).thenReturn(List.of(combo));

        var bloques = personalizacion.bloquesPara(combo);

        assertEquals(1, bloques.size());
        assertEquals("Pacto Caleta", bloques.get(0).opciones().get(0).etiqueta());
        assertTrue(bloques.stream().noneMatch(bloque ->
                "ITEM_CARRITO".equals(bloque.rol()) || "CONFIGURACION".equals(bloque.rol())));
        verify(catalogo, never()).obtenerAcompanamientos();
        verify(catalogo, never()).obtenerBebidas();
    }

    @Test
    void pizzasIncluyenSoloExtrasDisponiblesYUsanNombreRealComoTitulo() {
        Producto pizza = producto(3, "La Cómplice", "Familiar", "clasicas", true);
        Producto acompanamiento = producto(4, "Palitos de Ajo", "6 Unid", "acompanamientos", true);
        Producto noDisponible = producto(5, "Alitas BBQ", "6 Unid", "acompanamientos", false);
        Producto bebida = producto(6, "Inca Kola", "1.5L", "bebidas", true);
        when(catalogo.listar()).thenReturn(List.of(pizza));
        when(catalogo.obtenerAcompanamientos()).thenReturn(List.of(acompanamiento, noDisponible));
        when(catalogo.obtenerBebidas()).thenReturn(List.of(bebida));

        var bloques = personalizacion.bloquesPara(pizza);
        var bloqueAcompanamientos = bloques.stream()
                .filter(bloque -> "acompanamientos".equals(bloque.codigo()))
                .findFirst().orElseThrow();

        assertEquals(List.of("base", "masa", "cortesia", "acompanamientos", "bebidas"),
                bloques.stream().map(bloque -> bloque.codigo()).toList());
        assertEquals(1, bloqueAcompanamientos.opciones().size());
        assertEquals("Palitos de Ajo", bloqueAcompanamientos.opciones().get(0).etiqueta());
        assertTrue(bloqueAcompanamientos.opciones().get(0).descripcion().startsWith("6 Unid"));
    }


    @Test
    void duoCarnivoroConRecetaFijaNoSolicitaElegirPizza() {
        Producto combo = producto(20, "Dúo Carnívoro", "Incluye carnes y pizzas", "combos", true);
        Producto pizza = producto(21, "Pizza Familiar La Cómplice", "Tomate, queso y pepperoni", "clasicas", true);
        when(catalogo.listar()).thenReturn(List.of(combo, pizza));

        var bloques = personalizacion.bloquesPara(combo);

        assertTrue(bloques.stream().noneMatch(b -> "sabores-pizza".equals(b.codigo())));
    }

    @Test
    void combosMuestranSoloElSaborDePizzaSinTamanoNiDescripcion() {
        Producto combo = producto(30, "Paquete Clandestino", "Incluye pizza", "combos", true);
        Producto pizza = producto(31, "Pizza Familiar La Cómplice", "Tomate, queso y pepperoni", "clasicas", true);
        when(catalogo.listar()).thenReturn(List.of(combo, pizza));

        var bloques = personalizacion.bloquesPara(combo);
        var sabores = bloques.stream().filter(b -> "sabores-pizza".equals(b.codigo()))
                .findFirst().orElseThrow();

        assertEquals("La Cómplice", sabores.opciones().get(0).etiqueta());
        assertNull(sabores.opciones().get(0).descripcion());
    }


    @Test
    void pizzaPersonalMuestraLasPresentacionesDelMismoSabor() {
        Producto personal = producto(40, "La Cómplice Personal", "Personal", "promociones", true);
        Producto familiar = producto(41, "La Cómplice Familiar", "Familiar", "promociones", true);
        Producto otroSabor = producto(42, "La Coartada Familiar", "Familiar", "promociones", true);
        when(catalogo.listar()).thenReturn(List.of(personal, familiar, otroSabor));

        var bloques = personalizacion.bloquesPara(personal);
        var tamanos = bloques.stream().filter(b -> "base".equals(b.codigo()))
                .findFirst().orElseThrow();

        assertEquals(2, tamanos.opciones().size());
        assertEquals(List.of("Personal", "Familiar"), tamanos.opciones().stream()
                .map(opcion -> opcion.etiqueta()).toList());
    }

    @Test
    void losBloquesDeAdicionalesNoSeCreanCuandoNoHayOpcionesDisponibles() {
        Producto pizza = producto(50, "La Cómplice Personal", "Personal", "promociones", true);
        when(catalogo.listar()).thenReturn(List.of(pizza));
        when(catalogo.obtenerAcompanamientos()).thenReturn(List.of());
        when(catalogo.obtenerBebidas()).thenReturn(List.of());

        var bloques = personalizacion.bloquesPara(pizza);

        assertTrue(bloques.stream().noneMatch(b -> "ITEM_CARRITO".equals(b.rol())));
        assertTrue(bloques.stream().allMatch(b -> !b.opciones().isEmpty()));
    }

    private Producto producto(Integer id, String nombre, String tamano, String categoria, boolean disponible) {
        Categoria cat = new Categoria();
        cat.setId(categoria.hashCode());
        cat.setCodigo(categoria);

        Producto producto = new Producto();
        producto.setId(id);
        producto.setNombre(nombre);
        producto.setTamano(tamano);
        producto.setCategoria(cat);
        producto.setDisponible(disponible);
        producto.setPrecio(10.0);
        producto.setDescripcion("Descripción " + nombre);
        return producto;
    }
}
