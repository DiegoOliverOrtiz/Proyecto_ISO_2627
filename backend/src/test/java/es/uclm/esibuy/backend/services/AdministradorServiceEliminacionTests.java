package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.mockito.Mockito.verifyNoInteractions;
import org.springframework.security.access.AccessDeniedException;

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

    @Mock
    private SesionService sesionService;

	@InjectMocks
	private AdministradorService administradorService;

	private Administrador crearAdministrador(String id, String email) {
		Administrador administrador = new Administrador();
		administrador.setId(id);
		administrador.setEmail(email);
		return administrador;
	}

	@Test
	void eliminarAdministrador_siNoExiste_lanzaUsuarioNoEncontradoYNoElimina() {
		when(administradorDAO.findById(ID_OBJETIVO)).thenReturn(Optional.empty());

		assertThrows(UsuarioNoEncontradoException.class,
				() -> administradorService.eliminarAdministrador(ID_OBJETIVO, EMAIL_SOLICITANTE));

		verify(administradorDAO, never()).delete(any(Administrador.class));
        verifyNoInteractions(sesionService);
	}

    @Test
    void eliminarAdministrador_siEsSuPropiaCuenta_lanzaAutoEliminacionYNoElimina() {
        Administrador propio = crearAdministrador(ID_OBJETIVO, EMAIL_SOLICITANTE);
        when(administradorDAO.findById(ID_OBJETIVO)).thenReturn(Optional.of(propio));

        assertThrows(AutoEliminacionAdministradorException.class,
                () -> administradorService.eliminarAdministrador(ID_OBJETIVO, EMAIL_SOLICITANTE));

        verify(administradorDAO, never()).delete(any(Administrador.class));
        verifyNoInteractions(sesionService); 
    }

    @Test
    void eliminarAdministrador_siEsSuPropiaCuentaConOtroFormatoDeEmail_tambienLoRechaza() {
        Administrador propio = crearAdministrador(ID_OBJETIVO, EMAIL_SOLICITANTE);
        when(administradorDAO.findById(ID_OBJETIVO)).thenReturn(Optional.of(propio));

        assertThrows(AutoEliminacionAdministradorException.class,
                () -> administradorService.eliminarAdministrador(
                        ID_OBJETIVO, "  " + EMAIL_SOLICITANTE.toUpperCase() + " "));

        verify(administradorDAO, never()).delete(any(Administrador.class));
        verifyNoInteractions(sesionService);
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
        verifyNoInteractions(sesionService);
    }

    @Test
    void eliminarAdministrador_conIdNuloOEnBlanco_lanzaUsuarioNoEncontradoSinConsultarLaBaseDeDatos() {
        assertThrows(UsuarioNoEncontradoException.class,
                () -> administradorService.eliminarAdministrador(null, EMAIL_SOLICITANTE));
        assertThrows(UsuarioNoEncontradoException.class,
                () -> administradorService.eliminarAdministrador("   ", EMAIL_SOLICITANTE));

        verifyNoInteractions(administradorDAO);
    }

    @Test
    void eliminarAdministrador_sinSolicitanteIdentificado_deniegaElAccesoSinConsultarLaBaseDeDatos() {
        assertThrows(AccessDeniedException.class,
                () -> administradorService.eliminarAdministrador(ID_OBJETIVO, null));
        assertThrows(AccessDeniedException.class,
                () -> administradorService.eliminarAdministrador(ID_OBJETIVO, " "));

        verifyNoInteractions(administradorDAO);
    }

    @Test
    void eliminarAdministrador_alEliminarlo_cierraSusSesionesDespuesDeBorrarlo() {
        Administrador objetivo = crearAdministrador(ID_OBJETIVO, EMAIL_OBJETIVO);
        when(administradorDAO.findById(ID_OBJETIVO)).thenReturn(Optional.of(objetivo));
        when(administradorDAO.contarAdministradores()).thenReturn(2L);

        administradorService.eliminarAdministrador(ID_OBJETIVO, EMAIL_SOLICITANTE);

        InOrder orden = inOrder(administradorDAO, sesionService);
        orden.verify(administradorDAO).delete(objetivo);
        orden.verify(sesionService).cerrarSesiones(EMAIL_OBJETIVO);
    }

    @Test
    void eliminarAdministrador_siFallaElBorrado_noCierraSesiones() {
        Administrador objetivo = crearAdministrador(ID_OBJETIVO, EMAIL_OBJETIVO);
        when(administradorDAO.findById(ID_OBJETIVO)).thenReturn(Optional.of(objetivo));
        when(administradorDAO.contarAdministradores()).thenReturn(2L);
        doThrow(new IllegalStateException("fallo de base de datos"))
                .when(administradorDAO).delete(objetivo);

        assertThrows(IllegalStateException.class,
                () -> administradorService.eliminarAdministrador(ID_OBJETIVO, EMAIL_SOLICITANTE));

        verifyNoInteractions(sesionService);
    }
}