package com.lacaleta.service;

import com.lacaleta.dto.BloquePersonalizacionView;
import com.lacaleta.dto.OpcionPersonalizacionView;
import com.lacaleta.model.Producto;
import com.lacaleta.model.ComboItem;
import com.lacaleta.repository.ComboItemRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.text.Normalizer;

@Service
public class PersonalizacionService {
    private static final String ROL_BASE = "BASE";
    private static final String ROL_CONFIGURACION = "CONFIGURACION";
    private static final String ROL_ITEM_CARRITO = "ITEM_CARRITO";

    private final ProductoService productos;
    private final ComboItemRepository comboItems;

    public PersonalizacionService(ProductoService productos, ComboItemRepository comboItems) {
        this.productos = productos;
        this.comboItems = comboItems;
    }

    public List<BloquePersonalizacionView> bloquesPara(Producto producto) {
        List<BloquePersonalizacionView> bloques = new ArrayList<>();
        String descripcionConfiguracion = normalizar(producto.getDescripcion() == null ? "" : producto.getDescripcion());
        boolean promocionConfigurada = esCategoria(producto, "promociones", "promocion")
                && (descripcionConfiguracion.contains("pizza incluida definida:")
                    || descripcionConfiguracion.contains("pizza personalizar")
                    || descripcionConfiguracion.contains("no incluye pizza"));
        boolean combo = esCategoria(producto, "combos") || promocionConfigurada
                || (esCategoria(producto, "promociones", "promocion") && !esPizza(producto));
        boolean pizza = esPizza(producto) && !promocionConfigurada;
        boolean bebida = esCategoria(producto, "bebida", "bebidas");
        Producto productoVisible = productos.aplicarDatosCombo(producto);
        List<Producto> catalogoDisponible = productos.listar().stream()
                .filter(productos::visibleEnCarta)
                .filter(opcion -> opcion.getId() != null)
                .toList();

        List<Producto> opcionesBase;
        if (pizza) {
            // Las presentaciones suelen estar registradas como productos separados:
            // "La Cómplice Personal" y "La Cómplice Familiar". Agrupamos por sabor
            // para que el selector de tamaño nunca quede vacío.
            String saborSeleccionado = clavePizza(producto);
            opcionesBase = catalogoDisponible.stream()
                    .filter(opcion -> esPizza(opcion)
                            || (esCategoria(opcion, "promocion", "promociones")
                                && saborSeleccionado.equals(clavePizza(opcion))))
                    .filter(opcion -> saborSeleccionado.equals(clavePizza(opcion)))
                    .toList();
        } else {
            opcionesBase = catalogoDisponible.stream()
                    .filter(opcion -> Objects.equals(opcion.getNombre(), producto.getNombre()))
                    .filter(opcion -> opcion.getCategoria() != null
                            && producto.getCategoria() != null
                            && Objects.equals(opcion.getCategoria().getId(), producto.getCategoria().getId()))
                    .toList();
        }

        if (opcionesBase.isEmpty()) {
            // El producto que abrió la pantalla siempre es una opción válida de respaldo.
            opcionesBase = List.of(productoVisible);
        } else if (combo) {
            opcionesBase = opcionesBase.stream().map(productos::aplicarDatosCombo).toList();
        }
        String tituloBase = pizza ? "Elige el tamaño" : bebida ? "Elige tu bebida" : combo ? "Combo" : "Producto";
        bloques.add(bloqueProductos("base", tituloBase, ROL_BASE, opcionesBase, false, 1, !pizza));

        if (combo) {
            List<ComboItem> configuracionItems = producto.getId() == null ? List.of() : comboItems.findByComboProductoIdOrderByIdAsc(producto.getId());
            if (!configuracionItems.isEmpty()) {
                // Solo las filas marcadas como PERSONALIZAR generan selectores para el cliente.
                for (ComboItem item : configuracionItems) {
                    if ("PIZZA".equalsIgnoreCase(item.getTipoItem()) && "PERSONALIZAR".equalsIgnoreCase(item.getModalidad())) {
                        List<Producto> opcionesPizza = catalogoDisponible.stream().filter(this::esPizza)
                                .filter(p -> item.getTamano() == null || coincideTamanoPizza(p, item.getTamano())).toList();
                        if (!opcionesPizza.isEmpty()) bloques.add(bloqueProductos("sabores-pizza-" + item.getId(),
                                "Elige " + item.getCantidad() + (item.getCantidad() == 1 ? " pizza" : " pizzas") + " (" + item.getTamano() + ")",
                                ROL_CONFIGURACION, opcionesPizza, item.getCantidad() > 1, item.getCantidad(), true));
                    } else if ("BEBIDA".equalsIgnoreCase(item.getTipoItem()) && "PERSONALIZAR".equalsIgnoreCase(item.getModalidad())) {
                        List<Producto> opcionesBebida = productos.obtenerBebidas().stream().filter(productos::visibleEnCarta)
                                .filter(p -> item.getTamano() == null || coincidePresentacion(p, normalizar(item.getTamano()).replace(" ", ""))).toList();
                        if (!opcionesBebida.isEmpty()) bloques.add(bloqueProductos("sabor-bebida-" + item.getId(),
                                "Elige " + item.getCantidad() + (item.getCantidad() == 1 ? " bebida" : " bebidas"),
                                ROL_CONFIGURACION, opcionesBebida, item.getCantidad() > 1, item.getCantidad(), true));
                    } else if ("ACOMPANAMIENTO".equalsIgnoreCase(item.getTipoItem()) && "PERSONALIZAR".equalsIgnoreCase(item.getModalidad())) {
                        List<Producto> opcionesAcompanamiento = productos.obtenerAcompanamientos().stream()
                                .filter(productos::visibleEnCarta).filter(p -> p.getId() != null).toList();
                        if (!opcionesAcompanamiento.isEmpty()) bloques.add(bloqueProductos("acompanamiento-combo-" + item.getId(),
                                "Elige " + item.getCantidad() + (item.getCantidad() == 1 ? " acompañamiento" : " acompañamientos"),
                                ROL_CONFIGURACION, opcionesAcompanamiento, item.getCantidad() > 1, item.getCantidad(), true));
                    }
                }
            } else {
            String tamanoPizzaCombo = tamanoPizzaCombo(producto);
            List<Producto> pizzasDisponibles = catalogoDisponible.stream()
                    .filter(this::esPizza)
                    .filter(opcion -> tamanoPizzaCombo == null || coincideTamanoPizza(opcion, tamanoPizzaCombo))
                    .toList();
            // Los combos con receta propia (por ejemplo, Dúo Carnívoro) ya definen
            // sus ingredientes y no deben solicitar una pizza adicional al cliente.
            if (!pizzasDisponibles.isEmpty() && necesitaPizza(producto) && cantidadPizzasCombo(producto) > 0
                    && !tieneRecetaFijaConPizzaDefinida(producto)) {
                int cantidadPizzas = cantidadPizzasCombo(producto);
                bloques.add(bloqueProductos("sabores-pizza", cantidadPizzas > 1
                                ? "Elige los sabores de pizza (" + cantidadPizzas + ")"
                                : "Elige el sabor de pizza",
                        ROL_CONFIGURACION, pizzasDisponibles, cantidadPizzas > 1,
                        cantidadPizzas, true));
            }
            String tamanoBebida = tamanoBebidaCombo(producto);
            List<Producto> bebidasCombo = productos.obtenerBebidas().stream()
                    .filter(productos::visibleEnCarta)
                    // Los combos que prometen gaseosa deben ofrecer gaseosas, no chicha,
                    // refrescos ni otras bebidas. El tamaño se valida por separado.
                    .filter(opcion -> !requiereGaseosa(producto) || esGaseosa(opcion))
                    .filter(opcion -> tamanoBebida == null || coincidePresentacion(opcion, tamanoBebida))
                    .toList();
            if (!bebidasCombo.isEmpty() && necesitaBebida(producto) && cantidadBebidasCombo(producto) > 0) {
                int cantidadBebidas = cantidadBebidasCombo(producto);
                bloques.add(bloqueProductos("sabor-bebida", cantidadBebidas > 1 ? "Elige las bebidas (" + cantidadBebidas + ")" : "Elige el sabor de la bebida",
                        ROL_CONFIGURACION, bebidasCombo, cantidadBebidas > 1, cantidadBebidas, true));
            }
            }
        }

        if (pizza) {
            bloques.add(bloqueTexto("masa", "Elige el tipo de masa", List.of(
                    opcionTexto("tradicional", "Masa Tradicional", "Estándar"),
                    opcionTexto("delgada", "Delgada Crocante", "Sin costo extra")
            )));
            bloques.add(bloqueTexto("cortesia", "Cortesía de la casa", List.of(
                    opcionTexto("ajo", "Salsa de Ajo Especial (1 unid.)", "Receta artesanal Caleta"),
                    opcionTexto("pepperoncini", "Pepperoncini Caleta", "Ají encurtido tradicional para el borde"),
                    opcionTexto("oregano", "Sobre de Orégano y Ají Seco", "Condimento adicional")
            )));

        }

        // Los adicionales se ofrecen en el segundo paso tanto para pizzas como para combos.
        boolean comboTieneItemsConfigurados = combo && producto.getId() != null && !comboItems.findByComboProductoIdOrderByIdAsc(producto.getId()).isEmpty();
        if (pizza || (combo && !comboTieneItemsConfigurados)) {
            List<Producto> acompanamientos = productos.obtenerAcompanamientos().stream()
                    .filter(productos::visibleEnCarta)
                    .toList();
            if (!acompanamientos.isEmpty()) {
                bloques.add(bloqueProductos("acompanamientos", "Adicionales: acompañamientos", ROL_ITEM_CARRITO,
                        acompanamientos, true, 0, true));
            }
            List<Producto> bebidas = productos.obtenerBebidas().stream()
                    .filter(productos::visibleEnCarta)
                    .toList();
            if (!bebidas.isEmpty()) {
                bloques.add(bloqueProductos("bebidas", "Adicionales: bebidas", ROL_ITEM_CARRITO,
                        bebidas, true, 0, true));
            }
        }
        return List.copyOf(bloques);
    }

