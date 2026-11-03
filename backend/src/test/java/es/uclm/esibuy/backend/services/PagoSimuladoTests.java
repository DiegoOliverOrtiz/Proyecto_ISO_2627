package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import es.uclm.esibuy.backend.dao.ClienteDAO;
import es.uclm.esibuy.backend.dao.MetodoPagoDAO;
import es.uclm.esibuy.backend.dao.TipoMetodoPagoDAO;
import es.uclm.esibuy.backend.dto.MetodoPagoRequest;
import es.uclm.esibuy.backend.dto.MetodoPagoResponse;
import es.uclm.esibuy.backend.dto.TipoMetodoPagoResponse;
import es.uclm.esibuy.backend.exceptions.MetodoPagoDuplicadoException;
import es.uclm.esibuy.backend.exceptions.MetodoPagoInvalidoException;
import es.uclm.esibuy.backend.exceptions.UsuarioNoEncontradoException;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.MetodoPago;
import es.uclm.esibuy.backend.model.TipoMetodoPago;
import es.uclm.esibuy.backend.exceptions.MetodoPagoNoEncontradoException;

@ExtendWith(MockitoExtension.class)
class PagoSimuladoServiceTests {

	private static final String EMAIL = "cliente@example.com";
	private static final String ID_CLIENTE = "cliente-1";
	private static final String NUMERO_TARJETA = "4242424242424242";

	@Mock
	private ClienteDAO clienteDAO;

	@Mock
	private TipoMetodoPagoDAO tipoMetodoPagoDAO;

	@Mock
	private MetodoPagoDAO metodoPagoDAO;

	private PagoSimuladoService servicio;

	@BeforeEach
	void setUp() {
		servicio = new PagoSimuladoService(clienteDAO, tipoMetodoPagoDAO, metodoPagoDAO, new ValidadorMetodoPago());
	}

	private Cliente crearCliente() {
		Cliente cliente = new Cliente();
		cliente.setId(ID_CLIENTE);
		cliente.setEmail(EMAIL);
		return cliente;
	}

	private TipoMetodoPago crearTipo(String codigo, String nombre) {
		TipoMetodoPago tipo = new TipoMetodoPago();
		tipo.setCodigo(codigo);
		tipo.setNombre(nombre);
		return tipo;
	}

	private void prepararCliente() {
		when(clienteDAO.findByEmail(EMAIL)).thenReturn(Optional.of(crearCliente()));
	}

	private void prepararTipo(String codigo, String nombre) {
		when(tipoMetodoPagoDAO.findByCodigoAndActivoTrue(codigo)).thenReturn(Optional.of(crearTipo(codigo, nombre)));
	}

	private void prepararGuardadoConId() {
		when(metodoPagoDAO.save(any(MetodoPago.class))).thenAnswer(invocacion -> {
			MetodoPago metodo = invocacion.getArgument(0);
			metodo.setId("mp-1");
			return metodo;
		});
	}

	private String caducidadFutura() {
		YearMonth futura = YearMonth.now().plusYears(2);
		return String.format(Locale.ROOT, "%02d/%02d", futura.getMonthValue(), futura.getYear() % 100);
	}

	private MetodoPagoRequest tarjeta(String nombre, String numero) {
		return new MetodoPagoRequest("TARJETA", nombre, numero, "Ana Pérez", caducidadFutura(), "123", null, null);
	}

	private MetodoPago guardado() {
		ArgumentCaptor<MetodoPago> captor = ArgumentCaptor.forClass(MetodoPago.class);
		verify(metodoPagoDAO).save(captor.capture());
		return captor.getValue();
	}

	private List<String> textosGuardados(MetodoPago metodo) {
		return Arrays.asList(
				metodo.getClienteId(), metodo.getTipo(), metodo.getNombre(), metodo.getToken(),
				metodo.getReferenciaEnmascarada(), metodo.getTitular(), metodo.getCaducidad());
	}

