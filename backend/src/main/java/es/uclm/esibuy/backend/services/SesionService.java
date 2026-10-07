package es.uclm.esibuy.backend.services;

import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;

@Service
public class SesionService {

	private final SessionRegistry sessionRegistry;

	public SesionService(SessionRegistry sessionRegistry) {
		this.sessionRegistry = sessionRegistry;
	}

	public void registrarSesion(String idSesion, String email) {
		throw new UnsupportedOperationException("pendiente");
	}

	public void cerrarSesiones(String email) {
		throw new UnsupportedOperationException("pendiente");
	}
}