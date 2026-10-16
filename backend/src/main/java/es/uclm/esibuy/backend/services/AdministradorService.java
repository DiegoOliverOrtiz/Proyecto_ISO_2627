package es.uclm.esibuy.backend.services;

import java.time.LocalDate;
import java.util.Locale;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import es.uclm.esibuy.backend.dao.AdministradorDAO;
import es.uclm.esibuy.backend.dao.UsuarioDAO;
import es.uclm.esibuy.backend.dto.ListadoUsuariosResponse;
import es.uclm.esibuy.backend.dto.ModificarUsuarioRequest;
import es.uclm.esibuy.backend.dto.RegistroAdministradorRequest;
import es.uclm.esibuy.backend.dto.RegistroUsuarioResponse;
import es.uclm.esibuy.backend.dto.UsuarioAdministracionResponse;
import es.uclm.esibuy.backend.exceptions.AutoBloqueoAdministradorException;
import es.uclm.esibuy.backend.exceptions.AutoEliminacionAdministradorException;
import es.uclm.esibuy.backend.exceptions.DuplicateRegistrationException;
import es.uclm.esibuy.backend.exceptions.InvalidRegistrationException;
import es.uclm.esibuy.backend.exceptions.UltimoAdministradorException;
import es.uclm.esibuy.backend.exceptions.UsuarioNoEncontradoException;
import es.uclm.esibuy.backend.model.Administrador;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.Usuario;
import es.uclm.esibuy.backend.model.Vendedor;
import jakarta.validation.Validator;

@Service
public class AdministradorService {

	private static final String MENSAJE_SOLICITANTE_NO_IDENTIFICADO =
			"No se ha podido identificar al solicitante.";

	private final AdministradorDAO administradorDAO;
	private final UsuarioDAO usuarioDAO;
	private final PasswordEncoder passwordEncoder;
	private final PasswordPolicy passwordPolicy;
	private final Validator validator;
	private final SesionService sesionService;

	public AdministradorService(
			AdministradorDAO administradorDAO,
			UsuarioDAO usuarioDAO,
			PasswordEncoder passwordEncoder,
			PasswordPolicy passwordPolicy,
			Validator validator,
			SesionService sesionService) {

		this.administradorDAO = administradorDAO;
		this.usuarioDAO = usuarioDAO;
		this.passwordEncoder = passwordEncoder;
		this.passwordPolicy = passwordPolicy;
		this.validator = validator;
		this.sesionService = sesionService;
	}

	public ListadoUsuariosResponse consultarUsuarios(
			String texto,
			String estado,
			String rol,
			Pageable pageable) {

		// Si no se indica rol, filtramos para que NO devuelva administradores
		String rolFiltro = (rol == null || rol.trim().isEmpty())
				? "NO_ADMIN"
				: rol;

		Page<UsuarioAdministracionResponse> pagina =
				usuarioDAO.buscar(texto, estado, rolFiltro, pageable)
						.map(UsuarioAdministracionResponse::desde);

		return new ListadoUsuariosResponse(
				pagina.getContent(),
				pagina.getNumber(),
				pagina.getSize(),
				pagina.getTotalElements(),
				pagina.getTotalPages(),
				usuarioDAO.obtenerEstadisticas());
	}

	public UsuarioAdministracionResponse consultarUsuario(String id) {

		return usuarioDAO.buscarPorId(id)
				.map(UsuarioAdministracionResponse::desde)
				.orElseThrow(() ->
						new UsuarioNoEncontradoException(id));
	}

	/*
	 * HU07 - Modificar usuario
	 */
	public UsuarioAdministracionResponse modificarUsuario(
			String id,
			ModificarUsuarioRequest request) {

		if (request == null) {
			throw new IllegalArgumentException(
					"Los datos de modificación son obligatorios.");
		}

		Usuario usuario = usuarioDAO.findById(id)
				.orElseThrow(() ->
						new UsuarioNoEncontradoException(id));

		/*
		 * Campos comunes modificables
		 */
		if (request.nombre() == null ||
				request.nombre().isBlank()) {

			throw new IllegalArgumentException(
					"El nombre es obligatorio.");
		}

		if (request.apellidos() == null ||
				request.apellidos().isBlank()) {

			throw new IllegalArgumentException(
					"Los apellidos son obligatorios.");
		}

		usuario.setNombre(
				request.nombre().trim());

		usuario.setApellidos(
				request.apellidos().trim());

		usuario.setTelefono(
				request.telefono());

		usuario.setImagenPerfil(
				request.imagenPerfil());

		/*
		 * Campos específicos según
		 * el tipo de usuario
		 */
		switch (usuario) {

			case Cliente cliente -> modificarDatosCliente(cliente, request);

			case Vendedor vendedor -> vendedor.setDniNif(request.dniNif());

			case Administrador administrador -> administrador.setSede(request.sede());

			default -> throw new IllegalStateException(
					"Tipo de usuario no soportado.");
		}

		/*
		 * Persistimos los cambios
		 */
		Usuario usuarioGuardado =
				usuarioDAO.save(usuario);

		return UsuarioAdministracionResponse.desde(
				usuarioGuardado);
	}