	@Test
	void registrarTarjeta_guardaSoloUnTokenYLaReferenciaEnmascarada() {
		prepararCliente();
		prepararTipo("TARJETA", "Tarjeta");
		prepararGuardadoConId();

		MetodoPagoResponse respuesta = servicio.registrarMetodoPago(EMAIL, tarjeta("Visa personal", "4242 4242 4242 4242"));

		MetodoPago metodo = guardado();
		assertEquals(ID_CLIENTE, metodo.getClienteId());
		assertEquals("TARJETA", metodo.getTipo());
		assertEquals("Visa personal", metodo.getNombre());
		assertTrue(metodo.getToken().startsWith("tok_sim_"));
		assertEquals("**** **** **** 4242", metodo.getReferenciaEnmascarada());
		assertEquals("Ana Pérez", metodo.getTitular());
		assertEquals(caducidadFutura(), metodo.getCaducidad());
		assertTrue(textosGuardados(metodo).stream().noneMatch(texto -> texto.contains(NUMERO_TARJETA)));

		assertEquals("mp-1", respuesta.id());
		assertEquals("**** **** **** 4242", respuesta.datosEnmascarados());
		assertFalse(respuesta.toString().contains(NUMERO_TARJETA));
		assertFalse(respuesta.toString().contains("tok_sim_"));
	}

	@Test
	void laEntidadNoTieneCamposParaDatosSensiblesEnClaro() {
		List<String> nombres = Arrays.stream(MetodoPago.class.getDeclaredFields())
				.map(campo -> campo.getName().toLowerCase(Locale.ROOT))
				.toList();

		assertTrue(nombres.stream().noneMatch(nombre ->
				nombre.contains("cvv") || nombre.contains("numerotarjeta")
						|| nombre.contains("correo") || nombre.contains("telefono")));
	}

	@Test
	void registrarPaypal_enmascaraElCorreoYNoLoGuardaCompleto() {
		prepararCliente();
		prepararTipo("PAYPAL", "PayPal");
		prepararGuardadoConId();
		MetodoPagoRequest request = new MetodoPagoRequest(
				"PAYPAL", "Mi PayPal", null, null, null, null, "Juan@Gmail.com", null);

		MetodoPagoResponse respuesta = servicio.registrarMetodoPago(EMAIL, request);

		assertEquals("j***@gmail.com", respuesta.datosEnmascarados());
		MetodoPago metodo = guardado();
		assertTrue(textosGuardados(metodo).stream().noneMatch(texto -> texto != null && texto.contains("juan@gmail.com")));
	}

	@Test
	void registrarBizum_enmascaraElTelefonoYNoLoGuardaCompleto() {
		prepararCliente();
		prepararTipo("BIZUM", "Bizum");
		prepararGuardadoConId();
		MetodoPagoRequest request = new MetodoPagoRequest(
				"BIZUM", "Mi Bizum", null, null, null, null, null, "+34 612 345 789");

		MetodoPagoResponse respuesta = servicio.registrarMetodoPago(EMAIL, request);

		assertEquals("******789", respuesta.datosEnmascarados());
		MetodoPago metodo = guardado();
		assertTrue(textosGuardados(metodo).stream().noneMatch(texto -> texto != null && texto.contains("612345789")));
	}

	@Test
	void registrarMetodo_permiteVariosDelMismoTipoConNombresDistintos() {
		prepararCliente();
		prepararTipo("TARJETA", "Tarjeta");
		prepararGuardadoConId();
		MetodoPago existente = new MetodoPago();
		existente.setNombre("Visa personal");
		when(metodoPagoDAO.findByClienteId(ID_CLIENTE)).thenReturn(List.of(existente));

		MetodoPagoResponse respuesta = servicio.registrarMetodoPago(EMAIL, tarjeta("Visa trabajo", NUMERO_TARJETA));

		assertEquals("Visa trabajo", respuesta.nombre());
		verify(metodoPagoDAO).findByClienteId(ID_CLIENTE);
	}

