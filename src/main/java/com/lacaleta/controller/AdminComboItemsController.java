package com.lacaleta.controller;

import com.lacaleta.model.ComboItem;
import com.lacaleta.model.Producto;
import com.lacaleta.repository.ComboItemRepository;
import com.lacaleta.service.ProductoService;
import com.lacaleta.service.DisponibilidadCatalogoService;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/api/admin/productos/{productoId}/items-combo")
public class AdminComboItemsController {
    private final ComboItemRepository items;
    private final ProductoService productos;
    private final DisponibilidadCatalogoService disponibilidad;
    public AdminComboItemsController(ComboItemRepository items, ProductoService productos, DisponibilidadCatalogoService disponibilidad){this.items=items;this.productos=productos;this.disponibilidad=disponibilidad;}
    public record ItemDto(Long id, String tipoItem, Integer productoId, Integer cantidad, String modalidad, String tamano, String nombreProducto){}
    public record ItemInput(String tipoItem, Integer productoId, Integer cantidad, String modalidad, String tamano){}
    @GetMapping("/catalogo")
    public List<Map<String,Object>> catalogo(){
        return productos.listar().stream().filter(p->Boolean.TRUE.equals(p.getDisponible())).filter(p->p.getId()!=null).map(p->{Map<String,Object> m=new LinkedHashMap<>();m.put("id",p.getId());m.put("nombre",p.getNombre());m.put("tamano",p.getTamano());m.put("categoria",p.getCategoria()==null?"":p.getCategoria().getCodigo());m.put("categoriaNombre",p.getCategoria()==null?"":p.getCategoria().getNombre());return m;}).toList();
    }
    @GetMapping
    public List<ItemDto> obtener(@PathVariable Integer productoId){
        return items.findByComboProductoIdOrderByIdAsc(productoId).stream().map(i->{String nombre=productos.buscarPorId(i.getProductoId()==null?-1:i.getProductoId()).map(Producto::getNombre).orElse("");return new ItemDto(i.getId(),i.getTipoItem(),i.getProductoId(),i.getCantidad(),i.getModalidad(),i.getTamano(),nombre);}).toList();
    }
    @PutMapping @Transactional
    public Map<String,String> guardar(@PathVariable Integer productoId,@RequestBody List<ItemInput> lista){
        productos.buscarPorId(productoId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Combo no encontrado."));
        if(lista==null||lista.size()>40)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La lista de artículos no es válida.");
        List<ComboItem> nuevos=new ArrayList<>(); StringBuilder descripcion=new StringBuilder("Contenido del combo: ");
        for(ItemInput x:lista){
            if(x==null||!Set.of("PIZZA","BEBIDA","ACOMPANAMIENTO").contains(Objects.toString(x.tipoItem(),""))||x.cantidad()==null||x.cantidad()<1||x.cantidad()>20||!Set.of("DEFINIDA","PERSONALIZAR","NINGUNA").contains(Objects.toString(x.modalidad(),"")))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Revisa tipo, modalidad y cantidad de los artículos.");
            if("DEFINIDA".equals(x.modalidad())&&(x.productoId()==null||productos.buscarPorId(x.productoId()).isEmpty()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Selecciona un producto definido.");
            if("PIZZA".equals(x.tipoItem())&&x.tamano()==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Selecciona el tamaño de pizza.");
            if ("ACOMPANAMIENTO".equals(x.tipoItem()) && "DEFINIDA".equals(x.modalidad())) {
                if (x.productoId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Selecciona un acompañamiento definido.");
                Producto acomp = productos.buscarPorId(x.productoId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,"Acompañamiento inexistente."));
                String codigo = acomp.getCategoria() == null ? "" : java.text.Normalizer.normalize(Objects.toString(acomp.getCategoria().getCodigo(),""), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);
                if (!(codigo.contains("acompanamiento") || codigo.contains("adicional") || codigo.contains("entrada")))
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El producto debe pertenecer a Acompañamientos.");
            }
            Producto productoDefinido = "DEFINIDA".equals(x.modalidad())
                    ? productos.buscarPorId(x.productoId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,"El producto seleccionado ya no existe."))
                    : null;
            String tamanoGuardado = x.tamano();
            // Para bebidas definidas, usa la presentación real del producto cuando el nombre la indica.
            if ("BEBIDA".equals(x.tipoItem()) && productoDefinido != null) {
                String textoProducto = (Objects.toString(productoDefinido.getNombre(), "") + " " + Objects.toString(productoDefinido.getTamano(), "")).toLowerCase(Locale.ROOT).replace(" ", "");
                if (textoProducto.contains("1.5l") || textoProducto.contains("1,5l") || textoProducto.contains("1500ml")) tamanoGuardado = "1.5 L";
                else if (textoProducto.contains("600ml")) tamanoGuardado = "600 ml";
                else if (textoProducto.contains("3l") || textoProducto.contains("3000ml")) tamanoGuardado = "3 L";
                else if (textoProducto.contains("1l") || textoProducto.contains("1000ml")) tamanoGuardado = "1 L";
                else if (tamanoGuardado == null || tamanoGuardado.isBlank()) tamanoGuardado = productoDefinido.getTamano();
            }
            ComboItem i=new ComboItem();i.setComboProductoId(productoId);i.setTipoItem(x.tipoItem());i.setProductoId(productoDefinido == null ? null : x.productoId());i.setCantidad(x.cantidad());i.setModalidad(x.modalidad());i.setTamano(tamanoGuardado);nuevos.add(i);
            String nombre;
            if ("NINGUNA".equals(x.modalidad())) nombre = "Sin artículo seleccionado";
            else if (productoDefinido != null) nombre = productoDefinido.getNombre();
            else nombre = ("PIZZA".equals(x.tipoItem()) ? "Pizza a personalizar" : "BEBIDA".equals(x.tipoItem()) ? "Bebida a personalizar" : "Acompañamiento a personalizar");
            if(descripcion.length()>20)descripcion.append("; ");
            descripcion.append(x.cantidad()).append(" x ").append(nombre);
            if(!"NINGUNA".equals(x.modalidad()) && tamanoGuardado!=null&&!tamanoGuardado.isBlank()) descripcion.append(" - ").append(tamanoGuardado);
        }
        items.deleteByComboProductoId(productoId);items.flush();items.saveAll(nuevos);
        Producto p=productos.buscarPorId(productoId).orElseThrow();p.setDescripcion(descripcion.toString());productos.guardar(p);
        // Ejecutar el recálculo tras confirmar la transacción, para que lea los nuevos ítems.
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() {
                        try { disponibilidad.actualizarStockCalculado(); }
                        catch (org.springframework.orm.ObjectOptimisticLockingFailureException ex) {
                            // Otra actualización de stock ganó; el planificador recalculará después.
                        }
                    }
                });
        }
        return Map.of("mensaje","Contenido del combo guardado en la base de datos.");
    }
}
