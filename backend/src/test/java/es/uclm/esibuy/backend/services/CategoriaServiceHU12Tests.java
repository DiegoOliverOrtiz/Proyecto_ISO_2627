package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import es.uclm.esibuy.backend.dao.CategoriaDAO;
import es.uclm.esibuy.backend.model.Categoria;

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

        @Test
        void rechazaCategoriaConNombreVacio() {
                Categoria categoria = new Categoria("", "Productos para el hogar");

                assertThrows(IllegalArgumentException.class,
                                () -> categoriaService.crearCategoria(categoria));

                verifyNoInteractions(categoriaDAO);
        }

        @Test
        void rechazaCategoriaConNombreSoloEspacios() {
                Categoria categoria = new Categoria("   ", "Productos para el hogar");

                assertThrows(
                                IllegalArgumentException.class,
                                () -> categoriaService.crearCategoria(categoria));

                verifyNoInteractions(categoriaDAO);
        }

        @Test
        void rechazaCategoriaConDescripcionVacia() {
                Categoria categoria = new Categoria("Hogar", "");

                assertThrows(
                                IllegalArgumentException.class,
                                () -> categoriaService.crearCategoria(categoria));

                verifyNoInteractions(categoriaDAO);
        }
}