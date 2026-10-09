package es.uclm.esibuy.backend.controllers;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import es.uclm.esibuy.backend.config.SecurityConfiguration;
import es.uclm.esibuy.backend.dto.EstadisticasUsuariosResponse;
import es.uclm.esibuy.backend.dto.ListadoUsuariosResponse;
import es.uclm.esibuy.backend.dto.UsuarioAdministracionResponse;
import es.uclm.esibuy.backend.services.AdministradorService;

@WebMvcTest(controllers = AdministradorController.class)
@Import(SecurityConfiguration.class)
class AdministradorControllerHU04Tests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdministradorService administradorService;

    @Test
    void administradorPuedeConsultarElListadoConFiltros() throws Exception {
        PageRequest pagina = PageRequest.of(
                0,
                20,
                Sort.by("apellidos").ascending());

        UsuarioAdministracionResponse usuario = new UsuarioAdministracionResponse(
                "usuario-id",
                "Ana",
                "Activa",
                "ana@example.com",
                "600000001",
                "ACTIVO",
                "CLIENTE",
                null,
                null,
                null,
                true,
                null,
                null,
                null,
                null);

        ListadoUsuariosResponse respuesta = new ListadoUsuariosResponse(
                List.of(usuario),
                0,
                20,
                1,
                1,
                new EstadisticasUsuariosResponse(1, 1, 0, 0));

        when(administradorService.consultarUsuarios(
                "Ana",
                "ACTIVO",
                "CLIENTE",
                pagina))
                .thenReturn(respuesta);

        mockMvc.perform(get("/administrador/usuarios")
                        .with(user("admin").roles("ADMIN"))
                        .param("busqueda", "Ana")
                        .param("estado", "ACTIVO")
                        .param("rol", "CLIENTE")
                        .param("page", "0")
                        .param("size", "20")
                        .param("sort", "apellidos,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].nombre").value("Ana"))
                .andExpect(jsonPath("$.contenido[0].estado").value("ACTIVO"))
                .andExpect(jsonPath("$.estadisticas.totalUsuarios").value(1))
                .andExpect(jsonPath("$.contenido[0].contrasenaHash").doesNotExist());

        verify(administradorService)
                .consultarUsuarios("Ana", "ACTIVO", "CLIENTE", pagina);
    }

    @Test
    void clienteNoPuedeConsultarElListado() throws Exception {
        mockMvc.perform(get("/administrador/usuarios")
                        .with(user("cliente").roles("CLIENTE")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(administradorService);
    }

    @Test
    void vendedorNoPuedeConsultarElDetalle() throws Exception {
        mockMvc.perform(get("/administrador/usuarios/usuario-id")
                        .with(user("vendedor").roles("VENDEDOR")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(administradorService);
    }

    @Test
    void administradorPuedeConsultarElDetalle() throws Exception {
        UsuarioAdministracionResponse respuesta = new UsuarioAdministracionResponse(
                "usuario-id",
                "Ana",
                "Activa",
                "ana@example.com",
                null,
                "ACTIVO",
                "CLIENTE",
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                null);

        when(administradorService.consultarUsuario("usuario-id"))
                .thenReturn(respuesta);

        mockMvc.perform(get("/administrador/usuarios/usuario-id")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@example.com"))
                .andExpect(jsonPath("$.rol").value("CLIENTE"))
                .andExpect(jsonPath("$.contrasenaHash").doesNotExist());

        verify(administradorService).consultarUsuario("usuario-id");
    }
}