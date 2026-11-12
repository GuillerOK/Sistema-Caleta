package com.lacaleta.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pedidos", indexes = @Index(name = "idx_pedidos_usuario_fecha", columnList = "usuario_id,fecha"),
       uniqueConstraints = @UniqueConstraint(columnNames = {"usuario_id", "clave_compra"}))
public class Pedido {
    private Double subtotal;
    private Double envio;
    private Double descuentoCupon;
    private String codigoCupon;
    private String tipoCupon;
    private String tituloCupon;

    public Double getSubtotal() { return subtotal; }
    public void setSubtotal(Double subtotal) { this.subtotal = subtotal; }
    public Double getEnvio() { return envio; }
    public void setEnvio(Double envio) { this.envio = envio; }
    public Double getDescuentoCupon() { return descuentoCupon; }
    public void setDescuentoCupon(Double descuentoCupon) { this.descuentoCupon = descuentoCupon; }
    public String getCodigoCupon() { return codigoCupon; }
    public void setCodigoCupon(String codigoCupon) { this.codigoCupon = codigoCupon; }
    public String getTipoCupon() { return tipoCupon; }
    public void setTipoCupon(String tipoCupon) { this.tipoCupon = tipoCupon; }
    public String getTituloCupon() { return tituloCupon; }
    public void setTituloCupon(String tituloCupon) { this.tituloCupon = tituloCupon; }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;
    
    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();
    
    @Column(nullable = false, length = 20)
    private String estado = "PENDIENTE"; // REALIZADO confirma el pago online simulado.
    
    @Column(name = "metodo_pago", length = 20)
    private String metodoPago; // ONLINE_SIMULADO para compras nuevas; conserva métodos históricos.
    
    @Column(name = "direccion_entrega")
    private String direccionEntrega;
    
    @Column(nullable = false)
    private Double total;

    // Consumos calculados y validados al comprar; se descuentan al confirmar.
    @Column(name = "stock_productos_pendiente", columnDefinition = "text")
    private String stockProductosPendiente;
    @Column(name = "stock_insumos_pendiente", columnDefinition = "text")
    private String stockInsumosPendiente;
    public String getStockProductosPendiente() { return stockProductosPendiente; }
    public void setStockProductosPendiente(String valor) { stockProductosPendiente = valor; }
    public String getStockInsumosPendiente() { return stockInsumosPendiente; }
    public void setStockInsumosPendiente(String valor) { stockInsumosPendiente = valor; }

    // Diferencia pedidos nuevos (stock reservado al pagar) de pedidos históricos.
    @Column(name = "stock_reservado", nullable = false)
    private Boolean stockReservado = false;
    public boolean isStockReservado() { return Boolean.TRUE.equals(stockReservado); }
    public void setStockReservado(boolean reservado) { this.stockReservado = reservado; }

    @Column(name = "clave_compra", length = 36)
    private String claveCompra;
    public String getClaveCompra() { return claveCompra; }
    public void setClaveCompra(String claveCompra) { this.claveCompra = claveCompra; }
    
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetallePedido> detalles = new ArrayList<>();

    @Transient
    public boolean isCancelable() {
        return "EN_PROCESO".equals(estado) && fecha != null
            && !fecha.plusMinutes(5).isBefore(LocalDateTime.now(ZoneId.of("America/Lima")));
    }

    public Pedido() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getMetodoPago() { return metodoPago; }
    public void setMetodoPago(String metodoPago) { this.metodoPago = metodoPago; }

    public String getDireccionEntrega() { return direccionEntrega; }
    public void setDireccionEntrega(String direccionEntrega) { this.direccionEntrega = direccionEntrega; }

    public Double getTotal() { return total; }
    public void setTotal(Double total) { this.total = total; }

    public List<DetallePedido> getDetalles() { return detalles; }
    public void setDetalles(List<DetallePedido> detalles) { this.detalles = detalles; }
}
