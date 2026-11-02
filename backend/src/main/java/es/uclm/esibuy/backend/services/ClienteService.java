package es.uclm.esibuy.backend.services;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import es.uclm.esibuy.backend.dao.ClienteDAO;
import es.uclm.esibuy.backend.dto.DireccionRequest;
import es.uclm.esibuy.backend.dto.DireccionResponse;
import es.uclm.esibuy.backend.dto.RegistroClienteRequest;
import es.uclm.esibuy.backend.dto.RegistroUsuarioResponse;
import es.uclm.esibuy.backend.exceptions.DireccionDuplicadaException;
import es.uclm.esibuy.backend.exceptions.DireccionNoEncontradaException;
import es.uclm.esibuy.backend.exceptions.InvalidRegistrationException;
import es.uclm.esibuy.backend.exceptions.UsuarioNoEncontradoException;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.Direccion;

@Service
public class ClienteService {

	private final ClienteDAO clienteDAO;
	private final SoporteRegistro soporteRegistro;
	private final FavoritosRegistroService favoritosRegistroService;
	private final BajaUsuarioService bajaUsuarioService;

	public ClienteService(
			ClienteDAO clienteDAO,
			SoporteRegistro soporteRegistro) {
		this(clienteDAO, soporteRegistro, null, null);
	}

	public ClienteService(ClienteDAO clienteDAO, SoporteRegistro soporteRegistro,
			FavoritosRegistroService favoritosRegistroService) {
		this(clienteDAO, soporteRegistro, favoritosRegistroService, null);
	}

	public ClienteService(
			ClienteDAO clienteDAO,
			SoporteRegistro soporteRegistro,
			BajaUsuarioService bajaUsuarioService) {
		this(clienteDAO, soporteRegistro, null, bajaUsuarioService);
	}

	@Autowired
	public ClienteService(
			ClienteDAO clienteDAO,
			SoporteRegistro soporteRegistro,
			FavoritosRegistroService favoritosRegistroService,
			BajaUsuarioService bajaUsuarioService) {
		this.clienteDAO = clienteDAO;
		this.soporteRegistro = soporteRegistro;
		this.favoritosRegistroService = favoritosRegistroService;
		this.bajaUsuarioService = bajaUsuarioService;
	}

	public void darDeBaja(String emailAutenticado) {
		bajaUsuarioService.darDeBajaCliente(emailAutenticado);
	}

	public RegistroUsuarioResponse registrar(RegistroClienteRequest request) {
		if (request == null) {
			throw new InvalidRegistrationException();
		}
		soporteRegistro.validarSolicitud(request, request.contrasena());
		String email = soporteRegistro.normalizarCorreo(request.email());
		soporteRegistro.comprobarCorreoDisponible(email);

		Cliente cliente = new Cliente();
		cliente.setNombre(request.nombre().trim());
		cliente.setApellidos(request.apellidos().trim());
		cliente.setEmail(email);
		cliente.setFechaNacimiento(request.fechaNacimiento());
		cliente.setDniNif(request.dniNif().trim());
		cliente.setTelefono(request.telefono());
		cliente.setImagenPerfil(request.imagenPerfil());
		cliente.setEsPremium(request.esPremium());
		cliente.setBloqueada(false);
		cliente.setActivada(false);
		cliente.registrarContrasenaHash(soporteRegistro.codificarContrasena(request.contrasena()));

		Cliente guardado = soporteRegistro.guardarRegistro(() -> clienteDAO.save(cliente));

		if (favoritosRegistroService != null) {
			favoritosRegistroService.crearListaPredeterminada(
				guardado.getId()
			);
		}

		return new RegistroUsuarioResponse(guardado.getId(), "cliente");
	}

	public Cliente cambiarTipoCliente(String emailAutenticado, Boolean esPremium) {
		if (emailAutenticado == null || emailAutenticado.isBlank()) {
			throw new UsuarioNoEncontradoException();
		}
		if (esPremium == null) {
			throw new IllegalArgumentException("El tipo de cliente es obligatorio.");
		}

		Cliente cliente = clienteDAO.findByEmail(emailAutenticado)
				.orElseThrow(UsuarioNoEncontradoException::new);
		cliente.setEsPremium(esPremium);
		return clienteDAO.save(cliente);
	}

	public List<DireccionResponse> consultarDirecciones(String emailAutenticado) {
		return obtenerCliente(emailAutenticado).getDirecciones().stream()
				.map(DireccionResponse::de)
				.toList();
	}

	public DireccionResponse agregarDireccion(String emailAutenticado, DireccionRequest request) {
		Cliente cliente = obtenerCliente(emailAutenticado);
		comprobarNombreDisponible(cliente, request.nombre().trim(), null);

		Direccion direccion = new Direccion();
		direccion.setId(UUID.randomUUID().toString());
		aplicarDatos(direccion, request);
		cliente.agregarDireccion(direccion);
		clienteDAO.save(cliente);
		return DireccionResponse.de(direccion);
	}

	public DireccionResponse modificarDireccion(
			String emailAutenticado,
			String idDireccion,
			DireccionRequest request) {
		Cliente cliente = obtenerCliente(emailAutenticado);
		Direccion direccion = obtenerDireccion(cliente, idDireccion);
		comprobarNombreDisponible(cliente, request.nombre().trim(), direccion.getId());

		aplicarDatos(direccion, request);
		clienteDAO.save(cliente);
		return DireccionResponse.de(direccion);
	}

	public void eliminarDireccion(String emailAutenticado, String idDireccion) {
		Cliente cliente = obtenerCliente(emailAutenticado);
		Direccion direccion = obtenerDireccion(cliente, idDireccion);

		cliente.eliminarDireccion(direccion.getId());
		clienteDAO.save(cliente);
	}

	private Cliente obtenerCliente(String emailAutenticado) {
		if (emailAutenticado == null || emailAutenticado.isBlank()) {
			throw new UsuarioNoEncontradoException();
		}
		return clienteDAO.findByEmail(emailAutenticado.trim().toLowerCase(Locale.ROOT))
				.orElseThrow(UsuarioNoEncontradoException::new);
	}

	private Direccion obtenerDireccion(Cliente cliente, String idDireccion) {
		if (idDireccion == null || idDireccion.isBlank()) {
			throw new DireccionNoEncontradaException();
		}
		return cliente.buscarDireccion(idDireccion)
				.orElseThrow(DireccionNoEncontradaException::new);
	}

	private void comprobarNombreDisponible(Cliente cliente, String nombre, String idExcluido) {
		boolean repetido = cliente.getDirecciones().stream()
				.filter(direccion -> !direccion.getId().equals(idExcluido))
				.anyMatch(direccion -> direccion.getNombre().equalsIgnoreCase(nombre));
		if (repetido) {
			throw new DireccionDuplicadaException();
		}
	}

	private void aplicarDatos(Direccion direccion, DireccionRequest request) {
		direccion.setNombre(request.nombre().trim());
		direccion.setCalle(request.calle().trim());
		direccion.setNumero(request.numero().trim());
		direccion.setPortal(normalizarOpcional(request.portal()));
		direccion.setPiso(normalizarOpcional(request.piso()));
		direccion.setCodigoPostal(request.codigoPostal().trim());
		direccion.setCiudad(request.ciudad().trim());
		direccion.setProvincia(request.provincia().trim());
	}

	private String normalizarOpcional(String valor) {
		return valor == null || valor.isBlank() ? null : valor.trim();
	}
}
