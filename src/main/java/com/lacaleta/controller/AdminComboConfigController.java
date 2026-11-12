package com.lacaleta.controller;

import com.lacaleta.model.Combo;
import com.lacaleta.model.Producto;
import com.lacaleta.repository.ComboRepository;
import com.lacaleta.service.ProductoService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.regex.*;

@RestController
@RequestMapping("/api/admin/productos/{productoId}/configuracion-combo")
public class AdminComboConfigController {
    private final ProductoService productos;
    private final ComboRepository combos;
    public AdminComboConfigController(ProductoService productos, ComboRepository combos) { this.productos=productos; this.combos=combos; }
    public record Config(String tipoPizza, Integer pizzaId, String tamanoPizza, Integer cantidadPizzas, String tamanoBebida, Integer cantidadBebidas, boolean esCombo) {}
    @GetMapping public Config obtener(@PathVariable Integer productoId) {
        Producto p=productos.buscarPorId(productoId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        String d=Objects.toString(p.getDescripcion(), "");
        String tipo=d.toLowerCase(Locale.ROOT).contains("pizza incluida definida:")?"DEFINIDA":d.toLowerCase(Locale.ROOT).contains("no incluye pizza")?"NINGUNA":"PERSONALIZAR";
        Integer cantidadPizza=numero(d,"Cantidad de pizzas:",cantidadInferida(d));
        Integer cantidadBebida=numero(d,"Cantidad de bebidas:",d.toLowerCase(Locale.ROOT).contains("bebida")?1:0);
        String tamPizza=valor(d,"Tamaño pizza combo:"); if(tamPizza==null && p.getTamano()!=null && Set.of("personal","mediana","familiar","grande").contains(p.getTamano().toLowerCase(Locale.ROOT))) tamPizza=p.getTamano(); String tamBebida=valor(d,"Bebida tamaño:");
        Integer pizzaId=null; if ("DEFINIDA".equals(tipo)) { String fragmento=d.substring(d.toLowerCase(Locale.ROOT).indexOf("pizza incluida definida:")+"pizza incluida definida:".length()).split("\\|")[0].trim(); for(Producto opcion:productos.listar()){ if(opcion.getNombre()!=null && fragmento.toLowerCase(Locale.ROOT).startsWith(opcion.getNombre().toLowerCase(Locale.ROOT))){pizzaId=opcion.getId();break;} } }
        boolean esCombo=p.getCategoria()!=null && ("combos".equalsIgnoreCase(p.getCategoria().getCodigo()) || "promociones".equalsIgnoreCase(p.getCategoria().getCodigo()) || "promocion".equalsIgnoreCase(p.getCategoria().getCodigo()));
        return new Config(tipo,pizzaId,tamPizza,cantidadPizza,tamBebida,cantidadBebida,esCombo);
    }
    @PutMapping public Map<String,String> guardar(@PathVariable Integer productoId,@RequestBody Config c) {
        Producto p=productos.buscarPorId(productoId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        String tipo=Objects.toString(c.tipoPizza(),"PERSONALIZAR").toUpperCase(Locale.ROOT);
        if(!Set.of("DEFINIDA","PERSONALIZAR","NINGUNA").contains(tipo)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tipo de pizza inválido.");
        int qp=c.cantidadPizzas()==null?1:c.cantidadPizzas(), qb=c.cantidadBebidas()==null?0:c.cantidadBebidas();
        if(qp<0||qp>20||qb<0||qb>20) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Las cantidades deben estar entre 0 y 20.");
        StringBuilder d=new StringBuilder();
        if("DEFINIDA".equals(tipo)) {
            if(c.pizzaId()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Selecciona la pizza definida.");
            Producto incluida=productos.buscarPorId(c.pizzaId()).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Pizza no encontrada."));
            d.append("Pizza incluida definida: ").append(incluida.getNombre()).append(" (").append(Objects.toString(incluida.getTamano(),c.tamanoPizza())).append(")");
        } else if("NINGUNA".equals(tipo)) d.append("No incluye pizza.");
        else d.append("Pizza personalizar: el cliente elige la pizza.");
        d.append(" | Cantidad de pizzas: ").append("NINGUNA".equals(tipo)?0:qp);
        if(c.tamanoPizza()!=null&&!c.tamanoPizza().isBlank()) d.append(" | Tamaño pizza combo: ").append(c.tamanoPizza().trim());
        d.append(" | Cantidad de bebidas: ").append(qb);
        if(c.tamanoBebida()!=null&&!c.tamanoBebida().isBlank()) d.append(" | Bebida tamaño: ").append(c.tamanoBebida().trim());
        p.setDescripcion(d.toString()); productos.guardar(p);
        if(p.getCategoria()!=null&&"combos".equalsIgnoreCase(p.getCategoria().getCodigo())) combos.findByNombreIgnoreCase(p.getNombre()).ifPresent(combo->{combo.setDescripcion(d.toString());combos.save(combo);});
        return Map.of("mensaje","Se actualizó lo que incluye el combo.");
    }
    private int cantidadInferida(String d){String x=d.toLowerCase(Locale.ROOT);if(x.contains("3 pizzas")||x.contains("tres pizzas"))return 3;if(x.contains("2 pizzas")||x.contains("dos pizzas")||x.contains("duo"))return 2;return 1;}
    private Integer numero(String d,String etiqueta,int defecto){try{Matcher m=Pattern.compile(Pattern.quote(etiqueta)+"\\s*(\\d+)",Pattern.CASE_INSENSITIVE).matcher(d);return m.find()?Integer.parseInt(m.group(1)):defecto;}catch(Exception e){return defecto;}}
    private String valor(String d,String etiqueta){Matcher m=Pattern.compile(Pattern.quote(etiqueta)+"\\s*([^|]+)",Pattern.CASE_INSENSITIVE).matcher(d);return m.find()?m.group(1).trim():null;}
}
