package es.uclm.esibuy.backend.services;

import java.util.Locale;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import es.uclm.esibuy.backend.dao.AdministradorDAO;
import es.uclm.esibuy.backend.dao.UsuarioDAO;
import es.uclm.esibuy.backend.dto.RegistroAdministradorRequest;
import es.uclm.esibuy.backend.dto.RegistroUsuarioResponse;
import es.uclm.esibuy.backend.exceptions.DuplicateRegistrationException;
import es.uclm.esibuy.backend.exceptions.InvalidRegistrationException;
import es.uclm.esibuy.backend.model.Administrador;
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
		administrador.setActivada(false);
		administrador.registrarContrasenaHash(passwordEncoder.encode(request.contrasena()));

		try {
			Administrador guardado = administradorDAO.save(administrador);
			return new RegistroUsuarioResponse(guardado.getId(), "administrador");
		} catch (DuplicateKeyException exception) {
			throw new DuplicateRegistrationException();
		}
	}
}
