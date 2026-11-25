package com.lacaleta.controller;

import com.lacaleta.model.Producto;
import com.lacaleta.service.CategoriaService;
import com.lacaleta.service.ProductoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Controller
@RequestMapping("/admin/pizzas")
public class AdminProductoController {

    @Autowired
    private ProductoService productoService;

    @Autowired
    private CategoriaService categoriaService;

    @Autowired
    private com.lacaleta.service.ImagenProductoStorage imagenStorage;

    // POST: /admin/pizzas/guardar (Crear nueva pizza)
    @PostMapping("/guardar")
    public String guardar(
            @RequestParam("nombre") String nombre,
            @RequestParam("precio") Double precio,
            @RequestParam(value = "stock", required = false) Integer stock,
            @RequestParam(value = "imagenUrl", required = false) String imagenUrl,
            @RequestParam(value = "imagenArchivo", required = false) MultipartFile imagenArchivo,
            @RequestParam(value = "codigoCategoria", defaultValue = "clasicas") String codigoCategoria,
            @RequestParam(value = "tamano", defaultValue = "Familiar") String tamano,
            @RequestParam(value = "capacidadPersonas", required = false) String capacidadPersonas,
            @RequestParam(value = "tipoPizzaCombo", required = false) String tipoPizzaCombo,
            @RequestParam(value = "pizzaIncluidaId", required = false) Integer pizzaIncluidaId,
            @RequestParam(value = "tamanoBebidaCombo", required = false) String tamanoBebidaCombo,
            @RequestParam(value = "cantidadPizzasCombo", defaultValue = "1") Integer cantidadPizzasCombo,
            @RequestParam(value = "cantidadBebidasCombo", defaultValue = "0") Integer cantidadBebidasCombo,
            RedirectAttributes flash) {

        if (nombre == null || nombre.isBlank() || nombre.trim().length() > 100) {
            flash.addFlashAttribute("error", "El nombre de la pizza es obligatorio.");
            return "redirect:/admin#productos";
        }
        if (precio == null || !Double.isFinite(precio) || precio <= 0) {
            flash.addFlashAttribute("error", "El precio debe ser mayor a 0.");
            return "redirect:/admin#productos";
        }

        var catOpt = categoriaService.buscarPorCodigo(codigoCategoria);
        String categoriaNormalizada = normalizarCodigoCategoria(codigoCategoria);
        String tamanoValidado = normalizarTamano(tamano);
        if (tamanoValidado == null) {
            flash.addFlashAttribute("error", "Selecciona un tamaño válido para el producto.");
            return "redirect:/admin#productos";
        }

        Producto pizza = new Producto();
        pizza.setNombre(nombre.trim());
        pizza.setPrecio(precio);
        pizza.setStock(0);
        String descripcion = "Pizza artesanal horneada a la leña.";
        boolean esComboOPromocion = esComboOPromocion(categoriaNormalizada);
        if (!esCapacidadValida(capacidadPersonas)) {
            flash.addFlashAttribute("error", "Selecciona una capacidad válida.");
            return "redirect:/admin#productos";
        }
        if (esComboOPromocion) {
            if (cantidadPizzasCombo == null || cantidadBebidasCombo == null
                    || cantidadPizzasCombo < 0 || cantidadPizzasCombo > 20
                    || cantidadBebidasCombo < 0 || cantidadBebidasCombo > 20) {
                flash.addFlashAttribute("error", "Las cantidades de pizzas y bebidas deben estar entre 0 y 20.");
                return "redirect:/admin#productos";
            }
            String tipo = tipoPizzaCombo == null ? "PERSONALIZAR" : tipoPizzaCombo.trim().toUpperCase(java.util.Locale.ROOT);
            if ("DEFINIDA".equals(tipo)) {
                Producto incluida = pizzaIncluidaId == null ? null : productoService.buscarPorId(pizzaIncluidaId).orElse(null);
                if (incluida == null) {
                    flash.addFlashAttribute("error", "Selecciona una pizza válida para el combo.");
                    return "redirect:/admin#productos";
                }
                descripcion = "Pizza incluida definida: " + incluida.getNombre()
                        + (incluida.getTamano() == null ? "" : " (" + incluida.getTamano() + ")");
            } else if ("NINGUNA".equals(tipo)) {
                descripcion = "No incluye pizza.";
            } else {
                descripcion = "Pizza personalizar: el cliente elige la pizza.";
            }
            descripcion += " | Cantidad de pizzas: " + ("NINGUNA".equals(tipo) ? 0 : cantidadPizzasCombo);
            descripcion += " | Cantidad de bebidas: " + cantidadBebidasCombo;
            if (tamanoBebidaCombo != null && !tamanoBebidaCombo.isBlank()) {
                descripcion += " | Bebida tamaño: " + tamanoBebidaCombo.trim();
            }
        }
        pizza.setDescripcion(descripcion);
        pizza.setTamano(tamanoValidado);
        pizza.setCapacidadPersonas(esComboOPromocion ? normalizarCapacidad(capacidadPersonas) : null);
        pizza.setDisponible(true);
        pizza.setCategoria(catOpt.orElse(null));

        String rutaImagenFinal = "/images/producto_americana.jpg";

        if (imagenArchivo != null && !imagenArchivo.isEmpty()) {
            try {
                rutaImagenFinal = imagenStorage.guardar(imagenArchivo);
            } catch (IOException e) {
                e.printStackTrace();
                flash.addFlashAttribute("error", "Error al procesar la subida de la imagen.");
                return "redirect:/admin#productos";
            }
        }

        pizza.setImagenUrl(rutaImagenFinal);

        productoService.guardar(pizza);
        flash.addFlashAttribute("exito", esComboOPromocion
                ? "Combo o promoción registrado con su configuración de pizza y bebida."
                : "Pizza registrada correctamente. Configura ahora su receta para calcular el stock.");
      return "redirect:/admin?receta=" + pizza.getId()
        + "&categoria=" + categoriaNormalizada
        + "#productos";
    }