    private int cantidadPizzasCombo(Producto producto) {
        String descripcion = normalizar(Objects.toString(producto.getDescripcion(), ""));
        java.util.regex.Matcher configurada = java.util.regex.Pattern.compile("cantidad de pizzas:\\s*(\\d+)").matcher(descripcion);
        if (configurada.find()) return Math.max(0, Math.min(20, Integer.parseInt(configurada.group(1))));
        String texto = normalizar((producto.getNombre() == null ? "" : producto.getNombre())
                + " " + (producto.getDescripcion() == null ? "" : producto.getDescripcion()));
        if (texto.contains("trio") || texto.contains("trilogia") || texto.contains("3 pizzas")
                || texto.contains("tres pizzas")) return 3;
        if (texto.contains("duo") || texto.contains("2 pizzas") || texto.contains("dos pizzas")
                || texto.contains("banquete") || texto.contains("pacto")) return 2;
        return 1;
    }


    private int cantidadBebidasCombo(Producto producto) {
        String texto = normalizar(Objects.toString(producto.getDescripcion(), ""));
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("cantidad de bebidas:\\s*(\\d+)").matcher(texto);
        if (m.find()) return Math.max(0, Math.min(20, Integer.parseInt(m.group(1))));
        if (texto.contains("2 bebidas") || texto.contains("dos bebidas") || texto.contains("bebidas personales")) return 2;
        return 1;
    }

