package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import es.uclm.esibuy.backend.dao.AdministradorDAO;
import es.uclm.esibuy.backend.exceptions.UsuarioNoEncontradoException;
import es.uclm.esibuy.backend.model.Administrador;
import es.uclm.esibuy.backend.exceptions.AutoEliminacionAdministradorException;
import es.uclm.esibuy.backend.exceptions.UltimoAdministradorException;


@ExtendWith(MockitoExtension.class)
class AdministradorServiceEliminacionTests {

	private static final String ID_OBJETIVO = "admin-objetivo-id";
	private static final String EMAIL_OBJETIVO = "admin.objetivo@esibuy.es";
	private static final String EMAIL_SOLICITANTE = "admin.solicitante@esibuy.es";

	@Mock
	private AdministradorDAO administradorDAO;

	@InjectMocks
	private AdministradorService administradorService;

	private Administrador crearAdministrador(String id, String email) {
		Administrador administrador = new Administrador();
		administrador.setId(id);
		administrador.setEmail(email);
		return administrador;
	}

    /* Test sustituido por uno más adelante
	@Test
	void eliminarAdministrador_conOtroAdministradorExistente_loElimina() {
		Administrador objetivo = crearAdministrador(ID_OBJETIVO, EMAIL_OBJETIVO);
		when(administradorDAO.findById(ID_OBJETIVO)).thenReturn(Optional.of(objetivo));

		administradorService.eliminarAdministrador(ID_OBJETIVO, EMAIL_SOLICITANTE);

		verify(administradorDAO).delete(objetivo);
	}
    */

	@Test
	void eliminarAdministrador_siNoExiste_lanzaUsuarioNoEncontradoYNoElimina() {
		when(administradorDAO.findById(ID_OBJETIVO)).thenReturn(Optional.empty());

		assertThrows(UsuarioNoEncontradoException.class,
				() -> administradorService.eliminarAdministrador(ID_OBJETIVO, EMAIL_SOLICITANTE));

		verify(administradorDAO, never()).delete(any(Administrador.class));
	}

    @Test
    void eliminarAdministrador_siEsSuPropiaCuenta_lanzaAutoEliminacionYNoElimina() {
        Administrador propio = crearAdministrador(ID_OBJETIVO, EMAIL_SOLICITANTE);
        when(administradorDAO.findById(ID_OBJETIVO)).thenReturn(Optional.of(propio));

        assertThrows(AutoEliminacionAdministradorException.class,
                () -> administradorService.eliminarAdministrador(ID_OBJETIVO, EMAIL_SOLICITANTE));

        verify(administradorDAO, never()).delete(any(Administrador.class));
    }

    @Test
    void eliminarAdministrador_siEsSuPropiaCuentaConOtroFormatoDeEmail_tambienLoRechaza() {
        Administrador propio = crearAdministrador(ID_OBJETIVO, EMAIL_SOLICITANTE);
        when(administradorDAO.findById(ID_OBJETIVO)).thenReturn(Optional.of(propio));

        assertThrows(AutoEliminacionAdministradorException.class,
                () -> administradorService.eliminarAdministrador(
                        ID_OBJETIVO, "  " + EMAIL_SOLICITANTE.toUpperCase() + " "));

        verify(administradorDAO, never()).delete(any(Administrador.class));
    }

    @Test
    void eliminarAdministrador_conOtroAdministradorYQuedandoAlMenosUno_loElimina() {
        Administrador objetivo = crearAdministrador(ID_OBJETIVO, EMAIL_OBJETIVO);
        when(administradorDAO.findById(ID_OBJETIVO)).thenReturn(Optional.of(objetivo));
        when(administradorDAO.contarAdministradores()).thenReturn(2L);

        administradorService.eliminarAdministrador(ID_OBJETIVO, EMAIL_SOLICITANTE);

        verify(administradorDAO).delete(objetivo);
    }

    @ParameterizedTest
    @ValueSource(longs = { 0L, 1L })
    void eliminarAdministrador_siSoloQuedaUnoOMenos_lanzaUltimoAdministradorYNoElimina(long total) {
        Administrador objetivo = crearAdministrador(ID_OBJETIVO, EMAIL_OBJETIVO);
        when(administradorDAO.findById(ID_OBJETIVO)).thenReturn(Optional.of(objetivo));
        when(administradorDAO.contarAdministradores()).thenReturn(total);

        assertThrows(UltimoAdministradorException.class,
                () -> administradorService.eliminarAdministrador(ID_OBJETIVO, EMAIL_SOLICITANTE));

        verify(administradorDAO, never()).delete(any(Administrador.class));
    }
}