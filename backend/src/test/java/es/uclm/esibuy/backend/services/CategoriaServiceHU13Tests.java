package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import es.uclm.esibuy.backend.dao.CategoriaDAO;
import es.uclm.esibuy.backend.dao.ProductoDAO;
import es.uclm.esibuy.backend.dto.ModificarCategoriaRequest;
import es.uclm.esibuy.backend.exceptions.CategoriaNoEncontradaException;
import es.uclm.esibuy.backend.exceptions.NombreCategoriaDuplicadoException;
import es.uclm.esibuy.backend.model.Categoria;
import es.uclm.esibuy.backend.model.Producto;

@ExtendWith(MockitoExtension.class)
class CategoriaServiceHU13Tests {

        @Mock
        private CategoriaDAO categoriaDAO;

        @Mock
        private ProductoDAO productoDAO;

        @InjectMocks
        private CategoriaService categoriaService;

        @Test
        void modificaUnaCategoriaValida() {
                String id = "categoria-1";

                Categoria categoria = new Categoria(
                                "Hogar",
                                "Productos para el hogar");
                categoria.setId(id);

                Producto producto1 = new Producto();
                producto1.setCategoria("Hogar");

                Producto producto2 = new Producto();
                producto2.setCategoria("Hogar");

                List<Producto> productos = List.of(producto1, producto2);

                ModificarCategoriaRequest request = new ModificarCategoriaRequest(
                                "  Casa  ",
                                "  Artículos para el hogar  ");

                when(categoriaDAO.findById(id)).thenReturn(Optional.of(categoria));
                when(categoriaDAO.existsByNombreIgnoreCaseAndIdNot("Casa", id))
                                .thenReturn(false);
                when(categoriaDAO.save(categoria)).thenReturn(categoria);

                when(productoDAO.findByCategoriaIgnoreCase("Hogar"))
                                .thenReturn(productos);

                Categoria resultado = categoriaService.modificarCategoria(id, request);

                assertSame(categoria, resultado);
                assertEquals("Casa", resultado.getNombre());
                assertEquals("Artículos para el hogar", resultado.getDescripcion());

                assertEquals("Casa", producto1.getCategoria());
                assertEquals("Casa", producto2.getCategoria());

                verify(categoriaDAO).existsByNombreIgnoreCaseAndIdNot("Casa", id);
                verify(categoriaDAO).save(categoria);

                verify(productoDAO).findByCategoriaIgnoreCase("Hogar");
                verify(productoDAO).saveAll(productos);
        }

        @Test
        void rechazaModificarUnaCategoriaInexistente() {
                String id = "categoria-inexistente";
                ModificarCategoriaRequest request = new ModificarCategoriaRequest(
                                "Casa",
                                "Artículos para el hogar");

                when(categoriaDAO.findById(id)).thenReturn(Optional.empty());

                assertThrows(
                                CategoriaNoEncontradaException.class,
                                () -> categoriaService.modificarCategoria(id, request));

                verify(categoriaDAO).findById(id);
                verifyNoMoreInteractions(categoriaDAO);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void rechazaModificarConNombreInvalido(String nombre) {
                String id = "categoria-1";
                Categoria categoria = new Categoria("Hogar", "Productos para el hogar");
                ModificarCategoriaRequest request = new ModificarCategoriaRequest(
                                nombre,
                                "Artículos para el hogar");
                when(categoriaDAO.findById(id)).thenReturn(Optional.of(categoria));

                assertThrows(
                                IllegalArgumentException.class,
                                () -> categoriaService.modificarCategoria(id, request));

                verify(categoriaDAO).findById(id);
                verifyNoMoreInteractions(categoriaDAO);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void rechazaModificarConDescripcionInvalida(String descripcion) {
                String id = "categoria-1";
                Categoria categoria = new Categoria("Hogar", "Productos para el hogar");
                ModificarCategoriaRequest request = new ModificarCategoriaRequest(
                                "Casa",
                                descripcion);
                when(categoriaDAO.findById(id)).thenReturn(Optional.of(categoria));

                assertThrows(
                                IllegalArgumentException.class,
                                () -> categoriaService.modificarCategoria(id, request));

                verify(categoriaDAO).findById(id);
                verifyNoMoreInteractions(categoriaDAO);
        }

        @Test
        void rechazaModificarConNombreDuplicado() {
                String id = "categoria-1";
                Categoria categoria = new Categoria("Hogar", "Productos para el hogar");
                ModificarCategoriaRequest request = new ModificarCategoriaRequest(
                                "Casa",
                                "Artículos para el hogar");
                when(categoriaDAO.findById(id)).thenReturn(Optional.of(categoria));
                when(categoriaDAO.existsByNombreIgnoreCaseAndIdNot("Casa", id)).thenReturn(true);

                assertThrows(
                                NombreCategoriaDuplicadoException.class,
                                () -> categoriaService.modificarCategoria(id, request));

                verify(categoriaDAO).existsByNombreIgnoreCaseAndIdNot("Casa", id);
                verifyNoMoreInteractions(categoriaDAO);
        }

        @Test
        void noActualizaProductosSiNoCambiaElNombreDeLaCategoria() {
                String id = "categoria-1";

                Categoria categoria = new Categoria(
                                "Hogar",
                                "Productos para el hogar");
                categoria.setId(id);

                ModificarCategoriaRequest request = new ModificarCategoriaRequest(
                                "Hogar",
                                "Nueva descripción");

                when(categoriaDAO.findById(id)).thenReturn(Optional.of(categoria));
                when(categoriaDAO.existsByNombreIgnoreCaseAndIdNot("Hogar", id))
                                .thenReturn(false);
                when(categoriaDAO.save(categoria)).thenReturn(categoria);

                categoriaService.modificarCategoria(id, request);

                verify(productoDAO, never()).findByCategoriaIgnoreCase("Hogar");
        }
}
