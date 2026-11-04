package es.uclm.esibuy.backend.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import es.uclm.esibuy.backend.config.SecurityConfiguration;
import es.uclm.esibuy.backend.exceptions.UsuarioNoEncontradoException;
import es.uclm.esibuy.backend.services.AdministradorService;
import es.uclm.esibuy.backend.services.ProductoService;

// HU-34 (T-34.3): solo el administrador consulta los productos y no tiene ninguna escritura abierta.
@WebMvcTest(controllers = AdministradorController.class)
@Import(SecurityConfiguration.class)
class ProductosAdministradorSecurityTests {

    private static final String RUTA = "/administrador/productos";
    private static final String RUTA_PRODUCTO = RUTA + "/producto-1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdministradorService administradorService;

    @MockitoBean
    private ProductoService productoService;

    @Test
    void elAdministradorPuedeConsultarLosProductos() throws Exception {
        when(productoService.consultarProductosParaAdministrador()).thenReturn(List.of());

        mockMvc.perform(get(RUTA).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());

        verify(productoService).consultarProductosParaAdministrador();
    }

    @Test
    void unClienteRecibe403() throws Exception {
        mockMvc.perform(get(RUTA).with(user("cliente").roles("CLIENTE")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productoService);
    }

    @Test
    void unVendedorRecibe403() throws Exception {
        mockMvc.perform(get(RUTA).with(user("vendedor").roles("VENDEDOR")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productoService);
    }

    @Test
    void sinSesionSeRechaza() throws Exception {
        mockMvc.perform(get(RUTA))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productoService);
    }

    static Stream<Arguments> escriturasSobreElListado() {
        return Stream.of(
                Arguments.of(post(RUTA)),
                Arguments.of(put(RUTA)),
                Arguments.of(patch(RUTA)));
    }

    @ParameterizedTest
    @MethodSource("escriturasSobreElListado")
    void elListadoNoAdmiteVerbosDeEscritura(MockHttpServletRequestBuilder peticion) throws Exception {
        mockMvc.perform(peticion.with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(productoService);
    }

    @Test
    void deleteSobreElListadoLlegaAEliminarAdministradorYNoTocaProductos() throws Exception {
        // DELETE /administrador/{id} ya existe (eliminar administrador): "productos" se toma como un id
        // de administrador inexistente, así que responde 404 y no se elimina ningún producto.
        doThrow(new UsuarioNoEncontradoException())
                .when(administradorService).eliminarAdministrador("productos", "admin");

        mockMvc.perform(delete(RUTA).with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNotFound());

        verifyNoInteractions(productoService);
    }

    static Stream<Arguments> escriturasSobreUnProducto() {
        return Stream.of(
                Arguments.of(get(RUTA_PRODUCTO)),
                Arguments.of(post(RUTA_PRODUCTO)),
                Arguments.of(put(RUTA_PRODUCTO)),
                Arguments.of(patch(RUTA_PRODUCTO)),
                Arguments.of(delete(RUTA_PRODUCTO)),
                Arguments.of(patch(RUTA_PRODUCTO + "/activar")),
                Arguments.of(patch(RUTA_PRODUCTO + "/desactivar")));
    }

    @ParameterizedTest
    @MethodSource("escriturasSobreUnProducto")
    void noExistenOperacionesSobreUnProductoConcreto(MockHttpServletRequestBuilder peticion) throws Exception {
        mockMvc.perform(peticion.with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNotFound());

        verifyNoInteractions(productoService);
    }

    static Stream<Arguments> escriturasDelVendedor() {
        return Stream.of(
                Arguments.of(post("/vendedor/productos")),
                Arguments.of(put("/vendedor/productos/producto-1")),
                Arguments.of(delete("/vendedor/productos/producto-1")),
                Arguments.of(patch("/vendedor/productos/producto-1/activar")),
                Arguments.of(patch("/vendedor/productos/producto-1/desactivar")));
    }

    @ParameterizedTest
    @MethodSource("escriturasDelVendedor")
    void elAdministradorNoPuedeUsarLasEscriturasDelVendedor(MockHttpServletRequestBuilder peticion)
            throws Exception {
        mockMvc.perform(peticion.with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productoService);
    }

    @Test
    void elControladorSoloExponeLaConsultaDeProductosParaElAdministrador() throws NoSuchMethodException {
        Method consulta = AdministradorController.class.getMethod("consultarProductos");

        assertEquals("hasRole('ADMIN')", consulta.getAnnotation(PreAuthorize.class).value());
        GetMapping get = consulta.getAnnotation(GetMapping.class);
        assertNotNull(get);
        assertEquals(List.of("/productos"), Arrays.asList(get.value()));

        for (Method metodo : AdministradorController.class.getDeclaredMethods()) {
            List<String> rutasDeEscritura = rutasDeEscritura(metodo);
            assertTrue(
                    rutasDeEscritura.stream().noneMatch(ruta -> ruta.contains("productos")),
                    () -> metodo.getName() + " abre una escritura sobre productos: " + rutasDeEscritura);
        }
    }

    private static List<String> rutasDeEscritura(Method metodo) {
        Stream<String> rutas = Stream.empty();
        if (metodo.isAnnotationPresent(PostMapping.class)) {
            rutas = Stream.concat(rutas, Arrays.stream(metodo.getAnnotation(PostMapping.class).value()));
        }
        if (metodo.isAnnotationPresent(PutMapping.class)) {
            rutas = Stream.concat(rutas, Arrays.stream(metodo.getAnnotation(PutMapping.class).value()));
        }
        if (metodo.isAnnotationPresent(PatchMapping.class)) {
            rutas = Stream.concat(rutas, Arrays.stream(metodo.getAnnotation(PatchMapping.class).value()));
        }
        if (metodo.isAnnotationPresent(DeleteMapping.class)) {
            rutas = Stream.concat(rutas, Arrays.stream(metodo.getAnnotation(DeleteMapping.class).value()));
        }
        return rutas.toList();
    }
}
