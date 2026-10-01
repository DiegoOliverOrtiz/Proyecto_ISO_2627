package es.uclm.esibuy.backend.services;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import es.uclm.esibuy.backend.dao.UsuarioDAO;
import es.uclm.esibuy.backend.exceptions.DuplicateRegistrationException;
import es.uclm.esibuy.backend.exceptions.InvalidRegistrationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

@Service
public class RegistrationSupport {
	private final UsuarioDAO usuarioDAO;
	private final PasswordEncoder passwordEncoder;
	private final PasswordPolicy passwordPolicy;
	private final Validator validator;

	public RegistrationSupport(
			UsuarioDAO usuarioDAO,
			PasswordEncoder passwordEncoder,
			PasswordPolicy passwordPolicy,
			Validator validator) {
		this.usuarioDAO = usuarioDAO;
		this.passwordEncoder = passwordEncoder;
		this.passwordPolicy = passwordPolicy;
		this.validator = validator;
	}

	public void validate(Object request, String password) {
		if (request == null) {
			throw new InvalidRegistrationException();
		}

		Map<String, List<String>> errors = new LinkedHashMap<>();
		for (ConstraintViolation<Object> violation : validator.validate(request)) {
			errors.computeIfAbsent(violation.getPropertyPath().toString(), field -> new java.util.ArrayList<>())
					.add(violation.getMessage());
		}
		if (!errors.isEmpty()) {
			throw new InvalidRegistrationException(errors);
		}

		passwordPolicy.validate(password);
	}

	public String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	public void ensureEmailIsAvailable(String email) {
		if (usuarioDAO.existsByEmail(email)) {
			throw new DuplicateRegistrationException();
		}
	}

	public String encodePassword(String password) {
		return passwordEncoder.encode(password);
	}

	public <T> T save(Supplier<T> operation) {
		try {
			return operation.get();
		} catch (DuplicateKeyException exception) {
			throw new DuplicateRegistrationException();
		}
	}
}