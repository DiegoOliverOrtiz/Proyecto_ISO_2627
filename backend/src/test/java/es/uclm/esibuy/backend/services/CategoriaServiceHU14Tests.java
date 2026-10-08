package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import es.uclm.esibuy.backend.dao.CategoriaDAO;
import es.uclm.esibuy.backend.dao.ProductoDAO;
import es.uclm.esibuy.backend.exceptions.CategoriaConProductosException;
import es.uclm.esibuy.backend.exceptions.CategoriaNoEncontradaException;
import es.uclm.esibuy.backend.model.Categoria;

@ExtendWith(MockitoExtension.class)
class CategoriaServiceHU14Tests {

    @Mock
    private CategoriaDAO categoriaDAO;

    @Mock
    private ProductoDAO productoDAO;

    @InjectMocks
    private CategoriaService categoriaService;

    @Test
    void eliminaCategoriaSinProductosAsociados() {
        String id = "categoria-1";
        Categoria categoria = new Categoria("Hogar", "Productos para el hogar");
        categoria.setId(id);

        when(categoriaDAO.findById(id)).thenReturn(Optional.of(categoria));
        when(productoDAO.existsByCategoriaIgnoreCase("Hogar")).thenReturn(false);

        categoriaService.eliminarCategoria(id);

        verify(categoriaDAO).findById(id);
        verify(productoDAO).existsByCategoriaIgnoreCase("Hogar");
        verify(categoriaDAO).delete(categoria);
    }

    @Test
    void rechazaEliminarCategoriaInexistente() {
        String id = "categoria-inexistente";

        when(categoriaDAO.findById(id)).thenReturn(Optional.empty());

        assertThrows(
                CategoriaNoEncontradaException.class,
                () -> categoriaService.eliminarCategoria(id));

        verify(categoriaDAO).findById(id);
        verify(productoDAO, never()).existsByCategoriaIgnoreCase(org.mockito.ArgumentMatchers.anyString());
        verify(categoriaDAO, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rechazaEliminarCategoriaConProductosAsociados() {
        String id = "categoria-1";
        Categoria categoria = new Categoria("Hogar", "Productos para el hogar");
        categoria.setId(id);

        when(categoriaDAO.findById(id)).thenReturn(Optional.of(categoria));
        when(productoDAO.existsByCategoriaIgnoreCase("Hogar")).thenReturn(true);

        assertThrows(
                CategoriaConProductosException.class,
                () -> categoriaService.eliminarCategoria(id));

        verify(categoriaDAO).findById(id);
        verify(productoDAO).existsByCategoriaIgnoreCase("Hogar");
        verify(categoriaDAO, never()).delete(categoria);
    }
}