    private String tamanoPizzaCombo(Producto producto) {
        String texto = normalizar(Objects.toString(producto.getDescripcion(), ""));
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("tamano pizza combo:\\s*([^|]+)").matcher(texto);
        if (m.find()) return m.group(1).trim();
        String tamanoProducto = normalizar(Objects.toString(producto.getTamano(), ""));
        if (List.of("personal", "mediana", "familiar", "grande").contains(tamanoProducto)) return tamanoProducto;
        return null;
    }

    private boolean coincideTamanoPizza(Producto producto, String objetivo) {
        String tamano = normalizar(Objects.toString(producto.getTamano(), ""));
        String nombre = normalizar(Objects.toString(producto.getNombre(), ""));
        String esperado = normalizar(objetivo);
        return tamano.equals(esperado) || nombre.matches(".*\\b" + java.util.regex.Pattern.quote(esperado) + "\\b.*");
    }

    private boolean necesitaPizza(Producto producto) {
        String texto = normalizar((producto.getNombre() == null ? "" : producto.getNombre())
                + " " + (producto.getDescripcion() == null ? "" : producto.getDescripcion()));
        // Una pizza definida ya viene incluida; solo las opciones "personalizar"
        // deben abrir el selector para que el cliente elija el sabor.
        if (texto.contains("pizza incluida definida:")) return false;
        if (texto.contains("pizza personalizar")) return true;
        if (texto.contains("no incluye pizza") || texto.contains("solo bebida") || texto.contains("solo bebidas")) return false;
        return texto.contains("pizza") || texto.contains("banquete")
                || texto.contains("familiar") || texto.contains("duo") || texto.contains("trio");
    }

    private boolean tieneRecetaFijaConPizzaDefinida(Producto producto) {
        String nombre = normalizar(producto.getNombre() == null ? "" : producto.getNombre());
        // Este combo ya tiene una receta consolidada de carnes/insumos. No debe
        // añadir una pizza elegida por el cliente ni descontar una segunda receta.
        return nombre.contains("duo carnivoro");
    }

