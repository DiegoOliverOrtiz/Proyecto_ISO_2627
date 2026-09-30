package es.uclm.esibuy.backend.services;

import java.util.Locale;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import es.uclm.esibuy.backend.dao.UsuarioDAO;
import es.uclm.esibuy.backend.dao.VendedorDAO;
import es.uclm.esibuy.backend.dto.RegistroUsuarioResponse;
import es.uclm.esibuy.backend.dto.RegistroVendedorRequest;
import es.uclm.esibuy.backend.exceptions.DuplicateRegistrationException;
import es.uclm.esibuy.backend.exceptions.InvalidRegistrationException;
import es.uclm.esibuy.backend.model.Vendedor;
import jakarta.validation.Validator;

@Service
public class VendedorService {

	private final VendedorDAO vendedorDAO;
	private final UsuarioDAO usuarioDAO;
	private final PasswordEncoder passwordEncoder;
	private final PasswordPolicy passwordPolicy;
	private final Validator validator;

	public VendedorService(
			VendedorDAO vendedorDAO,
			UsuarioDAO usuarioDAO,
			PasswordEncoder passwordEncoder,
			PasswordPolicy passwordPolicy,
			Validator validator) {
		this.vendedorDAO = vendedorDAO;
		this.usuarioDAO = usuarioDAO;
		this.passwordEncoder = passwordEncoder;
		this.passwordPolicy = passwordPolicy;
		this.validator = validator;
	}

	public RegistroUsuarioResponse registrar(RegistroVendedorRequest request) {
		if (request == null || !validator.validate(request).isEmpty()) {
			throw new InvalidRegistrationException();
		}
		passwordPolicy.validate(request.contrasena());

		String email = request.email().trim().toLowerCase(Locale.ROOT);
		String nombreComercial = request.nombreComercial().trim();
		if (usuarioDAO.existsByEmail(email) || vendedorDAO.existsByNombreComercial(nombreComercial)) {
			throw new DuplicateRegistrationException();
		}

		Vendedor vendedor = new Vendedor();
		vendedor.setNombre(request.nombre().trim());
		vendedor.setApellidos(request.apellidos().trim());
		vendedor.setEmail(email);
		vendedor.setCategoria(request.categoria().trim());
		vendedor.setDniNif(request.dniNif().trim());
		vendedor.setNombreComercial(nombreComercial);
		vendedor.setTelefono(request.telefono());
		vendedor.setImagenPerfil(request.imagenPerfil());
		vendedor.setBloqueada(false);
		vendedor.setActivada(false);
		vendedor.registrarContrasenaHash(passwordEncoder.encode(request.contrasena()));

		try {
			Vendedor guardado = vendedorDAO.save(vendedor);
			return new RegistroUsuarioResponse(guardado.getId(), "vendedor");
		} catch (DuplicateKeyException exception) {
			throw new DuplicateRegistrationException();
		}
	}
}
