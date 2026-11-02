package es.uclm.esibuy.backend.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "listas_productos")
public class ListaProductos {

    @Id
    private String id;

    @Indexed
    private String usuarioId;

    private String nombre;
    private boolean predeterminada;

    @Version
    private Long version;

    private Set<String> productos = new LinkedHashSet<>();

    public ListaProductos() {
    }

    public ListaProductos(String id, String usuarioId, String nombre, boolean predeterminada) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.nombre = nombre;
        this.predeterminada = predeterminada;
    }

    public static ListaProductos crearFavoritos(String usuarioId) {
        return new ListaProductos(
                "favoritos:" + usuarioId,
                usuarioId,
                "Favoritos",
                true
        );
    }

    public String getId() {
        return id;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public String getNombre() {
        return nombre;
    }

    public boolean isPredeterminada() {
        return predeterminada;
    }

    public Long getVersion() {
        return version;
    }

    public Set<String> getProductos() {
        return Collections.unmodifiableSet(
                new LinkedHashSet<>(productos)
        );
    }

    public boolean agregarProducto(String productoId) {
        if (productos == null) {
            productos = new LinkedHashSet<>();
        }
        return productos.add(productoId);
    }

    public boolean quitarProducto(String productoId) {
        return productos != null
                && productos.remove(productoId);
    }
}