    private String tamanoBebidaCombo(Producto producto) {
        String texto = normalizar((producto.getNombre() == null ? "" : producto.getNombre())
                + " " + (producto.getDescripcion() == null ? "" : producto.getDescripcion()));
        if (texto.matches("(?s).*3\\s*l(\\.|itros?).*")) return "3l";
        if (texto.matches("(?s).*1[.,]?5\\s*l(\\.|itros?).*")) return "1.5l";
        if (texto.matches("(?s).*600\\s*ml.*")) return "600ml";
        if (texto.matches("(?s).*1\\s*l(\\.|itro)?(\\s|$).*")) return "1l";
        if (texto.matches("(?s).*bebida.*personal.*")) return "personal";
        return null;
    }


    private boolean requiereGaseosa(Producto producto) {
        String texto = normalizar((producto.getNombre() == null ? "" : producto.getNombre())
                + " " + (producto.getDescripcion() == null ? "" : producto.getDescripcion()));
        return texto.contains("gaseosa") || texto.contains("soda");
    }

    private boolean esGaseosa(Producto producto) {
        String nombre = normalizar(producto.getNombre() == null ? "" : producto.getNombre());
        // Marcas habituales de gaseosas del catálogo. Se excluyen chicha, agua y jugos.
        return nombre.contains("inca kola") || nombre.contains("coca cola")
                || nombre.contains("pepsi") || nombre.contains("sprite")
                || nombre.contains("fanta") || nombre.contains("7up")
                || nombre.contains("seven up") || nombre.contains("schweppes")
                || nombre.contains("gaseosa") || nombre.contains("guarana");
    }

    private boolean coincidePresentacion(Producto producto, String objetivo) {
        String tamano = normalizar(producto.getTamano() == null ? "" : producto.getTamano())
                .replaceAll("\\s+", "").replace(',', '.');
        String nombre = normalizar(producto.getNombre() == null ? "" : producto.getNombre())
                .replaceAll("\\s+", "").replace(',', '.');
        String buscado = objetivo.replaceAll("\\s+", "").replace(',', '.');
        if ("personal".equals(buscado)) {
            return tamano.contains("personal") || nombre.matches(".*\\bpersonal\\b.*");
        }
        return tamano.equals(buscado) || nombre.matches(".*" + java.util.regex.Pattern.quote(buscado)
                + "(?:$|[^a-z0-9]).*") || nombre.endsWith(buscado);
    }

    private boolean necesitaBebida(Producto producto) {
        String texto = normalizar((producto.getNombre() == null ? "" : producto.getNombre())
                + " " + (producto.getDescripcion() == null ? "" : producto.getDescripcion()));
        return texto.contains("bebida") || texto.contains("gaseosa") || texto.contains("inca kola")
                || texto.contains("coca cola") || texto.contains("refresco");
    }

    private String clavePizza(Producto producto) {
        String nombre = normalizar(producto.getNombre() == null ? "" : producto.getNombre());
        nombre = nombre.replaceFirst("^pizza\\s+", "");
        nombre = nombre.replaceAll("\\b(personal|familiar|mediana|grande|pequena|pequeña|individual|duo|trio)\\b", " ");
        nombre = nombre.replaceAll("\\b(tamano|tamaño)\\s*[:\\-]?\\s*", " ");
        return nombre.replaceAll("[^a-z0-9]+", " ").trim();
    }

    private String etiquetaTamanoPizza(Producto producto) {
        String tamano = producto.getTamano();
        if (tamano != null && !tamano.isBlank()) return tamano.trim();
        String nombre = normalizar(producto.getNombre() == null ? "" : producto.getNombre());
        if (nombre.matches(".*\\bpersonal\\b.*")) return "Personal";
        if (nombre.matches(".*\\bfamiliar\\b.*")) return "Familiar";
        if (nombre.matches(".*\\bmediana\\b.*")) return "Mediana";
        if (nombre.matches(".*\\bgrande\\b.*")) return "Grande";
        return producto.getNombre();
    }

    private boolean esPizza(Producto producto) {
        if (esCategoria(producto, "clasica", "clasicas", "especial", "especiales",
                "principal", "principales")) {
            return true;
        }
        return esCategoria(producto, "promocion", "promociones")
                && "personal".equalsIgnoreCase(producto.getTamano());
    }

