package es.uclm.esibuy.backend.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class ListaProductosTests {

    @Test
    void crearListaGeneraUnaListaNoPredeterminadaSinIdNiProductos() {
        ListaProductos lista = ListaProductos.crearLista("cliente-1", "Videojuegos");

        assertNull(lista.getId());
        assertEquals("cliente-1", lista.getUsuarioId());
        assertEquals("Videojuegos", lista.getNombre());
        assertFalse(lista.isPredeterminada());
        assertTrue(lista.getProductos().isEmpty());
    }

    @Test
    void favoritosEsLaListaPredeterminadaConIdFijoPorUsuario() {
        ListaProductos favoritos = ListaProductos.crearFavoritos("cliente-1");

        assertEquals("favoritos:cliente-1", favoritos.getId());
        assertEquals("Favoritos", favoritos.getNombre());
        assertTrue(favoritos.isPredeterminada());
    }

    @Test
    void renombrarSoloCambiaElNombre() {
        ListaProductos lista = ListaProductos.crearLista("cliente-1", "Comida");
        lista.agregarProducto("p1");

        lista.renombrar("Alimentación");

        assertEquals("Alimentación", lista.getNombre());
        assertEquals(List.of("p1"), List.copyOf(lista.getProductos()));
    }

    @Test
    void unProductoRepetidoNoSeDuplicaYSeConservaElOrden() {
        ListaProductos lista = ListaProductos.crearLista("cliente-1", "Comida");

        assertTrue(lista.agregarProducto("p1"));
        assertTrue(lista.agregarProducto("p2"));
        assertFalse(lista.agregarProducto("p1"));

        assertEquals(List.of("p1", "p2"), List.copyOf(lista.getProductos()));
    }

    @Test
    void quitarUnProductoQueNoEstaNoCambiaLaLista() {
        ListaProductos lista = ListaProductos.crearLista("cliente-1", "Comida");
        lista.agregarProducto("p1");

        assertFalse(lista.quitarProducto("p9"));
        assertTrue(lista.quitarProducto("p1"));
        assertTrue(lista.getProductos().isEmpty());
    }

    @Test
    void losProductosNoSePuedenModificarDesdeFuera() {
        ListaProductos lista = ListaProductos.crearLista("cliente-1", "Comida");
        var productos = lista.getProductos();

        assertThrows(UnsupportedOperationException.class, () -> productos.add("p1"));
    }
}
