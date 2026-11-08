package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import es.uclm.esibuy.backend.dao.CategoriaDAO;
import es.uclm.esibuy.backend.dao.ProductoDAO;
import es.uclm.esibuy.backend.model.Categoria;

@ExtendWith(MockitoExtension.class)
class CategoriaServiceHU11Tests {

    @Mock
    private CategoriaDAO categoriaDAO;

    @Mock
    private ProductoDAO productoDAO;

    @InjectMocks
    private CategoriaService categoriaService;

    @BeforeEach
    void configurar() {
        when(categoriaDAO.findAll()).thenReturn(List.of(
                new Categoria("Hogar", "Artículos domésticos"),
                new Categoria("Deportes", "Material deportivo"),
                new Categoria("Electrónica", "Dispositivos")
        ));
    }

    @Test
    void buscaPorNombreSinDistinguirMayusculas() {
        List<Categoria> resultado =
                categoriaService.buscarCategorias("DEP", "asc");

        assertEquals(1, resultado.size());
        assertEquals("Deportes", resultado.get(0).getNombre());
    }

    @Test
    void ordenaCategoriasAscendentemente() {
        List<Categoria> resultado =
                categoriaService.buscarCategorias("", "asc");

        assertEquals("Deportes", resultado.get(0).getNombre());
        assertEquals("Electrónica", resultado.get(1).getNombre());
        assertEquals("Hogar", resultado.get(2).getNombre());
    }

    @Test
    void ordenaCategoriasDescendentemente() {
        List<Categoria> resultado =
                categoriaService.buscarCategorias("", "desc");

        assertEquals("Hogar", resultado.get(0).getNombre());
        assertEquals("Electrónica", resultado.get(1).getNombre());
        assertEquals("Deportes", resultado.get(2).getNombre());
    }

    @Test
    void devuelveListaVaciaSiNoHayCoincidencias() {
        List<Categoria> resultado =
                categoriaService.buscarCategorias("Inexistente", "asc");

        assertEquals(0, resultado.size());
    }

    @Test
    void noInterpretaExpresionesRegulares() {
        List<Categoria> resultado =
                categoriaService.buscarCategorias(".*", "asc");

        assertEquals(0, resultado.size());
    }

    @Test
    void noInterpretaOperadoresMongoDB() {
        List<Categoria> resultado =
                categoriaService.buscarCategorias("$ne", "asc");

        assertEquals(0, resultado.size());
    }
}
