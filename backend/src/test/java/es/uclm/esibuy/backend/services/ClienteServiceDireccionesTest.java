package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import es.uclm.esibuy.backend.dao.ClienteDAO;
import es.uclm.esibuy.backend.dto.DireccionRequest;
import es.uclm.esibuy.backend.dto.DireccionResponse;
import es.uclm.esibuy.backend.exceptions.DireccionDuplicadaException;
import es.uclm.esibuy.backend.exceptions.DireccionNoEncontradaException;
import es.uclm.esibuy.backend.exceptions.UsuarioNoEncontradoException;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.Direccion;

@ExtendWith(MockitoExtension.class)
class ClienteServiceDireccionesTests {

	private static final String EMAIL = "cliente@example.com";
	private static final String EMAIL_OTRO = "otro@example.com";
	private static final String ID_CASA = "dir-casa";
	private static final String ID_TRABAJO = "dir-trabajo";

	@Mock
	private ClienteDAO clienteDAO;

	@Mock
	private SoporteRegistro soporteRegistro;

	private ClienteService clienteService;

	@BeforeEach
	void setUp() {
		clienteService = new ClienteService(clienteDAO, soporteRegistro);
	}

	private Direccion crearDireccion(String id, String nombre) {
		Direccion direccion = new Direccion();
		direccion.setId(id);
		direccion.setNombre(nombre);
		direccion.setCalle("Calle Mayor");
		direccion.setNumero("1");
		direccion.setPortal("A");
		direccion.setPiso("2A");
		direccion.setCodigoPostal("13001");
		direccion.setCiudad("Ciudad Real");
		direccion.setProvincia("Ciudad Real");
		return direccion;
	}

	private Cliente crearCliente(String email) {
		Cliente cliente = new Cliente();
		cliente.setEmail(email);
		return cliente;
	}

	private Cliente clienteConDirecciones() {
		Cliente cliente = crearCliente(EMAIL);
		cliente.agregarDireccion(crearDireccion(ID_CASA, "Mi casa"));
		cliente.agregarDireccion(crearDireccion(ID_TRABAJO, "Trabajo"));
		return cliente;
	}

	private DireccionRequest peticion(String nombre) {
		return new DireccionRequest(nombre, "Calle Toledo", "5", null, null, "13002", "Almagro", "Ciudad Real");
	}

	@Test
	void agregarDireccion_guardaLaDireccionConSuNombreIdentificativo() {
		Cliente cliente = crearCliente(EMAIL);
		when(clienteDAO.findByEmail(EMAIL)).thenReturn(Optional.of(cliente));

		DireccionResponse respuesta = clienteService.agregarDireccion(EMAIL, peticion("  Mi casa "));

		assertEquals("Mi casa", respuesta.nombre());
		assertFalse(respuesta.id().isBlank());
		assertEquals(1, cliente.getDirecciones().size());
		assertEquals(respuesta.id(), cliente.getDirecciones().get(0).getId());
		verify(clienteDAO).save(cliente);
	}

	@Test
	void agregarDireccion_conPortalYPiso_losGuardaSinEspaciosSobrantes() {
		Cliente cliente = crearCliente(EMAIL);
		when(clienteDAO.findByEmail(EMAIL)).thenReturn(Optional.of(cliente));
		DireccionRequest request = new DireccionRequest(
				"Mi casa", " Calle Mayor ", " 12 ", " B ", " 3C ", "13001", "Ciudad Real", "Ciudad Real");

		DireccionResponse respuesta = clienteService.agregarDireccion(EMAIL, request);

		assertEquals("Calle Mayor", respuesta.calle());
		assertEquals("12", respuesta.numero());
		assertEquals("B", respuesta.portal());
		assertEquals("3C", respuesta.piso());
	}

