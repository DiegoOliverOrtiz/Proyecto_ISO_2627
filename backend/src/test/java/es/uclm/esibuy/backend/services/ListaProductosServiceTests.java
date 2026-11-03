package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import es.uclm.esibuy.backend.dao.ListaProductosDAO;
import es.uclm.esibuy.backend.dao.ProductoDAO;
import es.uclm.esibuy.backend.dao.UsuarioDAO;
import es.uclm.esibuy.backend.dto.ListaProductosResponse;
import es.uclm.esibuy.backend.dto.ResumenListaProductosResponse;
import es.uclm.esibuy.backend.exceptions.ListaNoEncontradaException;
import es.uclm.esibuy.backend.exceptions.NombreListaDuplicadoException;
import es.uclm.esibuy.backend.exceptions.OperacionListaNoPermitidaException;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.ListaProductos;

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

        assertThrows(IllegalArgumentException.class,
                () -> servicio.crear(CORREO, "a".repeat(ListaProductosService.MAX_NOMBRE + 1)));
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
        when(listaProductosDAO.findById(favoritos.getId())).thenReturn(Optional.of(favoritos));

        assertThrows(OperacionListaNoPermitidaException.class,
                () -> servicio.renombrar(CORREO, favoritos.getId(), "Otra"));
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
        when(listaProductosDAO.findById(favoritos.getId())).thenReturn(Optional.of(favoritos));

        assertThrows(OperacionListaNoPermitidaException.class,
                () -> servicio.eliminar(CORREO, favoritos.getId()));
        verify(listaProductosDAO, never()).delete(any());
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
