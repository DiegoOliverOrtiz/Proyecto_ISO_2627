package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

import es.uclm.esibuy.backend.dao.ListaProductosDAO;
import es.uclm.esibuy.backend.dao.ProductoDAO;
import es.uclm.esibuy.backend.dao.UsuarioDAO;
import es.uclm.esibuy.backend.dto.ListaProductosResponse;
import es.uclm.esibuy.backend.dto.ResumenListaProductosResponse;
import es.uclm.esibuy.backend.exceptions.ListaNoEncontradaException;
import es.uclm.esibuy.backend.exceptions.NombreListaDuplicadoException;
import es.uclm.esibuy.backend.exceptions.OperacionListaNoPermitidaException;
import es.uclm.esibuy.backend.exceptions.ProductoNoEncontradoException;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.ListaProductos;
import es.uclm.esibuy.backend.model.Producto;
import es.uclm.esibuy.backend.model.Vendedor;

@ExtendWith(MockitoExtension.class)
class ListaProductosServiceTests {

    private static final String CORREO = "cliente@test.com";
    private static final String USUARIO_ID = "cliente-1";
    private static final String OTRO_USUARIO_ID = "cliente-2";

    @Mock
    private ListaProductosDAO listaProductosDAO;

    @Mock
    private UsuarioDAO usuarioDAO;

    @Mock
    private ProductoDAO productoDAO;

    @Mock
    private FavoritosRegistroService favoritosRegistroService;

    @InjectMocks
    private ListaProductosService servicio;

    private void prepararUsuario() {
        Cliente cliente = mock(Cliente.class);
        when(cliente.isActivada()).thenReturn(true);
        lenient().when(cliente.getId()).thenReturn(USUARIO_ID);
        when(usuarioDAO.findByEmail(CORREO)).thenReturn(Optional.of(cliente));
    }

    private ListaProductos lista(String id, String usuarioId, String nombre) {
        return new ListaProductos(id, usuarioId, nombre, false);
    }

