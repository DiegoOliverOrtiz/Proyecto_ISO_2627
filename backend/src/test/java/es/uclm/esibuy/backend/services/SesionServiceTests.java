package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistryImpl;

class SesionServiceTests {

	private static final String EMAIL = "admin@esibuy.es";
	private static final String OTRO_EMAIL = "otro@esibuy.es";

	private SessionRegistryImpl sessionRegistry;
	private SesionService sesionService;

	@BeforeEach
	void setUp() {
		sessionRegistry = new SessionRegistryImpl();
		sesionService = new SesionService(sessionRegistry);
	}

	@Test
	void registrarSesion_asociaLaSesionAlEmailNormalizado() {
		sesionService.registrarSesion("s1", "  Admin@Esibuy.es ");

		SessionInformation info = sessionRegistry.getSessionInformation("s1");
		assertNotNull(info);
		assertEquals(EMAIL, info.getPrincipal());
		assertFalse(info.isExpired());
	}

	@Test
	void cerrarSesiones_expiraTodasLasSesionesDelUsuarioYSoloLasSuyas() {
		sesionService.registrarSesion("s1", EMAIL);
		sesionService.registrarSesion("s2", EMAIL);
		sesionService.registrarSesion("s3", OTRO_EMAIL);

		sesionService.cerrarSesiones(EMAIL);

		assertTrue(sessionRegistry.getSessionInformation("s1").isExpired());
		assertTrue(sessionRegistry.getSessionInformation("s2").isExpired());
		assertFalse(sessionRegistry.getSessionInformation("s3").isExpired());
	}

	@Test
	void cerrarSesiones_conOtroFormatoDeEmail_tambienExpiraLasSesiones() {
		sesionService.registrarSesion("s1", EMAIL);

		sesionService.cerrarSesiones("  ADMIN@ESIBUY.ES ");

		assertTrue(sessionRegistry.getSessionInformation("s1").isExpired());
	}

	@Test
	void cerrarSesiones_sinSesionesONuloOEnBlanco_noLanzaExcepcion() {
		assertDoesNotThrow(() -> sesionService.cerrarSesiones(EMAIL));
		assertDoesNotThrow(() -> sesionService.cerrarSesiones(null));
		assertDoesNotThrow(() -> sesionService.cerrarSesiones("  "));
	}
}