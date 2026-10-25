package es.uclm.esibuy.backend.controllers;

import es.uclm.esibuy.backend.config.SecurityConfiguration;
import es.uclm.esibuy.backend.dto.PerfilResponse;
import es.uclm.esibuy.backend.services.PerfilService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PerfilController.class)
@Import(SecurityConfiguration.class)
class PerfilControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PerfilService perfilService;

    @Test
    void rechazaLaConsultaSinAutenticacion() throws Exception {
        mockMvc.perform(get("/usuarios/perfil"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(perfilService);
    }

    @Test
    void usaElNombreDeLaSesionYNoUnIdentificadorRecibidoPorLaPeticion() throws Exception {
        PerfilResponse perfil = new PerfilResponse(
                "CLIENTE", "Ana", "García", "ana@esibuy.com", "600123123",
                null, "12*******", LocalDate.of(2000, 1, 2), true,
                null, null, null, null, null);
        when(perfilService.consultarPerfil("ana@esibuy.com")).thenReturn(perfil);

        mockMvc.perform(get("/usuarios/perfil")
                        .param("email", "otro@esibuy.com")
                        .param("id", "usuario-de-otro")
                        .with(user("ana@esibuy.com").roles("CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@esibuy.com"))
                .andExpect(jsonPath("$.dniNif").value("12*******"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.contrasenaHash").doesNotExist())
                .andExpect(jsonPath("$.historialContrasenasHash").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("otro@esibuy.com"))));

        verify(perfilService).consultarPerfil("ana@esibuy.com");
    }
}
