package es.uclm.esibuy.backend.services;

import java.util.Locale;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import es.uclm.esibuy.backend.dao.AdministradorDAO;
import es.uclm.esibuy.backend.dao.UsuarioDAO;
import es.uclm.esibuy.backend.dto.RegistroAdministradorRequest;
import es.uclm.esibuy.backend.dto.RegistroUsuarioResponse;
import es.uclm.esibuy.backend.dto.UsuarioAdministracionResponse;
import es.uclm.esibuy.backend.dto.ListadoUsuariosResponse;
import es.uclm.esibuy.backend.exceptions.AutoEliminacionAdministradorException;
import es.uclm.esibuy.backend.exceptions.DuplicateRegistrationException;
import es.uclm.esibuy.backend.exceptions.InvalidRegistrationException;
import es.uclm.esibuy.backend.exceptions.UsuarioNoEncontradoException;
import es.uclm.esibuy.backend.model.Administrador;
import es.uclm.esibuy.backend.model.Usuario;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.Vendedor;
import jakarta.validation.Validator;

@Service
public class AdministradorService {

	private final AdministradorDAO administradorDAO;
	private final UsuarioDAO usuarioDAO;
	private final PasswordEncoder passwordEncoder;
	private final PasswordPolicy passwordPolicy;
	private final Validator validator;

	public AdministradorService(
			AdministradorDAO administradorDAO,
			UsuarioDAO usuarioDAO,
			PasswordEncoder passwordEncoder,
			PasswordPolicy passwordPolicy,
			Validator validator) {
		this.administradorDAO = administradorDAO;
		this.usuarioDAO = usuarioDAO;
		this.passwordEncoder = passwordEncoder;
		this.passwordPolicy = passwordPolicy;
		this.validator = validator;
	}

	public ListadoUsuariosResponse consultarUsuarios(
			String texto, String estado, String rol, Pageable pageable) {
		
		// Si no se indica rol, filtramos para que NO devuelva administradores
		String rolFiltro = (rol == null || rol.trim().isEmpty()) ? "NO_ADMIN" : rol;
		
		Page<UsuarioAdministracionResponse> pagina = usuarioDAO.buscar(texto, estado, rolFiltro, pageable)
				.map(this::convertirRespuesta);
		
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
				.map(this::convertirRespuesta)
				.orElseThrow(() -> new UsuarioNoEncontradoException(id));
	}

	private UsuarioAdministracionResponse convertirRespuesta(Usuario usuario) {
		String rol;
		String sede = null;
		java.time.LocalDate fechaIncorporacion = null;
		Boolean esPremium = null;
		String nombreComercial = null;

		switch (usuario) {
    case Administrador administrador -> {
  			rol = "ADMIN";
  			sede = administrador.getSede();
  			fechaIncorporacion = administrador.getFechaIncorporacion();
  		}
    case Cliente cliente -> {
  			rol = "CLIENTE";
  			esPremium = cliente.isEsPremium();
  		}
    case Vendedor vendedor -> {
  			rol = "VENDEDOR";
  			nombreComercial = vendedor.getNombreComercial();
  		}
			default -> throw new IllegalStateException("Tipo de usuario no soportado.");
  }

		String estado;
		if (usuario.isBloqueada()) {
			estado = "BLOQUEADO";
		} else if (usuario.isActivada()) {
			estado = "ACTIVO";
		} else {
			estado = "PENDIENTE";
		}
		return new UsuarioAdministracionResponse(
				usuario.getId(),
				usuario.getNombre(),
				usuario.getApellidos(),
				usuario.getEmail(),
				usuario.getTelefono(),
				estado,
				rol,
				usuario.getImagenPerfil(),
				sede,
				fechaIncorporacion,
				esPremium,
				nombreComercial);
	}

	public RegistroUsuarioResponse registrar(RegistroAdministradorRequest request) {
		if (request == null || !validator.validate(request).isEmpty()) {
			throw new InvalidRegistrationException();
		}
		passwordPolicy.validate(request.contrasena());

		String email = request.email().trim().toLowerCase(Locale.ROOT);
		if (usuarioDAO.existsByEmail(email)) {
			throw new DuplicateRegistrationException();
		}

		Administrador administrador = new Administrador();
		administrador.setNombre(request.nombre().trim());
		administrador.setApellidos(request.apellidos().trim());
		administrador.setEmail(email);
		administrador.setSede(request.sede());
		administrador.setFechaIncorporacion(request.fechaIncorporacion());
		administrador.setImagenPerfil(request.imagenPerfil());
		administrador.setBloqueada(false);
		administrador.setActivada(true);
		administrador.registrarContrasenaHash(passwordEncoder.encode(request.contrasena()));

		try {
			Administrador guardado = administradorDAO.save(administrador);
			return new RegistroUsuarioResponse(guardado.getId(), "administrador");
		} catch (DuplicateKeyException exception) {
			throw new DuplicateRegistrationException();
		}
	}

	public void activarUsuario(String id) {
		Usuario usuario = usuarioDAO.findById(id).orElseThrow(UsuarioNoEncontradoException::new);
		if (!usuario.isActivada()) {
			usuario.setActivada(true);
			usuarioDAO.save(usuario);
		}
	}

	public void eliminarAdministrador(String id, String emailSolicitante) {
		Administrador objetivo = administradorDAO.findById(id)
				.orElseThrow(UsuarioNoEncontradoException::new);
		if (emailSolicitante.equals(objetivo.getEmail())) {
			throw new AutoEliminacionAdministradorException();
		}
		administradorDAO.delete(objetivo);
	}
}