package es.uclm.esibuy.backend.services;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import es.uclm.esibuy.backend.exceptions.InvalidRegistrationException;

@Component
public class PasswordPolicy {
	private static final String CAMPO_CONTRASENA = "contrasena";

	private final Set<String> commonPasswords;

	public PasswordPolicy() {
		ClassPathResource resource = new ClassPathResource("security/common-passwords.txt");
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
			this.commonPasswords = reader.lines()
					.map(PasswordPolicy::normalize)
					.filter(value -> !value.isBlank())
					.collect(Collectors.toUnmodifiableSet());
		} catch (IOException exception) {
			throw new IllegalStateException("No se pudo cargar la lista de contraseñas comunes.", exception);
		}
	}

	public void validate(String password) {
		validate(password, Set.of());
	}

	public void validate(String password, Collection<String> personalData) {
		if (password == null || password.codePointCount(0, password.length()) < 8
				|| !contains(password, Character::isUpperCase)
				|| !contains(password, Character::isLowerCase)
				|| !contains(password, Character::isDigit)
				|| !containsSpecial(password)) {
			throw new InvalidRegistrationException(
					CAMPO_CONTRASENA,
					"La contraseña debe tener al menos 8 caracteres e incluir mayúsculas, minúsculas, números y símbolos.");
		}

		String normalizedPassword = normalize(password);
		String alphanumericPassword = normalizedPassword.replaceAll("[^\\p{L}\\p{N}]+", "");
		String passwordWithoutTrailingDigits = quitarNumerosFinales(alphanumericPassword);
		if (commonPasswords.contains(normalizedPassword)
				|| commonPasswords.contains(alphanumericPassword)
				|| commonPasswords.contains(passwordWithoutTrailingDigits)) {
			throw new InvalidRegistrationException(
					CAMPO_CONTRASENA,
					"La contraseña no puede contener términos de la lista de contraseñas comunes.");
		}

		Collection<String> identifiers = new ArrayList<>();
		identifiers.add("ESIBuy");
		if (personalData != null) {
			identifiers.addAll(personalData);
		}
		String normalizedPasswordIdentifiers = normalizeIdentifier(password);
		boolean containsPersonalData = identifiers.stream()
				.filter(value -> value != null && !value.isBlank())
				.flatMap(value -> identifierTokens(value).stream())
				.anyMatch(normalizedPasswordIdentifiers::contains);
		if (containsPersonalData) {
			throw new InvalidRegistrationException(
					CAMPO_CONTRASENA,
					"La contraseña no puede contener datos personales ni el nombre de la aplicación.");
		}

		String normalizedPasswordDigits = digitsOnly(password);
		boolean containsPersonalNumber = identifiers.stream()
				.filter(value -> value != null && !value.isBlank())
				.flatMap(value -> numericIdentifierVariants(value).stream())
				.anyMatch(normalizedPasswordDigits::contains);
		if (containsPersonalNumber) {
			throw new InvalidRegistrationException(
					CAMPO_CONTRASENA,
					"La contraseña no puede contener la fecha de nacimiento, el teléfono ni el DNI.");
		}
	}

	public boolean matchesRecentPassword(String candidate, Iterable<String> passwordHashes, PasswordEncoder encoder) {
		if (candidate == null || passwordHashes == null) {
			return false;
		}
		for (String passwordHash : passwordHashes) {
			if (passwordHash != null && encoder.matches(candidate, passwordHash)) {
				return true;
			}
		}
		return false;
	}

	private static boolean contains(String value, java.util.function.IntPredicate predicate) {
		return value.codePoints().anyMatch(predicate);
	}

	private static boolean containsSpecial(String value) {
		return value.codePoints().anyMatch(codePoint ->
				!Character.isLetterOrDigit(codePoint) && !Character.isWhitespace(codePoint));
	}

	private static String normalize(String value) {
		return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
	}

	// Equivale a replaceFirst("\\p{N}+$", "") sin expresión regular, para que el coste sea lineal
	// aunque la contraseña tenga muchos dígitos que no estén al final (S5852).
	private static String quitarNumerosFinales(String value) {
		int fin = value.length();
		while (fin > 0) {
			int codePoint = value.codePointBefore(fin);
			if (!esNumero(codePoint)) {
				break;
			}
			fin -= Character.charCount(codePoint);
		}
		return value.substring(0, fin);
	}

	private static boolean esNumero(int codePoint) {
		int tipo = Character.getType(codePoint);
		return tipo == Character.DECIMAL_DIGIT_NUMBER
				|| tipo == Character.LETTER_NUMBER
				|| tipo == Character.OTHER_NUMBER;
	}

	private static Set<String> identifierTokens(String value) {
		String normalized = normalizeIdentifier(value).replaceAll("[^\\p{L}\\p{N}]+", " ");
		return java.util.Arrays.stream(normalized.split("\\s+"))
				.filter(token -> token.codePointCount(0, token.length()) >= 3
						&& token.codePoints().anyMatch(Character::isLetter))
				.collect(Collectors.toUnmodifiableSet());
	}

	private static String normalizeIdentifier(String value) {
		return Normalizer.normalize(value, Normalizer.Form.NFKD)
				.toLowerCase(Locale.ROOT)
				.replaceAll("\\p{M}+", "");
	}

	private static String digitsOnly(String value) {
		StringBuilder digits = new StringBuilder();
		value.codePoints()
				.map(Character::getNumericValue)
				.filter(digit -> digit >= 0 && digit <= 9)
				.forEach(digit -> digits.append((char) ('0' + digit)));
		return digits.toString();
	}

	private static Set<String> numericIdentifierVariants(String value) {
		String digits = digitsOnly(value);
		if (digits.length() < 7) {
			return Set.of();
		}
		if (digits.length() > 9) {
			return Set.of(digits, digits.substring(digits.length() - 9));
		}
		return Set.of(digits);
	}
}