package es.uclm.esibuy.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Document(collection = "productos")
@TypeAlias ("producto")
public class Producto {
    @Id 
    private String id;

    @NotBlank 
    private String nombre;

    @NotBlank
    private String categoria;

    private int stock;

    private String descripcion;

    @NotNull 
    private Double precio;

    private int descuento;
    private int descuentoPremium;

    private boolean esVisible;

    private String vendedor;

    private String imagen;

    public String getId() {
        return id;
    }
    public String getNombre() {
        return nombre;
    }

    public String getCategoria() {
        return categoria;
    }

    public int getStock() {
        return stock;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public Double getPrecio() {
        return precio;
    }

    public int getDescuento() {
        return descuento;
    }

    public int getDescuentoPremium() {
        return descuentoPremium;
    }

    public boolean isEsVisible() {
        return esVisible;
    }

    public String getVendedor() {
        return vendedor;
    }

    public String getImagen() {
        return imagen;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public void setPrecio(Double precio) {
        this.precio = precio;
    }

    public void setDescuento(int descuento) {
        this.descuento = descuento;
    }

    public void setDescuentoPremium(int descuentoPremium) {
        this.descuentoPremium = descuentoPremium;
    }

    public void setEsVisible(boolean esVisible) {
        this.esVisible = esVisible;
    }
    
    public void setVendedor(String vendedor) {
        this.vendedor = vendedor;
    }
    
    public void setImagen(String imagen) {
        this.imagen = imagen;
    }
    
}
