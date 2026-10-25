package es.uclm.esibuy.backend.controllers;

import es.uclm.esibuy.backend.dto.PerfilResponse;
import es.uclm.esibuy.backend.services.PerfilService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerfilControllerTests {

    @Mock
    private PerfilService perfilService;

    @Mock
    private Authentication authentication;

    @Test
    void consultaElPerfilUsandoElEmailDelUsuarioAutenticado() {
        PerfilController controller = new PerfilController(perfilService);
        PerfilResponse esperado = new PerfilResponse(
                "CLIENTE", "Ana", "García", "ana@esibuy.com", null,
                null, "12*******", null, true, null, null, null, null, null, null);
        when(authentication.getName()).thenReturn("ana@esibuy.com");
        when(perfilService.consultarPerfil("ana@esibuy.com")).thenReturn(esperado);

        ResponseEntity<PerfilResponse> respuesta = controller.consultarPerfil(authentication);

        assertEquals(200, respuesta.getStatusCode().value());
        assertEquals(esperado, respuesta.getBody());
        verify(perfilService).consultarPerfil("ana@esibuy.com");
    }
}