	@Test
	void agregarDireccion_sinPortalNiPiso_losGuardaVacios() {
		Cliente cliente = crearCliente(EMAIL);
		when(clienteDAO.findByEmail(EMAIL)).thenReturn(Optional.of(cliente));
		DireccionRequest request = new DireccionRequest(
				"Mi casa", "Calle Mayor", "1", "  ", "", "13001", "Ciudad Real", "Ciudad Real");

		DireccionResponse respuesta = clienteService.agregarDireccion(EMAIL, request);

		assertNull(respuesta.portal());
		assertNull(respuesta.piso());
	}

	@Test
	void agregarDireccion_permiteVariasDireccionesConNombresDistintos() {
		Cliente cliente = crearCliente(EMAIL);
		when(clienteDAO.findByEmail(EMAIL)).thenReturn(Optional.of(cliente));

		DireccionResponse casa = clienteService.agregarDireccion(EMAIL, peticion("Mi casa"));
		DireccionResponse trabajo = clienteService.agregarDireccion(EMAIL, peticion("Trabajo"));

		assertEquals(2, cliente.getDirecciones().size());
		assertFalse(casa.id().equals(trabajo.id()));
	}

	@Test
	void agregarDireccion_conNombreRepetido_lanzaDuplicadaYNoGuarda() {
		Cliente cliente = clienteConDirecciones();
		when(clienteDAO.findByEmail(EMAIL)).thenReturn(Optional.of(cliente));

		assertThrows(DireccionDuplicadaException.class,
				() -> clienteService.agregarDireccion(EMAIL, peticion(" MI CASA ")));

		assertEquals(2, cliente.getDirecciones().size());
		verify(clienteDAO, never()).save(any(Cliente.class));
	}

	@Test
	void consultarDirecciones_devuelveLasDelClienteAutenticado() {
		when(clienteDAO.findByEmail(EMAIL)).thenReturn(Optional.of(clienteConDirecciones()));

		List<DireccionResponse> respuesta = clienteService.consultarDirecciones(EMAIL);

		assertEquals(List.of("Mi casa", "Trabajo"),
				respuesta.stream().map(DireccionResponse::nombre).toList());
	}

	@Test
	void consultarDirecciones_sinDirecciones_devuelveListaVacia() {
		when(clienteDAO.findByEmail(EMAIL)).thenReturn(Optional.of(crearCliente(EMAIL)));

		assertTrue(clienteService.consultarDirecciones(EMAIL).isEmpty());
	}

	@Test
	void modificarDireccion_actualizaLosDatosYGuarda() {
		Cliente cliente = clienteConDirecciones();
		when(clienteDAO.findByEmail(EMAIL)).thenReturn(Optional.of(cliente));
		DireccionRequest cambios = new DireccionRequest(
				"Casa nueva", "Calle Toledo", "5", null, null, "13002", "Almagro", "Ciudad Real");

		DireccionResponse respuesta = clienteService.modificarDireccion(EMAIL, ID_CASA, cambios);

		assertEquals(ID_CASA, respuesta.id());
		Direccion guardada = cliente.buscarDireccion(ID_CASA).orElseThrow();
		assertEquals("Casa nueva", guardada.getNombre());
		assertEquals("Calle Toledo", guardada.getCalle());
		assertEquals("5", guardada.getNumero());
		assertNull(guardada.getPortal());
		assertNull(guardada.getPiso());
		assertEquals("13002", guardada.getCodigoPostal());
		assertEquals("Almagro", guardada.getCiudad());
		verify(clienteDAO).save(cliente);
	}

	@Test
	void modificarDireccion_conElMismoNombreDeLaPropiaDireccion_noEsDuplicado() {
		Cliente cliente = clienteConDirecciones();
		when(clienteDAO.findByEmail(EMAIL)).thenReturn(Optional.of(cliente));

		DireccionResponse respuesta = clienteService.modificarDireccion(EMAIL, ID_CASA, peticion("mi casa"));

		assertEquals("mi casa", respuesta.nombre());
		verify(clienteDAO).save(cliente);
	}