	private void modificarDatosCliente(
			Cliente cliente,
			ModificarUsuarioRequest request) {

		if (request.fechaNacimiento() == null) {
			throw new IllegalArgumentException(
					"La fecha de nacimiento es obligatoria.");
		}

		LocalDate fechaLimite =
				LocalDate.now().minusYears(18);

		if (request.fechaNacimiento()
				.isAfter(fechaLimite)) {

			throw new IllegalArgumentException(
					"El cliente debe ser mayor de edad.");
		}

		cliente.setDniNif(
				request.dniNif());

		cliente.setFechaNacimiento(
				request.fechaNacimiento());
	}

	public RegistroUsuarioResponse registrar(
			RegistroAdministradorRequest request) {

		if (request == null ||
				!validator.validate(request).isEmpty()) {

			throw new InvalidRegistrationException();
		}

		passwordPolicy.validate(
				request.contrasena());

		String email =
				request.email()
						.trim()
						.toLowerCase(Locale.ROOT);

		if (usuarioDAO.existsByEmail(email)) {

			throw new DuplicateRegistrationException();
		}

		Administrador administrador =
				new Administrador();

		administrador.setNombre(
				request.nombre().trim());

		administrador.setApellidos(
				request.apellidos().trim());

		administrador.setEmail(
				email);

		administrador.setSede(
				request.sede());

		administrador.setFechaIncorporacion(
				request.fechaIncorporacion());

		administrador.setImagenPerfil(
				request.imagenPerfil());

		administrador.setBloqueada(false);

		administrador.setActivada(true);

		administrador.registrarContrasenaHash(
				passwordEncoder.encode(
						request.contrasena()));

		try {

			Administrador guardado =
					administradorDAO.save(
							administrador);

			return new RegistroUsuarioResponse(
					guardado.getId(),
					"administrador");

		} catch (DuplicateKeyException exception) {

			throw new DuplicateRegistrationException();
		}
	}

	public void activarUsuario(String id) {

		Usuario usuario =
				usuarioDAO.findById(id)
						.orElseThrow(
								UsuarioNoEncontradoException::new);

		if (!usuario.isActivada()) {

			usuario.setActivada(true);

			usuarioDAO.save(usuario);
		}
	}

	public void bloquearUsuario(
			String id,
			String emailSolicitante) {

		if (emailSolicitante == null ||
				emailSolicitante.isBlank()) {

			throw new AccessDeniedException(
					MENSAJE_SOLICITANTE_NO_IDENTIFICADO);
		}

		Usuario usuario =
				usuarioDAO.findById(id)
						.orElseThrow(
								UsuarioNoEncontradoException::new);

		String emailNormalizado =
				emailSolicitante
						.trim()
						.toLowerCase(Locale.ROOT);

		if (emailNormalizado.equals(
				usuario.getEmail())) {

			throw new AutoBloqueoAdministradorException();
		}

		if (!usuario.isBloqueada()) {

			usuario.setBloqueada(true);

			usuarioDAO.save(usuario);

			sesionService.cerrarSesiones(
					usuario.getEmail());
		}
	}

	public void desbloquearUsuario(
			String id,
			String emailSolicitante) {

		if (emailSolicitante == null ||
				emailSolicitante.isBlank()) {

			throw new AccessDeniedException(
					MENSAJE_SOLICITANTE_NO_IDENTIFICADO);
		}

		Usuario usuario =
				usuarioDAO.findById(id)
						.orElseThrow(
								UsuarioNoEncontradoException::new);

		String emailNormalizado =
				emailSolicitante
						.trim()
						.toLowerCase(Locale.ROOT);

		if (emailNormalizado.equals(
				usuario.getEmail())) {

			throw new AutoBloqueoAdministradorException();
		}

		if (usuario.isBloqueada()) {

			usuario.setBloqueada(false);

			usuarioDAO.save(usuario);
		}
	}

	public void eliminarAdministrador(
			String id,
			String emailSolicitante) {

		if (emailSolicitante == null ||
				emailSolicitante.isBlank()) {

			throw new AccessDeniedException(
					MENSAJE_SOLICITANTE_NO_IDENTIFICADO);
		}

		if (id == null ||
				id.isBlank()) {

			throw new UsuarioNoEncontradoException();
		}

		Administrador objetivo =
				administradorDAO.findById(id)
						.orElseThrow(
								UsuarioNoEncontradoException::new);

		String emailNormalizado =
				emailSolicitante
						.trim()
						.toLowerCase(Locale.ROOT);

		if (emailNormalizado.equals(
				objetivo.getEmail())) {

			throw new AutoEliminacionAdministradorException();
		}

		if (administradorDAO
				.contarAdministradores() <= 1) {

			throw new UltimoAdministradorException();
		}

		administradorDAO.delete(objetivo);

		sesionService.cerrarSesiones(
				objetivo.getEmail());
	}
}