    private void guardarDevuelveLoMismo() {
        when(listaProductosDAO.save(any(ListaProductos.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    // Consultar

    @Test
    void consultarListasGarantizaFavoritosYLosMuestraPrimero() {
        prepararUsuario();
        when(listaProductosDAO.findByUsuarioId(USUARIO_ID)).thenReturn(List.of(
                lista("l2", USUARIO_ID, "videojuegos"),
                ListaProductos.crearFavoritos(USUARIO_ID),
                lista("l1", USUARIO_ID, "Alimentación")));

        List<ResumenListaProductosResponse> listas = servicio.consultarListas(CORREO);

        verify(favoritosRegistroService).crearListaPredeterminada(USUARIO_ID);
        assertEquals(List.of("Favoritos", "Alimentación", "videojuegos"),
                listas.stream().map(ResumenListaProductosResponse::nombre).toList());
        assertTrue(listas.get(0).predeterminada());
    }

    @Test
    void todoUsuarioTieneSuListaDeFavoritosAunqueEsteVacia() {
        Vendedor vendedor = mock(Vendedor.class);
        when(vendedor.isActivada()).thenReturn(true);
        when(vendedor.getId()).thenReturn("vendedor-1");
        when(usuarioDAO.findByEmail("vendedor@test.com")).thenReturn(Optional.of(vendedor));
        when(listaProductosDAO.findByUsuarioId("vendedor-1"))
                .thenReturn(List.of(ListaProductos.crearFavoritos("vendedor-1")));

        List<ResumenListaProductosResponse> listas = servicio.consultarListas("vendedor@test.com");

        verify(favoritosRegistroService).crearListaPredeterminada("vendedor-1");
        assertEquals(1, listas.size());
        assertTrue(listas.get(0).predeterminada());
        assertTrue(listas.get(0).productoIds().isEmpty());
    }

    @Test
    void elCorreoDeLaSesionSeNormalizaAntesDeBuscarAlUsuario() {
        prepararUsuario();
        when(listaProductosDAO.findByUsuarioId(USUARIO_ID)).thenReturn(List.of());

        servicio.consultarListas("  Cliente@Test.COM ");

        verify(usuarioDAO).findByEmail(CORREO);
    }

    @Test
    void eliminarUnaListaNoAfectaALasDemasListasDelUsuario() {
        prepararUsuario();
        ListaProductos comida = lista("l1", USUARIO_ID, "Comida");
        comida.agregarProducto("p1");
        when(listaProductosDAO.findById("l1")).thenReturn(Optional.of(comida));

        servicio.eliminar(CORREO, "l1");

        verify(listaProductosDAO).delete(comida);
        verify(listaProductosDAO, never()).deleteByUsuarioId(any());
        verify(listaProductosDAO, never()).deleteAll();
    }

    @Test
    void consultarListaPropiaDevuelveSusDatos() {
        prepararUsuario();
        when(listaProductosDAO.findById("l1"))
                .thenReturn(Optional.of(lista("l1", USUARIO_ID, "Alimentación")));

        ListaProductosResponse respuesta = servicio.consultarLista(CORREO, "l1");

        assertEquals("l1", respuesta.id());
        assertEquals("Alimentación", respuesta.nombre());
        assertTrue(respuesta.productos().isEmpty());
    }

    @Test
    void consultarListaAjenaSeTrataComoInexistente() {
        prepararUsuario();
        when(listaProductosDAO.findById("ajena"))
                .thenReturn(Optional.of(lista("ajena", OTRO_USUARIO_ID, "Privada")));

        assertThrows(ListaNoEncontradaException.class,
                () -> servicio.consultarLista(CORREO, "ajena"));
    }

    @Test
    void consultarListaInexistenteLanzaNoEncontrada() {
        prepararUsuario();
        when(listaProductosDAO.findById("nada")).thenReturn(Optional.empty());

        assertThrows(ListaNoEncontradaException.class,
                () -> servicio.consultarLista(CORREO, "nada"));
    }

    @Test
    void usuarioBloqueadoNoAccedeALasListas() {
        Cliente cliente = mock(Cliente.class);
        when(cliente.isActivada()).thenReturn(true);
        when(cliente.isBloqueada()).thenReturn(true);
        when(usuarioDAO.findByEmail(CORREO)).thenReturn(Optional.of(cliente));

        assertThrows(AccessDeniedException.class, () -> servicio.consultarListas(CORREO));
        verifyNoInteractions(listaProductosDAO);
    }

    @Test
    void sinCorreoNoSeAccede() {
        assertThrows(AccessDeniedException.class, () -> servicio.consultarListas(" "));
        verifyNoInteractions(usuarioDAO, listaProductosDAO);
    }

    // Crear

    @Test
    void crearListaGuardaNombreRecortadoYNoPredeterminada() {
        prepararUsuario();
        when(listaProductosDAO.countByUsuarioId(USUARIO_ID)).thenReturn(1L);
        when(listaProductosDAO.findByUsuarioId(USUARIO_ID))
                .thenReturn(List.of(ListaProductos.crearFavoritos(USUARIO_ID)));
        guardarDevuelveLoMismo();

        ListaProductosResponse respuesta = servicio.crear(CORREO, "  Videojuegos  ");

        ArgumentCaptor<ListaProductos> guardada = ArgumentCaptor.forClass(ListaProductos.class);
        verify(listaProductosDAO).save(guardada.capture());
        assertEquals("Videojuegos", guardada.getValue().getNombre());
        assertEquals(USUARIO_ID, guardada.getValue().getUsuarioId());
        assertFalse(guardada.getValue().isPredeterminada());
        assertEquals("Videojuegos", respuesta.nombre());
        verify(favoritosRegistroService).crearListaPredeterminada(USUARIO_ID);
    }

    @Test
    void crearConNombreVacioEsRechazado() {
        prepararUsuario();

        assertThrows(IllegalArgumentException.class, () -> servicio.crear(CORREO, "   "));
        assertThrows(IllegalArgumentException.class, () -> servicio.crear(CORREO, null));
        verify(listaProductosDAO, never()).save(any());
    }

    @Test
    void crearConNombreDemasiadoLargoEsRechazado() {
        prepararUsuario();

        String nombreLargo = "a".repeat(ListaProductosService.MAX_NOMBRE + 1);

        assertThrows(IllegalArgumentException.class, () -> servicio.crear(CORREO, nombreLargo));
        verify(listaProductosDAO, never()).save(any());
    }

    @Test
    void crearConNombreDeCincuentaCaracteresSeAcepta() {
        prepararUsuario();
        when(listaProductosDAO.findByUsuarioId(USUARIO_ID)).thenReturn(List.of());
        guardarDevuelveLoMismo();

        String nombre = "a".repeat(ListaProductosService.MAX_NOMBRE);

        assertEquals(nombre, servicio.crear(CORREO, nombre).nombre());
    }

    @Test
    void crearConNombreRepetidoSinDistinguirMayusculasEsRechazado() {
        prepararUsuario();
        when(listaProductosDAO.findByUsuarioId(USUARIO_ID))
                .thenReturn(List.of(lista("l1", USUARIO_ID, "Videojuegos")));

        assertThrows(NombreListaDuplicadoException.class,
                () -> servicio.crear(CORREO, "VIDEOJUEGOS"));
        verify(listaProductosDAO, never()).save(any());
    }

    @Test
    void elNombreDeOtroUsuarioNoCuentaComoRepetido() {
        prepararUsuario();
        // Solo se consultan las listas del propio usuario.
        when(listaProductosDAO.findByUsuarioId(USUARIO_ID)).thenReturn(List.of());
        guardarDevuelveLoMismo();

        assertEquals("Videojuegos", servicio.crear(CORREO, "Videojuegos").nombre());
        verify(listaProductosDAO, never()).findByUsuarioId(OTRO_USUARIO_ID);
    }

    @Test
    void elNombreFavoritosEstaReservado() {
        prepararUsuario();

        assertThrows(NombreListaDuplicadoException.class, () -> servicio.crear(CORREO, " favoritos "));
        verify(listaProductosDAO, never()).save(any());
    }

    @Test
    void noSePuedenCrearMasListasQueElMaximo() {
        prepararUsuario();
        when(listaProductosDAO.countByUsuarioId(USUARIO_ID))
                .thenReturn((long) ListaProductosService.MAX_LISTAS);

        assertThrows(OperacionListaNoPermitidaException.class,
                () -> servicio.crear(CORREO, "Otra"));
        verify(listaProductosDAO, never()).save(any());
    }

    // Renombrar

    @Test
    void renombrarCambiaElNombre() {
        prepararUsuario();
        ListaProductos existente = lista("l1", USUARIO_ID, "Comida");
        when(listaProductosDAO.findById("l1")).thenReturn(Optional.of(existente));
        when(listaProductosDAO.findByUsuarioId(USUARIO_ID)).thenReturn(List.of(existente));
        guardarDevuelveLoMismo();

        ListaProductosResponse respuesta = servicio.renombrar(CORREO, "l1", "Alimentación");

        assertEquals("Alimentación", respuesta.nombre());
        verify(listaProductosDAO).save(existente);
    }

    @Test
    void renombrarConElMismoNombreEnOtraCapitalizacionSePermite() {
        prepararUsuario();
        ListaProductos existente = lista("l1", USUARIO_ID, "comida");
        when(listaProductosDAO.findById("l1")).thenReturn(Optional.of(existente));
        when(listaProductosDAO.findByUsuarioId(USUARIO_ID)).thenReturn(List.of(existente));
        guardarDevuelveLoMismo();

        assertEquals("Comida", servicio.renombrar(CORREO, "l1", "Comida").nombre());
    }

    @Test
    void renombrarAUnNombreDeOtraListaPropiaEsRechazado() {
        prepararUsuario();
        ListaProductos existente = lista("l1", USUARIO_ID, "Comida");
        when(listaProductosDAO.findById("l1")).thenReturn(Optional.of(existente));
        when(listaProductosDAO.findByUsuarioId(USUARIO_ID))
                .thenReturn(List.of(existente, lista("l2", USUARIO_ID, "Juegos")));

        assertThrows(NombreListaDuplicadoException.class,
                () -> servicio.renombrar(CORREO, "l1", "juegos"));
        verify(listaProductosDAO, never()).save(any());
    }

    @Test
    void laListaDeFavoritosNoSeRenombra() {
        prepararUsuario();
        ListaProductos favoritos = ListaProductos.crearFavoritos(USUARIO_ID);
        String idFavoritos = favoritos.getId();
        when(listaProductosDAO.findById(idFavoritos)).thenReturn(Optional.of(favoritos));

        assertThrows(OperacionListaNoPermitidaException.class,
                () -> servicio.renombrar(CORREO, idFavoritos, "Otra"));
        verify(listaProductosDAO, never()).save(any());
    }

    @Test
    void renombrarListaAjenaEsRechazado() {
        prepararUsuario();
        when(listaProductosDAO.findById("ajena"))
                .thenReturn(Optional.of(lista("ajena", OTRO_USUARIO_ID, "Privada")));

        assertThrows(ListaNoEncontradaException.class,
                () -> servicio.renombrar(CORREO, "ajena", "Mía"));
        verify(listaProductosDAO, never()).save(any());
    }

    // Eliminar

    @Test
    void eliminarListaConProductosBorraSoloLaLista() {
        prepararUsuario();
        ListaProductos existente = lista("l1", USUARIO_ID, "Comida");
        existente.agregarProducto("producto-1");
        existente.agregarProducto("producto-2");
        when(listaProductosDAO.findById("l1")).thenReturn(Optional.of(existente));

        servicio.eliminar(CORREO, "l1");

        verify(listaProductosDAO).delete(existente);
        verifyNoInteractions(productoDAO);
    }

    @Test
    void laListaDeFavoritosNoSeElimina() {
        prepararUsuario();
        ListaProductos favoritos = ListaProductos.crearFavoritos(USUARIO_ID);
        String idFavoritos = favoritos.getId();
        when(listaProductosDAO.findById(idFavoritos)).thenReturn(Optional.of(favoritos));

        assertThrows(OperacionListaNoPermitidaException.class,
                () -> servicio.eliminar(CORREO, idFavoritos));
        verify(listaProductosDAO, never()).delete(any());
    }

    // Añadir y quitar productos (T-39.2)

    private Producto producto(String id, boolean visible, int stock) {
        Producto producto = mock(Producto.class);
        lenient().when(producto.getId()).thenReturn(id);
        lenient().when(producto.getNombre()).thenReturn("Producto " + id);
        lenient().when(producto.isEsVisible()).thenReturn(visible);
        lenient().when(producto.getStock()).thenReturn(stock);
        lenient().when(producto.getPrecio()).thenReturn(10.0);
        return producto;
    }

    @Test
    void anadirProductoVisibleALaListaElegida() {
        prepararUsuario();
        ListaProductos existente = lista("l1", USUARIO_ID, "Comida");
        Producto producto = producto("p1", true, 5);
        when(productoDAO.findById("p1")).thenReturn(Optional.of(producto));
        when(listaProductosDAO.findById("l1")).thenReturn(Optional.of(existente));
        when(productoDAO.findAllById(List.of("p1"))).thenReturn(List.of(producto));
        guardarDevuelveLoMismo();

        ListaProductosResponse respuesta = servicio.anadirProducto(CORREO, "l1", "p1");

        assertEquals(Set.of("p1"), existente.getProductos());
        assertEquals(1, respuesta.productos().size());
        assertTrue(respuesta.productos().get(0).disponible());
        verify(listaProductosDAO).save(existente);
    }

    @Test
    void anadirUnProductoRepetidoNoCreaDuplicadosNiGuarda() {
        prepararUsuario();
        ListaProductos existente = lista("l1", USUARIO_ID, "Comida");
        existente.agregarProducto("p1");
        Producto producto = producto("p1", true, 5);
        when(productoDAO.findById("p1")).thenReturn(Optional.of(producto));
        when(listaProductosDAO.findById("l1")).thenReturn(Optional.of(existente));
        when(productoDAO.findAllById(List.of("p1"))).thenReturn(List.of(producto));

        ListaProductosResponse respuesta = servicio.anadirProducto(CORREO, "l1", "p1");

        assertEquals(1, respuesta.productos().size());
        verify(listaProductosDAO, never()).save(any());
    }

    @Test
    void elMismoProductoPuedeEstarEnVariasListas() {
        prepararUsuario();
        ListaProductos comida = lista("l1", USUARIO_ID, "Comida");
        ListaProductos regalos = lista("l2", USUARIO_ID, "Regalos");
        Producto producto = producto("p1", true, 5);
        when(productoDAO.findById("p1")).thenReturn(Optional.of(producto));
        when(listaProductosDAO.findById("l1")).thenReturn(Optional.of(comida));
        when(listaProductosDAO.findById("l2")).thenReturn(Optional.of(regalos));
        when(productoDAO.findAllById(List.of("p1"))).thenReturn(List.of(producto));
        guardarDevuelveLoMismo();

        servicio.anadirProducto(CORREO, "l1", "p1");
        servicio.anadirProducto(CORREO, "l2", "p1");

        assertEquals(Set.of("p1"), comida.getProductos());
        assertEquals(Set.of("p1"), regalos.getProductos());
    }

    @Test
    void noSeAnadeUnProductoDesactivadoOInexistente() {
        Producto desactivado = producto("p1", false, 5);
        when(productoDAO.findById("p1")).thenReturn(Optional.of(desactivado));
        when(productoDAO.findById("nada")).thenReturn(Optional.empty());

        assertThrows(ProductoNoEncontradoException.class,
                () -> servicio.anadirProducto(CORREO, "l1", "p1"));
        assertThrows(ProductoNoEncontradoException.class,
                () -> servicio.anadirProducto(CORREO, "l1", "nada"));
        verify(listaProductosDAO, never()).save(any());
    }

    @Test
    void anadirSinIdentificadorDeProductoEsRechazado() {
        assertThrows(IllegalArgumentException.class,
                () -> servicio.anadirProducto(CORREO, "l1", " "));
        assertThrows(IllegalArgumentException.class,
                () -> servicio.quitarProducto(CORREO, "l1", null));
        verifyNoInteractions(listaProductosDAO, productoDAO);
    }

    @Test
    void anadirAUnaListaAjenaEsRechazado() {
        prepararUsuario();
        Producto producto = producto("p1", true, 5);
        when(productoDAO.findById("p1")).thenReturn(Optional.of(producto));
        ListaProductos ajena = lista("ajena", OTRO_USUARIO_ID, "Privada");
        when(listaProductosDAO.findById("ajena")).thenReturn(Optional.of(ajena));

        assertThrows(ListaNoEncontradaException.class,
                () -> servicio.anadirProducto(CORREO, "ajena", "p1"));
        assertTrue(ajena.getProductos().isEmpty());
        verify(listaProductosDAO, never()).save(any());
    }

    @Test
    void quitarProductoDeLaLista() {
        prepararUsuario();
        ListaProductos existente = lista("l1", USUARIO_ID, "Comida");
        existente.agregarProducto("p1");
        when(listaProductosDAO.findById("l1")).thenReturn(Optional.of(existente));
        guardarDevuelveLoMismo();

        ListaProductosResponse respuesta = servicio.quitarProducto(CORREO, "l1", "p1");

        assertTrue(existente.getProductos().isEmpty());
        assertTrue(respuesta.productos().isEmpty());
        verify(listaProductosDAO).save(existente);
        verify(productoDAO, never()).findById(any());
    }

    @Test
    void quitarDeUnaListaAjenaEsRechazado() {
        prepararUsuario();
        ListaProductos ajena = lista("ajena", OTRO_USUARIO_ID, "Privada");
        ajena.agregarProducto("p1");
        when(listaProductosDAO.findById("ajena")).thenReturn(Optional.of(ajena));

        assertThrows(ListaNoEncontradaException.class,
                () -> servicio.quitarProducto(CORREO, "ajena", "p1"));
        assertEquals(Set.of("p1"), ajena.getProductos());
        verify(listaProductosDAO, never()).save(any());
    }

    @Test
    void unProductoNoDisponiblePermaneceEnLaListaPeroNoEsComprable() {
        prepararUsuario();
        ListaProductos existente = lista("l1", USUARIO_ID, "Comida");
        existente.agregarProducto("desactivado");
        existente.agregarProducto("eliminado");
        Producto desactivado = producto("desactivado", false, 5);
        when(listaProductosDAO.findById("l1")).thenReturn(Optional.of(existente));
        when(productoDAO.findAllById(List.of("desactivado", "eliminado")))
                .thenReturn(List.of(desactivado));

        ListaProductosResponse respuesta = servicio.consultarLista(CORREO, "l1");

        assertEquals(2, respuesta.productos().size());
        assertFalse(respuesta.productos().get(0).disponible());
        assertEquals("Producto no disponible", respuesta.productos().get(0).aviso());
        assertFalse(respuesta.productos().get(1).disponible());
        assertEquals("Producto eliminado", respuesta.productos().get(1).nombre());
    }

    @Test
    void reintentaSiLaListaSeModificaALaVez() {
        prepararUsuario();
        ListaProductos existente = lista("l1", USUARIO_ID, "Comida");
        Producto producto = producto("p1", true, 5);
        when(productoDAO.findById("p1")).thenReturn(Optional.of(producto));
        when(listaProductosDAO.findById("l1"))
                .thenReturn(Optional.of(existente))
                .thenReturn(Optional.of(lista("l1", USUARIO_ID, "Comida")));
        when(productoDAO.findAllById(List.of("p1"))).thenReturn(List.of(producto));
        when(listaProductosDAO.save(any(ListaProductos.class)))
                .thenThrow(new OptimisticLockingFailureException("conflicto"))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        ListaProductosResponse respuesta = servicio.anadirProducto(CORREO, "l1", "p1");

        assertEquals(1, respuesta.productos().size());
        verify(listaProductosDAO, times(2)).save(any());
    }

    @Test
    void eliminarListaAjenaEsRechazado() {
        prepararUsuario();
        when(listaProductosDAO.findById("ajena"))
                .thenReturn(Optional.of(lista("ajena", OTRO_USUARIO_ID, "Privada")));

        assertThrows(ListaNoEncontradaException.class, () -> servicio.eliminar(CORREO, "ajena"));
        verify(listaProductosDAO, never()).delete(any());
    }
}