	@Test
	void registrarMetodo_conNombreRepetido_lanzaDuplicadoYNoGuarda() {
		prepararCliente();
		prepararTipo("TARJETA", "Tarjeta");
		MetodoPago existente = new MetodoPago();
		existente.setNombre("Visa personal");
		when(metodoPagoDAO.findByClienteId(ID_CLIENTE)).thenReturn(List.of(existente));

		assertThrows(MetodoPagoDuplicadoException.class,
				() -> servicio.registrarMetodoPago(EMAIL, tarjeta(" VISA PERSONAL ", NUMERO_TARJETA)));

		verify(metodoPagoDAO, never()).save(any(MetodoPago.class));
	}

	@Test
	void registrarMetodo_conTipoInexistenteOInactivo_lanzaInvalidoConErrorEnTipo() {
		prepararCliente();
		when(tipoMetodoPagoDAO.findByCodigoAndActivoTrue("TRANSFERENCIA")).thenReturn(Optional.empty());
		MetodoPagoRequest request = new MetodoPagoRequest(
				"transferencia", "Mi banco", null, null, null, null, null, null);

		MetodoPagoInvalidoException excepcion = assertThrows(MetodoPagoInvalidoException.class,
				() -> servicio.registrarMetodoPago(EMAIL, request));

		assertTrue(excepcion.getErrores().containsKey("tipo"));
		verify(metodoPagoDAO, never()).save(any(MetodoPago.class));
	}

	@Test
	void registrarMetodo_conDatosInvalidos_lanzaInvalidoYNoGuarda() {
		prepararCliente();
		prepararTipo("TARJETA", "Tarjeta");

		MetodoPagoInvalidoException excepcion = assertThrows(MetodoPagoInvalidoException.class,
				() -> servicio.registrarMetodoPago(EMAIL, tarjeta("Visa", "1234 5678 9012 3456")));

		assertTrue(excepcion.getErrores().containsKey("numeroTarjeta"));
		verify(metodoPagoDAO, never()).save(any(MetodoPago.class));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = "   ")
	void registrarMetodo_sinSolicitanteIdentificado_lanzaUsuarioNoEncontrado(String email) {
		assertThrows(UsuarioNoEncontradoException.class,
				() -> servicio.registrarMetodoPago(email, tarjeta("Visa", NUMERO_TARJETA)));

		verifyNoInteractions(clienteDAO, tipoMetodoPagoDAO, metodoPagoDAO);
	}

	@Test
	void registrarMetodo_siElClienteNoExiste_lanzaUsuarioNoEncontrado() {
		when(clienteDAO.findByEmail(EMAIL)).thenReturn(Optional.empty());

		assertThrows(UsuarioNoEncontradoException.class,
				() -> servicio.registrarMetodoPago(EMAIL, tarjeta("Visa", NUMERO_TARJETA)));

		verifyNoInteractions(metodoPagoDAO);
	}

	@Test
	void consultarTiposDisponibles_devuelveLosTiposDeLaBaseDeDatos() {
		when(tipoMetodoPagoDAO.findByActivoTrueOrderByNombreAsc()).thenReturn(List.of(
				crearTipo("BIZUM", "Bizum"), crearTipo("PAYPAL", "PayPal"), crearTipo("TARJETA", "Tarjeta")));

		List<TipoMetodoPagoResponse> tipos = servicio.consultarTiposDisponibles();

		assertEquals(List.of("BIZUM", "PAYPAL", "TARJETA"),
				tipos.stream().map(TipoMetodoPagoResponse::codigo).toList());
		assertEquals("PayPal", tipos.get(1).nombre());
	}

		private MetodoPago crearMetodo(String nombre, String tipo, String referencia) {
		MetodoPago metodo = new MetodoPago();
		metodo.setId("id-" + nombre);
		metodo.setClienteId(ID_CLIENTE);
		metodo.setNombre(nombre);
		metodo.setTipo(tipo);
		metodo.setToken("tok_sim_secreto");
		metodo.setReferenciaEnmascarada(referencia);
		return metodo;
	}