    // POST: /admin/pizzas/editar (Modificar pizza desde el modal de edición)
    @PostMapping("/editar")
    public String editar(
            @RequestParam("id") Integer id,
            @RequestParam("nombre") String nombre,
            @RequestParam(value = "descripcion", required = false) String descripcion,
            @RequestParam("precio") Double precio,
            @RequestParam(value = "stock", required = false) Integer stock,
            @RequestParam(value = "imagenUrl", required = false) String imagenUrl,
            @RequestParam(value = "imagenArchivo", required = false) MultipartFile imagenArchivo,
            @RequestParam(value = "codigoCategoria", required = false) String codigoCategoria,
            @RequestParam(value = "tamano", required = false) String tamano,
            @RequestParam(value = "capacidadPersonas", required = false) String capacidadPersonas,
            @RequestParam(value = "disponible", defaultValue = "true") Boolean disponible,
            RedirectAttributes flash) {

        if (nombre == null || nombre.isBlank() || nombre.trim().length() > 100
                || precio == null || !Double.isFinite(precio) || precio <= 0) {
            flash.addFlashAttribute("error", "Nombre, precio o stock inválidos.");
            return "redirect:/admin#productos";
        }
        var existente = productoService.buscarPorId(id);
        if (existente.isEmpty()) {
            flash.addFlashAttribute("error", "No se encontró la pizza a editar.");
            return "redirect:/admin#productos";
        }

        Producto productoExistente = existente.get();
        var categoria = productoExistente.getCategoria();
        if (codigoCategoria != null && !codigoCategoria.isBlank()) {
            var catOpt = categoriaService.buscarPorCodigo(codigoCategoria);
            categoria = catOpt.orElse(null);
            if (categoria == null) {
                flash.addFlashAttribute("error", "Selecciona una categoría válida.");
                return "redirect:/admin#productos";
            }
        }

        String categoriaNormalizada = normalizarCodigoCategoria(
                categoria == null ? null : categoria.getCodigo());
        String capacidadRecibida = capacidadPersonas == null
                ? productoExistente.getCapacidadPersonas() : capacidadPersonas;
        if (!esCapacidadValida(capacidadRecibida)) {
            flash.addFlashAttribute("error", "Selecciona una capacidad válida.");
            return "redirect:/admin#productos";
        }
        String capacidadValidada = esComboOPromocion(categoriaNormalizada)
                ? normalizarCapacidad(capacidadRecibida) : null;
        String tamanoRecibido = tamano == null || tamano.isBlank()
                ? productoExistente.getTamano() : tamano;
        if (tamanoRecibido == null || tamanoRecibido.isBlank()) {
            tamanoRecibido = "Familiar";
        }
        String tamanoValidado = normalizarTamano(tamanoRecibido);
        if (tamanoValidado == null) {
            flash.addFlashAttribute("error", "Selecciona un tamaño válido para el producto.");
            return "redirect:/admin#productos";
        }

        String imagenActualizada = null;
        // Si sube un archivo nuevo, lo procesamos
        if (imagenArchivo != null && !imagenArchivo.isEmpty()) {
            try {
                imagenActualizada = imagenStorage.guardar(imagenArchivo);
            } catch (IOException e) {
                e.printStackTrace();
                flash.addFlashAttribute("error", "Error al procesar la nueva imagen.");
                return "redirect:/admin#productos";
            }
        }

        var actualizado = productoService.actualizarDatos(
                id,
                nombre.trim(),
                descripcion == null ? null : descripcion.trim(),
                precio,
                imagenActualizada,
                categoria,
                tamanoValidado,
                capacidadValidada,
                disponible);
        if (actualizado.isEmpty()) {
            flash.addFlashAttribute("error", "No se encontró la pizza a editar.");
            return "redirect:/admin#productos";
        }
        flash.addFlashAttribute("exito", "Pizza actualizada correctamente.");
        return "redirect:/admin#productos";
    }

