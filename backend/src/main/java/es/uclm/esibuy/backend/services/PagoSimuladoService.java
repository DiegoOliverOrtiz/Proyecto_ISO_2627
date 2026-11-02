package es.uclm.esibuy.backend.services;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Comparator;

import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;

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

@Service
public class PagoSimuladoService {

	private static final String PREFIJO_TOKEN = "tok_sim_";

	private final ClienteDAO clienteDAO;
	private final TipoMetodoPagoDAO tipoMetodoPagoDAO;
	private final MetodoPagoDAO metodoPagoDAO;
	private final ValidadorMetodoPago validador;

	public PagoSimuladoService(
			ClienteDAO clienteDAO,
			TipoMetodoPagoDAO tipoMetodoPagoDAO,
			MetodoPagoDAO metodoPagoDAO,
			ValidadorMetodoPago validador) {
		this.clienteDAO = clienteDAO;
		this.tipoMetodoPagoDAO = tipoMetodoPagoDAO;
		this.metodoPagoDAO = metodoPagoDAO;
		this.validador = validador;
	}

	public List<TipoMetodoPagoResponse> consultarTiposDisponibles() {
		return tipoMetodoPagoDAO.findByActivoTrueOrderByNombreAsc().stream()
				.map(tipo -> new TipoMetodoPagoResponse(tipo.getCodigo(), tipo.getNombre()))
				.toList();
	}

	public List<MetodoPagoResponse> consultarMetodos(String emailAutenticado) {
		Cliente cliente = obtenerCliente(emailAutenticado);
		if (!cliente.isActivada() || cliente.isBloqueada()) {
			throw new AccessDeniedException("Solo un cliente activo puede consultar sus métodos de pago.");
		}
		return metodoPagoDAO.findByClienteId(cliente.getId()).stream()
				.map(MetodoPagoResponse::de).toList();
	}

	public MetodoPagoResponse registrarMetodoPago(String emailAutenticado, MetodoPagoRequest request) {
		Cliente cliente = obtenerCliente(emailAutenticado);

		String codigo = request.tipo().trim().toUpperCase(Locale.ROOT);
		TipoMetodoPago tipo = tipoMetodoPagoDAO.findByCodigoAndActivoTrue(codigo)
				.orElseThrow(() -> new MetodoPagoInvalidoException(
						Map.of("tipo", List.of("El tipo de método de pago no está disponible."))));

		ValidadorMetodoPago.Resultado resultado = validador.validar(tipo.getCodigo(), request);
		if (!resultado.esValido()) {
			throw new MetodoPagoInvalidoException(resultado.errores());
		}

		String nombre = request.nombre().trim();
		comprobarNombreDisponible(cliente.getId(), nombre);

		MetodoPago metodo = new MetodoPago();
		metodo.setClienteId(cliente.getId());
		metodo.setTipo(tipo.getCodigo());
		metodo.setNombre(nombre);
		metodo.setToken(PREFIJO_TOKEN + UUID.randomUUID());
		metodo.setReferenciaEnmascarada(enmascarar(tipo.getCodigo(), resultado.datoPrincipal()));
		metodo.setTitular(resultado.titular());
		metodo.setCaducidad(resultado.caducidad());
		metodo.setFechaAlta(Instant.now());

		return MetodoPagoResponse.de(metodoPagoDAO.save(metodo));
	}

	private Cliente obtenerCliente(String emailAutenticado) {
		if (emailAutenticado == null || emailAutenticado.isBlank()) {
			throw new UsuarioNoEncontradoException();
		}
		return clienteDAO.findByEmail(emailAutenticado.trim().toLowerCase(Locale.ROOT))
				.orElseThrow(UsuarioNoEncontradoException::new);
	}

	private void comprobarNombreDisponible(String clienteId, String nombre) {
		boolean repetido = metodoPagoDAO.findByClienteId(clienteId).stream()
				.anyMatch(metodo -> metodo.getNombre().equalsIgnoreCase(nombre));
		if (repetido) {
			throw new MetodoPagoDuplicadoException();
		}
	}

	private String enmascarar(String codigoTipo, String dato) {
		return switch (codigoTipo) {
			case TipoMetodoPago.CODIGO_TARJETA -> "**** **** **** " + dato.substring(dato.length() - 4);
			case TipoMetodoPago.CODIGO_PAYPAL -> dato.charAt(0) + "***" + dato.substring(dato.indexOf('@'));
			case TipoMetodoPago.CODIGO_BIZUM -> "******" + dato.substring(dato.length() - 3);
			default -> throw new IllegalStateException("Tipo de método de pago no soportado.");
		};
	}

	public List<MetodoPagoResponse> consultarMetodosPago(String emailAutenticado) {
		Cliente cliente = obtenerCliente(emailAutenticado);
		return metodoPagoDAO.findByClienteId(cliente.getId()).stream()
				.sorted(Comparator.comparing(MetodoPago::getNombre, String.CASE_INSENSITIVE_ORDER))
				.map(MetodoPagoResponse::de)
				.toList();
	}
}