    private boolean esCategoria(Producto producto, String... categorias) {
        if (producto.getCategoria() == null || producto.getCategoria().getCodigo() == null) return false;
        String codigo = normalizar(producto.getCategoria().getCodigo());
        return java.util.Arrays.stream(categorias).map(this::normalizar).anyMatch(codigo::equals);
    }

    private String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private BloquePersonalizacionView bloqueTexto(String codigo, String titulo,
                                                   List<OpcionPersonalizacionView> opciones) {
        return new BloquePersonalizacionView(codigo, titulo, ROL_CONFIGURACION,
                false, 1, 1, opciones);
    }

    private BloquePersonalizacionView bloqueProductos(String codigo, String titulo, String rol,
                                                        List<Producto> disponibles, boolean multiple,
                                                        int minimo, boolean mostrarNombre) {
        List<OpcionPersonalizacionView> opciones = disponibles.stream()
                .filter(producto -> Boolean.TRUE.equals(producto.getDisponible()))
                .filter(producto -> producto.getId() != null)
                .map(producto -> {
                    boolean soloSaborBebida = "sabor-bebida".equals(codigo);
                    boolean soloSaborPizza = "sabores-pizza".equals(codigo);
                    String etiqueta = soloSaborBebida
                            ? nombreSaborBebida(producto.getNombre())
                            : soloSaborPizza
                                ? nombreSaborPizza(producto.getNombre())
                                : (!mostrarNombre && "base".equals(codigo)
                                    ? etiquetaTamanoPizza(producto)
                                    : (mostrarNombre || producto.getTamano() == null || producto.getTamano().isBlank()
                                        ? producto.getNombre() : producto.getTamano()));
                    // En la configuración del combo se muestra únicamente el sabor:
                    // ni tamaño (personal/familiar), ni descripción, ni ingredientes.
                    String descripcion = (soloSaborBebida || soloSaborPizza) ? null
                            : (mostrarNombre
                                    ? textoSecundario(producto.getTamano(), producto.getDescripcion())
                                    : producto.getDescripcion());
                    return new OpcionPersonalizacionView(
                            producto.getId().toString(), producto.getId(), etiqueta, descripcion,
                            BigDecimal.valueOf(producto.getPrecio()), producto.getImagenUrl());
                })
                .toList();
        int maximo = ("sabores-pizza".equals(codigo) || "sabor-bebida".equals(codigo))
                ? Math.max(minimo, 1)
                : multiple ? (minimo > 0 ? Math.min(minimo, opciones.size()) : opciones.size()) : Math.min(1, opciones.size());
        return new BloquePersonalizacionView(codigo, titulo, rol, multiple,
                Math.min(minimo, maximo), maximo, opciones);
    }

    private String nombreSaborBebida(String nombre) {
        if (nombre == null) return "Bebida";
        // Mostrar solo la marca/sabor; la presentación ya está definida por el combo.
        return nombre.replaceAll("(?i)\\s*(?:[-–]?\\s*)?(?:botella\\s*)?(?:de\\s*)?(?:0[.,]?5|1[.,]?0|1[.,]?5|2|2[.,]?0|2[.,]?5|3)\\s*(?:l|lt|lts|litro|litros)\\.?\\s*$", "")
                .replaceAll("(?i)\\s*\\b(?:personal|pequeña|pequena|mediana|grande|familiar)\\s*$", "")
                .trim();
    }

    private String nombreSaborPizza(String nombre) {
        if (nombre == null || nombre.isBlank()) return "Pizza";
        String sabor = nombre.trim();
        // Algunos registros guardan el tamaño dentro del nombre. El selector del
        // combo debe presentar solo el sabor, por ejemplo "La Cómplice".
        sabor = sabor.replaceAll("(?i)^\\s*pizza\\s+", "");
        sabor = sabor.replaceAll("(?i)\\b(familiar|personal|mediana|grande|pequeña|pequena)\\b", " ");
        sabor = sabor.replaceAll("(?i)\\b(tamaño|tamano)\\s*[:\\-]?\\s*", " ");
        sabor = sabor.replaceAll("\\s*[-–|:]\\s*", " ").replaceAll("\\s+", " ").trim();
        return sabor.isBlank() ? nombre.trim() : sabor;
    }

    private String textoSecundario(String tamano, String descripcion) {
        if (tamano == null || tamano.isBlank()) return descripcion;
        if (descripcion == null || descripcion.isBlank()) return tamano;
        return tamano + " · " + descripcion;
    }

    private OpcionPersonalizacionView opcionTexto(String clave, String etiqueta, String descripcion) {
        return new OpcionPersonalizacionView(clave, null, etiqueta, descripcion, null, null);
    }
}
