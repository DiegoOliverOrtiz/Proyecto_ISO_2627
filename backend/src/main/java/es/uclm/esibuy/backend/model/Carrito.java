package es.uclm.esibuy.backend.model;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "carritos")
@TypeAlias("carrito")
public class Carrito {
    @Id
    private String id;

    @Version
    private Long version;

    private Map<String, Integer> productos = new LinkedHashMap<>();

    public Carrito() {
    }

    public Carrito(String idCliente) {
        this.id = idCliente;
    }

    public String getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public Map<String, Integer> getProductos() {
        return Map.copyOf(productos);
    }

    public int cantidadDe(String idProducto) {
        return productos.getOrDefault(idProducto, 0);
    }

    public Carrito conCantidad(String idProducto, int cantidad) {
        Carrito actualizado = new Carrito(id);
        actualizado.version = version;
        actualizado.productos = new LinkedHashMap<>(productos);
        actualizado.productos.put(idProducto, cantidad);
        return actualizado;
    }

    public Carrito sinProducto(String idProducto) {
        Carrito actualizado = new Carrito(id);
        actualizado.version = version;
        actualizado.productos = new LinkedHashMap<>(productos);
        actualizado.productos.remove(idProducto);
        return actualizado;
    }

    public Carrito vacio() {
        Carrito actualizado = new Carrito(id);
        actualizado.version = version;
        return actualizado;
    }
}
