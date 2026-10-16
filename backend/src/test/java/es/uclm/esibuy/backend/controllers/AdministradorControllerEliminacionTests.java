package es.uclm.esibuy.backend.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;

import es.uclm.esibuy.backend.dto.ApiErrorResponse;
import es.uclm.esibuy.backend.exceptions.AutoBloqueoAdministradorException;
import es.uclm.esibuy.backend.exceptions.AutoEliminacionAdministradorException;
import es.uclm.esibuy.backend.exceptions.UltimoAdministradorException;
import es.uclm.esibuy.backend.services.AdministradorService;

@ExtendWith(MockitoExtension.class)
class AdministradorControllerEliminacionTests {

    private static final String ID = "admin-id";
    private static final String EMAIL_AUTENTICADO = "admin.solicitante@esibuy.es";

    @Mock
    private AdministradorService administradorService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private AdministradorController controller;

    @Test
    void eliminarAdministrador_siTodoVaBien_devuelve204YDelegaConElEmailAutenticado() {
        when(authentication.getName()).thenReturn(EMAIL_AUTENTICADO);

        ResponseEntity<Void> respuesta =
                controller.eliminarAdministrador(ID, authentication);

        assertEquals(204, respuesta.getStatusCode().value());

        verify(administradorService)
                .eliminarAdministrador(ID, EMAIL_AUTENTICADO);
    }

    @Test
    void eliminarAdministrador_estaRestringidoARolAdminYMetodoDelete()
            throws NoSuchMethodException {

        Method metodo = AdministradorController.class
                .getMethod(
                        "eliminarAdministrador",
                        String.class,
                        Authentication.class
                );

        PreAuthorize preAuthorize =
                metodo.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize);
        assertEquals(
                "hasRole('ADMIN')",
                preAuthorize.value()
        );

        assertNotNull(
                metodo.getAnnotation(DeleteMapping.class)
        );
    }

    @Test
    void manejarReglaAdministrativa_autoEliminacion_devuelve409ConMensaje() {

        ResponseEntity<ApiErrorResponse> respuesta =
                controller.manejarReglaAdministrativa(
                        new AutoEliminacionAdministradorException()
                );

        assertEquals(
                409,
                respuesta.getStatusCode().value()
        );

        assertNotNull(
                respuesta.getBody()
        );
    }

    @Test
    void manejarReglaAdministrativa_ultimoAdministrador_devuelve409ConMensaje() {

        ResponseEntity<ApiErrorResponse> respuesta =
                controller.manejarReglaAdministrativa(
                        new UltimoAdministradorException()
                );

        assertEquals(
                409,
                respuesta.getStatusCode().value()
        );

        assertNotNull(
                respuesta.getBody()
        );
    }

    @Test
    void manejarReglaAdministrativa_autoBloqueo_devuelve409ConMensaje() {

        ResponseEntity<ApiErrorResponse> respuesta =
                controller.manejarReglaAdministrativa(
                        new AutoBloqueoAdministradorException()
                );

        assertEquals(
                409,
                respuesta.getStatusCode().value()
        );

        assertNotNull(
                respuesta.getBody()
        );
    }
}