    private String normalizarCodigoCategoria(String codigo) {
        if (codigo == null) return "";
        return java.text.Normalizer.normalize(codigo, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .toLowerCase(java.util.Locale.ROOT);
    }

    private boolean esComboOPromocion(String categoria) {
        return categoria.equals("combos") || categoria.equals("promociones")
                || categoria.equals("promocion");
    }

    private boolean esCapacidadValida(String capacidad) {
        return capacidad == null || capacidad.isBlank()
                || java.util.Set.of("1 persona", "2 personas", "3-4 personas", "5 a más")
                        .contains(capacidad.trim());
    }

    private String normalizarCapacidad(String capacidad) {
        return capacidad == null || capacidad.isBlank() ? null : capacidad.trim();
    }

    private String normalizarTamano(String tamano) {
        if (tamano == null) return null;
        String valor = tamano.trim();
        java.util.Set<String> permitidos = java.util.Set.of("Personal", "Mediana", "Grande", "Familiar");
        return permitidos.stream().filter(valor::equalsIgnoreCase).findFirst().orElse(null);
    }

    // POST: eliminar desde el formulario de confirmación.
    @PostMapping("/eliminar/{id}")
    public String eliminar(@PathVariable Integer id, RedirectAttributes flash) {
        try {
            if (productoService.eliminar(id)) {
                flash.addFlashAttribute("exito", "Pizza eliminada correctamente.");
            } else {
                flash.addFlashAttribute("error", "No se pudo encontrar la pizza a eliminar.");
            }
        } catch (org.springframework.web.server.ResponseStatusException e) {
            flash.addFlashAttribute("error", e.getReason());
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            flash.addFlashAttribute("error", "No se puede eliminar porque el producto tiene registros relacionados. Puedes inhabilitarlo.");
        }
        return "redirect:/admin#productos";
    }
}
