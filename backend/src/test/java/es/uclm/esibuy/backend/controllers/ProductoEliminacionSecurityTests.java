package es.uclm.esibuy.backend.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import es.uclm.esibuy.backend.config.SecurityConfiguration;
import es.uclm.esibuy.backend.dao.CategoriaDAO;
import es.uclm.esibuy.backend.dao.ClienteDAO;
import es.uclm.esibuy.backend.dao.PedidoDAO;
import es.uclm.esibuy.backend.dao.ProductoDAO;
import es.uclm.esibuy.backend.dao.VendedorDAO;
import es.uclm.esibuy.backend.model.Producto;
import es.uclm.esibuy.backend.model.Vendedor;
import es.uclm.esibuy.backend.services.PerfilService;
import es.uclm.esibuy.backend.services.ProductoImagenService;
import es.uclm.esibuy.backend.services.ProductoService;
import es.uclm.esibuy.backend.services.SesionService;
import es.uclm.esibuy.backend.services.VendedorService;

// HU-33 (T-33.4): acceso a productos ajenos y CSRF en el borrado, con el servicio real y los DAO simulados.
@WebMvcTest(VendedorController.class)
@Import({SecurityConfiguration.class, ProductoService.class})
class ProductoEliminacionSecurityTests {

    private static final String RUTA = "/vendedor/productos/producto-a";
    private static final String CORREO_A = "vendedor-a@test.com";
    private static final String CORREO_B = "vendedor-b@test.com";
    private static final String TIENDA_A = "Tienda A";
    private static final String TIENDA_B = "Tienda B";
    private static final String IMAGEN = "/product-images/producto-a.png";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProductoDAO productoDAO;

    @MockitoBean
    private VendedorDAO vendedorDAO;

    @MockitoBean
    private CategoriaDAO categoriaDAO;

    @MockitoBean
    private ClienteDAO clienteDAO;

    @MockitoBean
    private PedidoDAO pedidoDAO;

    @MockitoBean
    private ProductoImagenService productoImagenService;

    @MockitoBean
    private VendedorService vendedorService;

    @MockitoBean
    private PerfilService perfilService;

    @MockitoBean
    private SesionService sesionService;

    private Producto productoDeA;

    @BeforeEach
    void preparar() {
        lenient().when(vendedorDAO.findByEmail(CORREO_A)).thenReturn(Optional.of(vendedor(CORREO_A, TIENDA_A, false)));
        lenient().when(vendedorDAO.findByEmail(CORREO_B)).thenReturn(Optional.of(vendedor(CORREO_B, TIENDA_B, false)));
        productoDeA = new Producto();
        productoDeA.setId("producto-a");
        productoDeA.setNombre("Teclado");
        productoDeA.setVendedor(TIENDA_A);
        productoDeA.setImagen(IMAGEN);
        lenient().when(productoDAO.findById("producto-a")).thenReturn(Optional.of(productoDeA));
    }

    private Vendedor vendedor(String correo, String nombreComercial, boolean bloqueado) {
        Vendedor vendedor = new Vendedor();
        vendedor.setEmail(correo);
        vendedor.setNombreComercial(nombreComercial);
        vendedor.setActivada(true);
        vendedor.setBloqueada(bloqueado);
        return vendedor;
    }

    @Test
    void vendedorBNoPuedeEliminarElProductoDelVendedorA() throws Exception {
        mvc.perform(delete(RUTA)
                        .with(user(CORREO_B).roles("VENDEDOR"))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("No puedes eliminar productos de otro vendedor."))
                .andExpect(jsonPath("$.nombre").doesNotExist())
                .andExpect(jsonPath("$.imagen").doesNotExist());

        verify(productoDAO, never()).delete(any());
        verify(productoDAO, never()).deleteById(any());
        verifyNoInteractions(pedidoDAO, productoImagenService);
    }

    @Test
    void elVendedorPropietarioSiPuedeEliminarlo() throws Exception {
        mvc.perform(delete(RUTA)
                        .with(user(CORREO_A).roles("VENDEDOR"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(productoDAO).delete(productoDeA);
        verify(productoImagenService).eliminar(IMAGEN);
    }

    @Test
    void elPropietarioRecibe409SiHayPedidosEnCurso() throws Exception {
        when(pedidoDAO.existsByProductoIdAndEstadoIn(any(), any())).thenReturn(true);

        mvc.perform(delete(RUTA)
                        .with(user(CORREO_A).roles("VENDEDOR"))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(
                        "No se puede eliminar el producto porque figura en pedidos en curso (pagados o enviados)."));

        verify(productoDAO, never()).delete(any());
        verifyNoInteractions(productoImagenService);
    }

    @Test
    void sinTokenCsrfSeRechazaSinLlegarAlServicio() throws Exception {
        mvc.perform(delete(RUTA)
                        .with(user(CORREO_A).roles("VENDEDOR")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productoDAO, pedidoDAO, productoImagenService);
    }

    @Test
    void conTokenCsrfInvalidoSeRechazaSinLlegarAlServicio() throws Exception {
        mvc.perform(delete(RUTA)
                        .with(user(CORREO_A).roles("VENDEDOR"))
                        .with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productoDAO, pedidoDAO, productoImagenService);
    }

    @Test
    void unClienteNoPuedeEliminarProductos() throws Exception {
        mvc.perform(delete(RUTA)
                        .with(user("cliente@test.com").roles("CLIENTE"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productoDAO, pedidoDAO, productoImagenService);
    }

    @Test
    void unAdministradorNoPuedeEliminarProductos() throws Exception {
        mvc.perform(delete(RUTA)
                        .with(user("admin@test.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productoDAO, pedidoDAO, productoImagenService);
    }

    @Test
    void sinSesionNoSePuedeEliminar() throws Exception {
        mvc.perform(delete(RUTA).with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productoDAO, pedidoDAO, productoImagenService);
    }

    @Test
    void unVendedorBloqueadoNoPuedeEliminarSuProducto() throws Exception {
        when(vendedorDAO.findByEmail(CORREO_A)).thenReturn(Optional.of(vendedor(CORREO_A, TIENDA_A, true)));

        mvc.perform(delete(RUTA)
                        .with(user(CORREO_A).roles("VENDEDOR"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(productoDAO, never()).delete(any());
        verifyNoInteractions(pedidoDAO, productoImagenService);
    }
}
