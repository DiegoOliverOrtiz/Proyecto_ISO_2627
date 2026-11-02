package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import es.uclm.esibuy.backend.dao.ListaProductosDAO;
import es.uclm.esibuy.backend.dao.ProductoDAO;
import es.uclm.esibuy.backend.dao.UsuarioDAO;
import es.uclm.esibuy.backend.dto.FavoritosResponse;
import es.uclm.esibuy.backend.exceptions.ProductoNoEncontradoException;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.ListaProductos;
import es.uclm.esibuy.backend.model.Producto;

@ExtendWith(MockitoExtension.class)
class FavoritoServiceTests {

    @Mock
    private ListaProductosDAO listaProductosDAO;

    @Mock
    private UsuarioDAO usuarioDAO;

    @Mock
    private ProductoDAO productoDAO;

    @InjectMocks
    private FavoritoService favoritoService;

    private static final String CORREO = "cliente@test.com";
    private static final String USUARIO_ID = "cliente-1";
    private static final String PRODUCTO_ID = "producto-1";

    private ListaProductos lista;

    @BeforeEach
    void preparar() {
        lista = ListaProductos.crearFavoritos(USUARIO_ID);
    }

    private void prepararUsuario(String correo, String id) {
        Cliente cliente = mock(Cliente.class);

        when(cliente.isActivada()).thenReturn(true);
        when(cliente.getId()).thenReturn(id);

        when(usuarioDAO.findByEmail(correo))
                .thenReturn(Optional.of(cliente));
    }

    private Producto crearProducto(
            String id,
            boolean visible,
            boolean soloPremium,
            int stock) {

        Producto producto = mock(Producto.class);

        lenient().when(producto.getId()).thenReturn(id);
        lenient().when(producto.getNombre())
                .thenReturn("Producto de prueba");
        lenient().when(producto.isEsVisible())
                .thenReturn(visible);
        lenient().when(producto.isSoloPremium())
                .thenReturn(soloPremium);
        lenient().when(producto.getStock())
                .thenReturn(stock);
        lenient().when(producto.getPrecio())
                .thenReturn(100.0);

        return producto;
    }

    private void prepararListaExistente() {
        when(listaProductosDAO.findById("favoritos:" + USUARIO_ID))
                .thenReturn(Optional.of(lista));
    }

