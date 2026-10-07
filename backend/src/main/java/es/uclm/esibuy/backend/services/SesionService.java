package es.uclm.esibuy.backend.services;

import java.util.Locale;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;

@Service
public class SesionService {

	private final SessionRegistry sessionRegistry;

	public SesionService(SessionRegistry sessionRegistry) {
		this.sessionRegistry = sessionRegistry;
	}

	public void registrarSesion(String idSesion, String email) {
		sessionRegistry.registerNewSession(idSesion, normalizar(email));
	}

	public void cerrarSesiones(String email) {
		if (email == null || email.isBlank()) {
			return;
		}
		sessionRegistry.getAllSessions(normalizar(email), false)
				.forEach(SessionInformation::expireNow);
	}

	private String normalizar(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}