package com.lacaleta.model;

import jakarta.persistence.*;

@Entity
@Table(name = "combo_items")
public class ComboItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name="combo_producto_id", nullable=false)
    private Integer comboProductoId;
    @Column(name="tipo_item", nullable=false, length=20)
    private String tipoItem;
    @Column(name="producto_id")
    private Integer productoId;
    @Column(nullable=false)
    private Integer cantidad = 1;
    @Column(name="modalidad", nullable=false, length=20)
    private String modalidad = "DEFINIDA";
    @Column(name="tamano", length=30)
    private String tamano;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Integer getComboProductoId(){return comboProductoId;} public void setComboProductoId(Integer v){comboProductoId=v;}
    public String getTipoItem(){return tipoItem;} public void setTipoItem(String v){tipoItem=v;}
    public Integer getProductoId(){return productoId;} public void setProductoId(Integer v){productoId=v;}
    public Integer getCantidad(){return cantidad;} public void setCantidad(Integer v){cantidad=v;}
    public String getModalidad(){return modalidad;} public void setModalidad(String v){modalidad=v;}
    public String getTamano(){return tamano;} public void setTamano(String v){tamano=v;}
}