    @Test
    void consultarCreaListaSiNoExiste() {
        prepararUsuario(CORREO, USUARIO_ID);

        when(listaProductosDAO.findById("favoritos:" + USUARIO_ID))
                .thenReturn(Optional.empty());

        when(listaProductosDAO.save(any(ListaProductos.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        when(productoDAO.findAllById(any()))
                .thenReturn(List.of());

        FavoritosResponse respuesta =
                favoritoService.consultar(CORREO);

        assertNotNull(respuesta);
        assertTrue(respuesta.ids().isEmpty());
        assertTrue(respuesta.productos().isEmpty());

        verify(listaProductosDAO).save(
                argThat(l ->
                        l.getUsuarioId().equals(USUARIO_ID)
                        && l.isPredeterminada()
                        && l.getProductos().isEmpty()
                )
        );
    }

    @Test
    void anadirProductoAGuardados() {
        prepararUsuario(CORREO, USUARIO_ID);
        prepararListaExistente();

        Producto producto = crearProducto(
                PRODUCTO_ID, true, false, 10
        );

        when(productoDAO.findById(PRODUCTO_ID))
                .thenReturn(Optional.of(producto));

        when(productoDAO.findAllById(any()))
                .thenReturn(List.of(producto));

        FavoritosResponse respuesta =
                favoritoService.anadir(CORREO, PRODUCTO_ID);

        assertEquals(1, respuesta.ids().size());
        assertTrue(respuesta.ids().contains(PRODUCTO_ID));
        assertTrue(respuesta.productos().get(0).disponible());

        verify(listaProductosDAO, times(1))
                .save(lista);
    }

    @Test
    void anadirDosVecesNoGeneraDuplicados() {
        prepararUsuario(CORREO, USUARIO_ID);
        prepararListaExistente();

        Producto producto = crearProducto(
                PRODUCTO_ID, true, false, 10
        );

        when(productoDAO.findById(PRODUCTO_ID))
                .thenReturn(Optional.of(producto));

        when(productoDAO.findAllById(any()))
                .thenReturn(List.of(producto));

        favoritoService.anadir(CORREO, PRODUCTO_ID);
        FavoritosResponse respuesta =
                favoritoService.anadir(CORREO, PRODUCTO_ID);

        assertEquals(1, respuesta.ids().size());
        assertEquals(1, lista.getProductos().size());

        verify(listaProductosDAO, times(1))
                .save(lista);
    }

    @Test
    void quitarProductoDeFavoritos() {
        prepararUsuario(CORREO, USUARIO_ID);
        prepararListaExistente();

        lista.agregarProducto(PRODUCTO_ID);

        when(productoDAO.findAllById(any()))
                .thenReturn(List.of());

        FavoritosResponse respuesta =
                favoritoService.quitar(CORREO, PRODUCTO_ID);

        assertTrue(respuesta.ids().isEmpty());
        assertTrue(lista.getProductos().isEmpty());

        verify(listaProductosDAO).save(lista);
    }

    @Test
    void quitarProductoQueNoExisteEsIdempotente() {
        prepararUsuario(CORREO, USUARIO_ID);
        prepararListaExistente();

        when(productoDAO.findAllById(any()))
                .thenReturn(List.of());

        FavoritosResponse respuesta =
                favoritoService.quitar(CORREO, "no-guardado");

        assertTrue(respuesta.ids().isEmpty());

        verify(listaProductosDAO, never())
                .save(any(ListaProductos.class));
    }

    @Test
    void productoDesactivadoPermaneceEnFavoritos() {
        prepararUsuario(CORREO, USUARIO_ID);
        prepararListaExistente();

        lista.agregarProducto(PRODUCTO_ID);

        Producto desactivado = crearProducto(
                PRODUCTO_ID, false, false, 10
        );

        when(productoDAO.findAllById(any()))
                .thenReturn(List.of(desactivado));

        FavoritosResponse respuesta =
                favoritoService.consultar(CORREO);

        assertEquals(1, respuesta.ids().size());
        assertFalse(respuesta.productos().get(0).disponible());
        assertEquals(
                "Producto no disponible",
                respuesta.productos().get(0).aviso()
        );
    }

    @Test
    void productoEliminadoPermaneceComoNoDisponible() {
        prepararUsuario(CORREO, USUARIO_ID);
        prepararListaExistente();

        lista.agregarProducto(PRODUCTO_ID);

        when(productoDAO.findAllById(any()))
                .thenReturn(List.of());

        FavoritosResponse respuesta =
                favoritoService.consultar(CORREO);

        assertEquals(1, respuesta.ids().size());
        assertFalse(respuesta.productos().get(0).disponible());
        assertEquals(
                "Producto eliminado",
                respuesta.productos().get(0).nombre()
        );
    }

    @Test
    void noPermiteAnadirProductoDesactivado() {
        Producto desactivado = crearProducto(
                PRODUCTO_ID, false, false, 10
        );

        when(productoDAO.findById(PRODUCTO_ID))
                .thenReturn(Optional.of(desactivado));

        assertThrows(
                ProductoNoEncontradoException.class,
                () -> favoritoService.anadir(CORREO, PRODUCTO_ID)
        );

        verify(listaProductosDAO, never())
                .save(any(ListaProductos.class));
    }

    @Test
    void noPermiteAnadirProductoInexistente() {
        when(productoDAO.findById(PRODUCTO_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                ProductoNoEncontradoException.class,
                () -> favoritoService.anadir(CORREO, PRODUCTO_ID)
        );
    }

    @Test
    void noPermiteConsultarSinAutenticacion() {
        assertThrows(
                AccessDeniedException.class,
                () -> favoritoService.consultar(null)
        );
    }

    @Test
    void noPermiteConsultarConUsuarioInexistente() {
        when(usuarioDAO.findByEmail(CORREO))
                .thenReturn(Optional.empty());

        assertThrows(
                AccessDeniedException.class,
                () -> favoritoService.consultar(CORREO)
        );
    }

    @Test
    void cadaUsuarioConsultaSuPropiaLista() {
        String otroCorreo = "otro@test.com";
        String otroId = "cliente-2";

        prepararUsuario(CORREO, USUARIO_ID);
        prepararUsuario(otroCorreo, otroId);

        ListaProductos otraLista =
                ListaProductos.crearFavoritos(otroId);

        lista.agregarProducto("producto-A");
        otraLista.agregarProducto("producto-B");

        when(listaProductosDAO.findById("favoritos:" + USUARIO_ID))
                .thenReturn(Optional.of(lista));

        when(listaProductosDAO.findById("favoritos:" + otroId))
                .thenReturn(Optional.of(otraLista));

        when(productoDAO.findAllById(any()))
                .thenReturn(List.of());

        FavoritosResponse respuesta1 =
                favoritoService.consultar(CORREO);

        FavoritosResponse respuesta2 =
                favoritoService.consultar(otroCorreo);

        assertEquals(
                List.of("producto-A"),
                respuesta1.ids()
        );

        assertEquals(
                List.of("producto-B"),
                respuesta2.ids()
        );

        assertFalse(respuesta1.ids().contains("producto-B"));
        assertFalse(respuesta2.ids().contains("producto-A"));
    }

    @Test
    void productoSoloPremiumNoEsComprablePorClienteNormal() {
        prepararUsuario(CORREO, USUARIO_ID);
        prepararListaExistente();

        lista.agregarProducto(PRODUCTO_ID);

        Producto premium = crearProducto(
                PRODUCTO_ID, true, true, 10
        );

        when(productoDAO.findAllById(any()))
                .thenReturn(List.of(premium));

        FavoritosResponse respuesta =
                favoritoService.consultar(CORREO);

        assertFalse(respuesta.productos().get(0).disponible());
        assertEquals(
                "Solo disponible para clientes Premium",
                respuesta.productos().get(0).aviso()
        );
    }

    @Test
    void productoSinStockNoEsComprable() {
        prepararUsuario(CORREO, USUARIO_ID);
        prepararListaExistente();

        lista.agregarProducto(PRODUCTO_ID);

        Producto agotado = crearProducto(
                PRODUCTO_ID, true, false, 0
        );

        when(productoDAO.findAllById(any()))
                .thenReturn(List.of(agotado));

        FavoritosResponse respuesta =
                favoritoService.consultar(CORREO);

        assertFalse(respuesta.productos().get(0).disponible());
        assertEquals(
                "Sin stock",
                respuesta.productos().get(0).aviso()
        );
    }

    @Test
    void noPermiteIdentificadorProductoVacio() {
        assertThrows(
                IllegalArgumentException.class,
                () -> favoritoService.anadir(CORREO, " ")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> favoritoService.quitar(CORREO, null)
        );
    }
}
