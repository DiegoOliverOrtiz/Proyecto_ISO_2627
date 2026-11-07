package es.uclm.esibuy.backend.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
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
import es.uclm.esibuy.backend.dto.TwoFactorCodeRequest;
import es.uclm.esibuy.backend.model.Administrador;
import es.uclm.esibuy.backend.services.AuthService;
import es.uclm.esibuy.backend.services.SesionService;
import es.uclm.esibuy.backend.services.TotpService;

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
	@Mock
	private TotpService totpService;

	@InjectMocks
	private AuthController controller;

	@AfterEach
	void limpiarContexto() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void iniciarSesion_registraLaSesionSoloDespuesDeVerificarTotp() {
	    Administrador administrador = new Administrador();
	    administrador.setEmail(EMAIL);
	    administrador.setActivada(true);
	    administrador.setTotpActivado(true);
	    administrador.setSecretoTotpCifrado("secreto-cifrado-prueba");

	    when(authService.autenticar(
	            org.mockito.ArgumentMatchers.eq(EMAIL),
	            org.mockito.ArgumentMatchers.eq(PASSWORD),
	            org.mockito.ArgumentMatchers.anyString()))
	            .thenReturn(administrador);

	    MockHttpServletRequest request = new MockHttpServletRequest();
	    MockHttpServletResponse response = new MockHttpServletResponse();

	    var inicio = controller.iniciarSesion(
	            new LoginRequest(EMAIL, PASSWORD), request, response);

	    assertEquals(202, inicio.getStatusCode().value());
	    verifyNoInteractions(sesionService);

	    when(authService.buscarPorCorreo(EMAIL)).thenReturn(administrador);
	    when(totpService.verificarSegundoFactor(administrador, "123456"))
	            .thenReturn(true);

	    var confirmacion = controller.verificarSegundoFactor(
	            new TwoFactorCodeRequest("123456"), request, response);

	    assertEquals(200, confirmacion.getStatusCode().value());

	    verify(sesionService).registrarSesion(
	            request.getSession(false).getId(), EMAIL);
	}
}