	@Test
	void modificarDireccion_conElNombreDeOtraDireccionPropia_lanzaDuplicadaYNoGuarda() {
		Cliente cliente = clienteConDirecciones();
		when(clienteDAO.findByEmail(EMAIL)).thenReturn(Optional.of(cliente));

		assertThrows(DireccionDuplicadaException.class,
				() -> clienteService.modificarDireccion(EMAIL, ID_CASA, peticion("Trabajo")));

		assertEquals("Mi casa", cliente.buscarDireccion(ID_CASA).orElseThrow().getNombre());
		verify(clienteDAO, never()).save(any(Cliente.class));
	}

	@Test
	void modificarDireccion_deOtroCliente_lanzaNoEncontradaYNoGuarda() {
		Cliente otro = crearCliente(EMAIL_OTRO);
		otro.agregarDireccion(crearDireccion("dir-ajena", "Su casa"));
		when(clienteDAO.findByEmail(EMAIL_OTRO)).thenReturn(Optional.of(otro));

		assertThrows(DireccionNoEncontradaException.class,
				() -> clienteService.modificarDireccion(EMAIL_OTRO, ID_CASA, peticion("Mía ahora")));

		verify(clienteDAO, never()).save(any(Cliente.class));
	}

	@Test
	void eliminarDireccion_laQuitaDelClienteYGuarda() {
		Cliente cliente = clienteConDirecciones();
		when(clienteDAO.findByEmail(EMAIL)).thenReturn(Optional.of(cliente));

		clienteService.eliminarDireccion(EMAIL, ID_CASA);

		assertTrue(cliente.buscarDireccion(ID_CASA).isEmpty());
		assertEquals(1, cliente.getDirecciones().size());
		verify(clienteDAO).save(cliente);
	}

	@Test
	void eliminarDireccion_deOtroCliente_lanzaNoEncontradaYNoGuarda() {
		Cliente otro = crearCliente(EMAIL_OTRO);
		otro.agregarDireccion(crearDireccion("dir-ajena", "Su casa"));
		when(clienteDAO.findByEmail(EMAIL_OTRO)).thenReturn(Optional.of(otro));

		assertThrows(DireccionNoEncontradaException.class,
				() -> clienteService.eliminarDireccion(EMAIL_OTRO, ID_CASA));

		assertEquals(1, otro.getDirecciones().size());
		verify(clienteDAO, never()).save(any(Cliente.class));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = "   ")
	void modificarYEliminar_conIdNuloOEnBlanco_lanzanNoEncontrada(String id) {
		when(clienteDAO.findByEmail(EMAIL)).thenReturn(Optional.of(clienteConDirecciones()));

		assertThrows(DireccionNoEncontradaException.class,
				() -> clienteService.modificarDireccion(EMAIL, id, peticion("X")));
		assertThrows(DireccionNoEncontradaException.class,
				() -> clienteService.eliminarDireccion(EMAIL, id));

		verify(clienteDAO, never()).save(any(Cliente.class));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = "   ")
	void operaciones_sinSolicitanteIdentificado_lanzanUsuarioNoEncontrado(String email) {
		assertThrows(UsuarioNoEncontradoException.class, () -> clienteService.consultarDirecciones(email));
		assertThrows(UsuarioNoEncontradoException.class,
				() -> clienteService.agregarDireccion(email, peticion("Mi casa")));
		assertThrows(UsuarioNoEncontradoException.class,
				() -> clienteService.modificarDireccion(email, ID_CASA, peticion("Mi casa")));
		assertThrows(UsuarioNoEncontradoException.class,
				() -> clienteService.eliminarDireccion(email, ID_CASA));

		verifyNoInteractions(clienteDAO);
	}

	@Test
	void consultarDirecciones_siElClienteNoExiste_lanzaUsuarioNoEncontrado() {
		when(clienteDAO.findByEmail(EMAIL)).thenReturn(Optional.empty());

		assertThrows(UsuarioNoEncontradoException.class, () -> clienteService.consultarDirecciones(EMAIL));
	}
}