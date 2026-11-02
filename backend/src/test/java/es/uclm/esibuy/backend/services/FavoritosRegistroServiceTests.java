package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import es.uclm.esibuy.backend.dao.ListaProductosDAO;
import es.uclm.esibuy.backend.model.ListaProductos;

@ExtendWith(MockitoExtension.class)
class FavoritosRegistroServiceTests {

    @Mock
    private ListaProductosDAO listaProductosDAO;

    @InjectMocks
    private FavoritosRegistroService servicio;

    @BeforeEach
    void configurar() {
        // Sin preparación común: cada prueba define sus mocks.
    }

    @Test
    void creaListaPredeterminadaAlRegistrarUsuario() {
        when(listaProductosDAO.existsById("favoritos:usuario-1"))
                .thenReturn(false);

        servicio.crearListaPredeterminada("usuario-1");

        verify(listaProductosDAO).save(argThat(lista ->
                "favoritos:usuario-1".equals(lista.getId())
                && "usuario-1".equals(lista.getUsuarioId())
                && "Favoritos".equals(lista.getNombre())
                && lista.isPredeterminada()
                && lista.getProductos().isEmpty()
        ));
    }

    @Test
    void noCreaOtraListaSiYaExiste() {
        when(listaProductosDAO.existsById("favoritos:usuario-1"))
                .thenReturn(true);

        servicio.crearListaPredeterminada("usuario-1");

        verify(listaProductosDAO, never())
                .save(any(ListaProductos.class));
    }

    @Test
    void cadaUsuarioTieneIdentificadorDeListaDistinto() {
        when(listaProductosDAO.existsById(anyString()))
                .thenReturn(false);

        servicio.crearListaPredeterminada("usuario-1");
        servicio.crearListaPredeterminada("usuario-2");

        verify(listaProductosDAO).save(argThat(lista ->
                "favoritos:usuario-1".equals(lista.getId())
        ));

        verify(listaProductosDAO).save(argThat(lista ->
                "favoritos:usuario-2".equals(lista.getId())
        ));
    }

    @Test
    void noPermiteUsuarioNulo() {
        assertThrows(IllegalArgumentException.class,
                () -> servicio.crearListaPredeterminada(null));

        verifyNoInteractions(listaProductosDAO);
    }

    @Test
    void noPermiteUsuarioVacio() {
        assertThrows(IllegalArgumentException.class,
                () -> servicio.crearListaPredeterminada("  "));

        verifyNoInteractions(listaProductosDAO);
    }

    @Test
    void toleraDuplicadoSiOtraPeticionCreoLaLista() {
        when(listaProductosDAO.existsById("favoritos:usuario-1"))
                .thenReturn(false, true);

        when(listaProductosDAO.save(any(ListaProductos.class)))
                .thenThrow(new DuplicateKeyException("Duplicado"));

        assertDoesNotThrow(() ->
                servicio.crearListaPredeterminada("usuario-1")
        );
    }

    @Test
    void propagaDuplicadoSiListaNoExisteTrasElConflicto() {
        when(listaProductosDAO.existsById("favoritos:usuario-1"))
                .thenReturn(false);

        when(listaProductosDAO.save(any(ListaProductos.class)))
                .thenThrow(new DuplicateKeyException("Duplicado"));

        assertThrows(DuplicateKeyException.class,
                () -> servicio.crearListaPredeterminada("usuario-1")
        );
    }
}
