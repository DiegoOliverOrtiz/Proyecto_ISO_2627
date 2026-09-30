package es.uclm.esibuy.backend.services;

import java.util.Locale;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import es.uclm.esibuy.backend.dao.ClienteDAO;
import es.uclm.esibuy.backend.dao.UsuarioDAO;
import es.uclm.esibuy.backend.dto.RegistroClienteRequest;
import es.uclm.esibuy.backend.dto.RegistroUsuarioResponse;
import es.uclm.esibuy.backend.exceptions.DuplicateRegistrationException;
import es.uclm.esibuy.backend.exceptions.InvalidRegistrationException;
import es.uclm.esibuy.backend.model.Cliente;
import jakarta.validation.Validator;

@Service
public class ClienteService {

	private final ClienteDAO clienteDAO;
	private final UsuarioDAO usuarioDAO;
	private final PasswordEncoder passwordEncoder;
	private final PasswordPolicy passwordPolicy;
	private final Validator validator;

	public ClienteService(
			ClienteDAO clienteDAO,
			UsuarioDAO usuarioDAO,
			PasswordEncoder passwordEncoder,
			PasswordPolicy passwordPolicy,
			Validator validator) {
		this.clienteDAO = clienteDAO;
		this.usuarioDAO = usuarioDAO;
		this.passwordEncoder = passwordEncoder;
		this.passwordPolicy = passwordPolicy;
		this.validator = validator;
	}

	public RegistroUsuarioResponse registrar(RegistroClienteRequest request) {
		if (request == null || !validator.validate(request).isEmpty()) {
			throw new InvalidRegistrationException();
		}
		passwordPolicy.validate(request.contrasena());

		String email = request.email().trim().toLowerCase(Locale.ROOT);
		if (usuarioDAO.existsByEmail(email)) {
			throw new DuplicateRegistrationException();
		}

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
		cliente.registrarContrasenaHash(passwordEncoder.encode(request.contrasena()));

		try {
			Cliente guardado = clienteDAO.save(cliente);
			return new RegistroUsuarioResponse(guardado.getId(), "cliente");
		} catch (DuplicateKeyException exception) {
			throw new DuplicateRegistrationException();
		}
	}
}