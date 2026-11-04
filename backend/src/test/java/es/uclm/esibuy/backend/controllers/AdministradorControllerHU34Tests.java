package es.uclm.esibuy.backend.controllers;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import es.uclm.esibuy.backend.config.SecurityConfiguration;
import es.uclm.esibuy.backend.dto.ProductoAdministracionResponse;
import es.uclm.esibuy.backend.services.AdministradorService;
import es.uclm.esibuy.backend.services.ProductoService;

@WebMvcTest(controllers = AdministradorController.class)
@Import(SecurityConfiguration.class)
class AdministradorControllerHU34Tests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdministradorService administradorService;

    @MockitoBean
    private ProductoService productoService;

    @Test
    void administradorConsultaLosProductosConVendedorYEstado() throws Exception {
        when(productoService.consultarProductosParaAdministrador()).thenReturn(List.of(
                new ProductoAdministracionResponse(
                        "p1", "Teclado", "Informática", 3, "Teclado mecánico", 49.99, 10, 20,
                        true, "/product-images/teclado.png", false, "Tienda A"),
                new ProductoAdministracionResponse(
                        "p2", "Ratón", "Informática", 0, null, 15.0, 0, 0,
                        false, null, true, "Tienda B")));

        mockMvc.perform(get("/administrador/productos")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nombre").value("Teclado"))
                .andExpect(jsonPath("$[0].vendedor").value("Tienda A"))
                .andExpect(jsonPath("$[0].esVisible").value(true))
                .andExpect(jsonPath("$[0].precio").value(49.99))
                .andExpect(jsonPath("$[0].descuentoPremium").value(20))
                .andExpect(jsonPath("$[1].vendedor").value("Tienda B"))
                .andExpect(jsonPath("$[1].esVisible").value(false))
                .andExpect(jsonPath("$[1].soloPremium").value(true));

        verify(productoService).consultarProductosParaAdministrador();
    }

    @Test
    void administradorRecibeUnaListaVaciaSiNoHayProductos() throws Exception {
        when(productoService.consultarProductosParaAdministrador()).thenReturn(List.of());

        mockMvc.perform(get("/administrador/productos")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
