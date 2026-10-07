package es.uclm.esibuy.backend.controllers;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;

import es.uclm.esibuy.backend.dto.LoginRequest;
import es.uclm.esibuy.backend.model.Administrador;
import es.uclm.esibuy.backend.services.AuthService;
import es.uclm.esibuy.backend.services.SesionService;

@ExtendWith(MockitoExtension.class)
class AuthControllerSesionTests {

	private static final String EMAIL = "admin@esibuy.es";
	private static final String PASSWORD = "Clave-Segura-1";

	@Mock
	private AuthService authService;
	@Mock
	private SecurityContextRepository securityContextRepository;
	@Mock
	private SesionService sesionService;

	@InjectMocks
	private AuthController controller;

	@AfterEach
	void limpiarContexto() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void iniciarSesion_registraLaSesionConElEmailDelUsuario() {
		Administrador administrador = new Administrador();
		administrador.setEmail(EMAIL);
		when(authService.autenticar(EMAIL, PASSWORD)).thenReturn(administrador);
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		controller.iniciarSesion(new LoginRequest(EMAIL, PASSWORD), request, response);

		verify(sesionService).registrarSesion(request.getSession(false).getId(), EMAIL);
	}
}