	@Test
	void consultarMetodosPago_devuelveSoloLosDelClienteEnmascaradosYOrdenadosPorNombre() {
		prepararCliente();
		when(metodoPagoDAO.findByClienteId(ID_CLIENTE)).thenReturn(List.of(
				crearMetodo("visa trabajo", "TARJETA", "**** **** **** 4242"),
				crearMetodo("Bizum", "BIZUM", "******789")));

		List<MetodoPagoResponse> respuesta = servicio.consultarMetodosPago(EMAIL);

		assertEquals(List.of("Bizum", "visa trabajo"),
				respuesta.stream().map(MetodoPagoResponse::nombre).toList());
		assertEquals(List.of("BIZUM", "TARJETA"),
				respuesta.stream().map(MetodoPagoResponse::tipo).toList());
		assertEquals("******789", respuesta.get(0).datosEnmascarados());
		assertFalse(respuesta.toString().contains("tok_sim_secreto"));
		verify(metodoPagoDAO).findByClienteId(ID_CLIENTE);
	}

	@Test
	void consultarMetodosPago_sinMetodos_devuelveListaVacia() {
		prepararCliente();

		assertTrue(servicio.consultarMetodosPago(EMAIL).isEmpty());
	}

	@Test
	void consultarMetodosPago_usaSiempreElIdDelClienteAutenticado() {
		Cliente otro = new Cliente();
		otro.setId("cliente-2");
		otro.setEmail("otro@example.com");
		when(clienteDAO.findByEmail("otro@example.com")).thenReturn(Optional.of(otro));

		assertTrue(servicio.consultarMetodosPago("otro@example.com").isEmpty());

		verify(metodoPagoDAO).findByClienteId("cliente-2");
		verify(metodoPagoDAO, never()).findByClienteId(ID_CLIENTE);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = "   ")
	void consultarMetodosPago_sinSolicitanteIdentificado_lanzaUsuarioNoEncontrado(String email) {
		assertThrows(UsuarioNoEncontradoException.class, () -> servicio.consultarMetodosPago(email));

		verifyNoInteractions(clienteDAO, metodoPagoDAO);
	}

		@Test
	void eliminarMetodoPago_borraElMetodoDelClienteAutenticado() {
		prepararCliente();
		when(metodoPagoDAO.deleteByIdAndClienteId("mp-1", ID_CLIENTE)).thenReturn(1L);

		servicio.eliminarMetodoPago(EMAIL, "mp-1");

		verify(metodoPagoDAO).deleteByIdAndClienteId("mp-1", ID_CLIENTE);
	}

	@Test
	void eliminarMetodoPago_deOtroClienteOInexistente_lanzaNoEncontrado() {
		prepararCliente();
		when(metodoPagoDAO.deleteByIdAndClienteId("mp-ajeno", ID_CLIENTE)).thenReturn(0L);

		assertThrows(MetodoPagoNoEncontradoException.class,
				() -> servicio.eliminarMetodoPago(EMAIL, "mp-ajeno"));

		verify(metodoPagoDAO).deleteByIdAndClienteId("mp-ajeno", ID_CLIENTE);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = "   ")
	void eliminarMetodoPago_conIdNuloOEnBlanco_lanzaNoEncontradoSinBorrar(String id) {
		prepararCliente();

		assertThrows(MetodoPagoNoEncontradoException.class,
				() -> servicio.eliminarMetodoPago(EMAIL, id));

		verify(metodoPagoDAO, never()).deleteByIdAndClienteId(any(), any());
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = "   ")
	void eliminarMetodoPago_sinSolicitanteIdentificado_lanzaUsuarioNoEncontrado(String email) {
		assertThrows(UsuarioNoEncontradoException.class,
				() -> servicio.eliminarMetodoPago(email, "mp-1"));

		verifyNoInteractions(clienteDAO, metodoPagoDAO);
	}
}