package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import es.uclm.esibuy.backend.dao.CategoriaDAO;
import es.uclm.esibuy.backend.model.Categoria;
import es.uclm.esibuy.backend.exceptions.NombreCategoriaDuplicadoException;

@ExtendWith(MockitoExtension.class)
class CategoriaServiceHU12Tests {

    @Mock
    private CategoriaDAO categoriaDAO;

    @InjectMocks
    private CategoriaService categoriaService;

    @Test
    void creaUnaCategoriaValida() {
        Categoria categoria = new Categoria("Hogar", "Productos para el hogar");

        Categoria guardada = new Categoria("Hogar", "Productos para el hogar");
        guardada.setId("categoria-1");

        when(categoriaDAO.save(categoria)).thenReturn(guardada);

        Categoria resultado = categoriaService.crearCategoria(categoria);

        assertEquals("categoria-1", resultado.getId());
        assertEquals("Hogar", resultado.getNombre());
        assertEquals("Productos para el hogar", resultado.getDescripcion());

        verify(categoriaDAO).save(categoria);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rechazaCategoriaConNombreInvalido(String nombre) {
        Categoria categoria = new Categoria(nombre, "Productos para el hogar");

        assertThrows(
                IllegalArgumentException.class,
                () -> categoriaService.crearCategoria(categoria)
        );

        verifyNoInteractions(categoriaDAO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rechazaCategoriaConDescripcionInvalida(String descripcion) {
        Categoria categoria = new Categoria("Hogar", descripcion);

        assertThrows(
                IllegalArgumentException.class,
                () -> categoriaService.crearCategoria(categoria)
        );

        verifyNoInteractions(categoriaDAO);
    }

    @Test
    void rechazaCategoriaConNombreDuplicado() {
        Categoria categoria = new Categoria("Hogar", "Productos para el hogar");

        when(categoriaDAO.existsByNombreIgnoreCase("Hogar")).thenReturn(true);

        assertThrows(
                NombreCategoriaDuplicadoException.class,
                () -> categoriaService.crearCategoria(categoria)
        );

        verify(categoriaDAO).existsByNombreIgnoreCase("Hogar");
    }
    @Test
    void normalizaEspaciosAntesDeComprobarDuplicadosYGuardar() {
        Categoria categoria = new Categoria("  Hogar  ", "  Productos para el hogar  ");
        when(categoriaDAO.save(categoria)).thenReturn(categoria);

        Categoria resultado = categoriaService.crearCategoria(categoria);

        assertEquals("Hogar", resultado.getNombre());
        assertEquals("Productos para el hogar", resultado.getDescripcion());
        verify(categoriaDAO).existsByNombreIgnoreCase("Hogar");
    }
}
