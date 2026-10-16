package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import es.uclm.esibuy.backend.dao.CategoriaDAO;
import es.uclm.esibuy.backend.dao.VendedorDAO;
import es.uclm.esibuy.backend.model.Vendedor;

@ExtendWith(MockitoExtension.class)
class VendedorServiceTests {

    @Mock
    private VendedorDAO vendedorDAO;

    @Mock
    private CategoriaDAO categoriaDAO;

    @Mock
    private SoporteRegistro soporteRegistro;

    private VendedorService vendedorService;

    @BeforeEach
    void setUp() {
        vendedorService = new VendedorService(vendedorDAO, categoriaDAO, soporteRegistro);
    }

    @Test
    void devuelveNombreComercialDelVendedorEncontradoPorEmail() {
        Vendedor vendedor = new Vendedor();
        vendedor.setEmail("vendedor@example.com");
        vendedor.setNombreComercial("Tienda Ejemplo");
        vendedor.setActivada(true);
        when(vendedorDAO.findByEmail("vendedor@example.com")).thenReturn(Optional.of(vendedor));

        String nombreComercial = vendedorService.obtenerNombreComercial("vendedor@example.com");

        assertEquals("Tienda Ejemplo", nombreComercial);
        verify(vendedorDAO).findByEmail("vendedor@example.com");
    }

    @Test
    void rechazaVendedorInactivo() {
        Vendedor vendedor = new Vendedor();
        vendedor.setActivada(false);
        when(vendedorDAO.findByEmail("vendedor@example.com")).thenReturn(Optional.of(vendedor));

        assertThrows(
                AccessDeniedException.class,
                () -> vendedorService.obtenerNombreComercial("vendedor@example.com"));
    }

    @Test
    void rechazaVendedorBloqueado() {
        Vendedor vendedor = new Vendedor();
        vendedor.setActivada(true);
        vendedor.setBloqueada(true);
        when(vendedorDAO.findByEmail("vendedor@example.com")).thenReturn(Optional.of(vendedor));

        assertThrows(
                AccessDeniedException.class,
                () -> vendedorService.obtenerNombreComercial("vendedor@example.com"));
    }
}
