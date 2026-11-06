package es.uclm.esibuy.backend.security;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

public class SesionAbsolutaFilter extends OncePerRequestFilter {

	public static final String ATRIBUTO_SESION_AUTENTICADA_EN = "ESIBUY_SESION_AUTENTICADA_EN";

	private final Duration timeoutAbsoluto;

	public SesionAbsolutaFilter(Duration timeoutAbsoluto) {
		this.timeoutAbsoluto = timeoutAbsoluto;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		HttpSession sesion = request.getSession(false);
		if (sesion == null) {
			filterChain.doFilter(request, response);
			return;
		}

		Object valorInstante = sesion.getAttribute(ATRIBUTO_SESION_AUTENTICADA_EN);
		Instant autenticadaEn;
		if (valorInstante instanceof Instant instante) {
			autenticadaEn = instante;
		} else if (valorInstante == null && tieneContextoAutenticado(sesion)) {
			autenticadaEn = Instant.ofEpochMilli(sesion.getCreationTime());
			sesion.setAttribute(ATRIBUTO_SESION_AUTENTICADA_EN, autenticadaEn);
		} else if (valorInstante != null) {
			expirarSesion(sesion, response);
			return;
		} else {
			filterChain.doFilter(request, response);
			return;
		}

		if (!Instant.now().isBefore(autenticadaEn.plus(timeoutAbsoluto))) {
			expirarSesion(sesion, response);
			return;
		}

		filterChain.doFilter(request, response);
	}

	private boolean tieneContextoAutenticado(HttpSession sesion) {
		Object contexto = sesion.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
		if (!(contexto instanceof SecurityContext securityContext)) {
			return false;
		}

		Authentication authentication = securityContext.getAuthentication();
		return authentication != null && authentication.isAuthenticated();
	}

	private void expirarSesion(HttpSession sesion, HttpServletResponse response) {
		sesion.invalidate();
		SecurityContextHolder.clearContext();
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
	}
}
