package es.uclm.esibuy.backend.controllers;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import es.uclm.esibuy.backend.exceptions.AutoBloqueoAdministradorException;
import es.uclm.esibuy.backend.services.AdministradorService;

@WebMvcTest(controllers = AdministradorController.class)
@Import(AdministradorControllerHU06Tests.MethodSecurityTestConfig.class)
class AdministradorControllerHU06Tests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdministradorService administradorService;

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Test
    void administradorPuedeBloquearUsuario() throws Exception {
        mockMvc.perform(
                patch("/administrador/usuarios/usuario-id/bloquear")
                        .with(user("admin@esibuy.es").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(administradorService)
                .bloquearUsuario(
                        "usuario-id",
                        "admin@esibuy.es");
    }

    @Test
    void administradorPuedeDesbloquearUsuario() throws Exception {
        mockMvc.perform(
                patch("/administrador/usuarios/usuario-id/desbloquear")
                        .with(user("admin@esibuy.es").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(administradorService)
                .desbloquearUsuario(
                        "usuario-id",
                        "admin@esibuy.es");
    }

    @Test
    void clienteNoPuedeBloquearUsuario() throws Exception {
        mockMvc.perform(
                patch("/administrador/usuarios/usuario-id/bloquear")
                        .with(user("cliente@example.com").roles("CLIENTE"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(administradorService);
    }

    @Test
    void vendedorNoPuedeDesbloquearUsuario() throws Exception {
        mockMvc.perform(
                patch("/administrador/usuarios/usuario-id/desbloquear")
                        .with(user("vendedor@example.com").roles("VENDEDOR"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(administradorService);
    }

    @Test
    void autobloqueoDevuelveConflicto() throws Exception {
        doThrow(new AutoBloqueoAdministradorException())
                .when(administradorService)
                .bloquearUsuario(
                        "admin-id",
                        "admin@esibuy.es");

        mockMvc.perform(
                patch("/administrador/usuarios/admin-id/bloquear")
                        .with(user("admin@esibuy.es").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error")
                        .value("Un administrador no puede bloquear ni desbloquear su propia cuenta."));
    }
}