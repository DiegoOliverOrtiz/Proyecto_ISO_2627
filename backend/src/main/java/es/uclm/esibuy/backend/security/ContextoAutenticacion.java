package es.uclm.esibuy.backend.security;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.http.HttpSession;

/**
 * Construye el contexto de seguridad y marca la sesión de un usuario ya autenticado.
 */
public final class ContextoAutenticacion {

    public static final String PREFIJO_ROL = "ROLE_";

    private ContextoAutenticacion() {
    }

    public static SecurityContext crear(String email, String rol) {
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(new UsernamePasswordAuthenticationToken(
                email,
                null,
                List.of(new SimpleGrantedAuthority(PREFIJO_ROL + rol))));
        return contexto;
    }

    public static void marcarInicioSesion(HttpSession sesion) {
        sesion.setAttribute(SesionAbsolutaFilter.ATRIBUTO_SESION_AUTENTICADA_EN, Instant.now());
    }

    public static void establecer(SecurityContext contexto) {
        SecurityContextHolder.setContext(contexto);
    }

    public static void limpiar() {
        SecurityContextHolder.clearContext();
    }

    public static Instant caducaEn(long segundos) {
        return Instant.now().plusSeconds(segundos);
    }

    public static boolean vigente(Object caducidad) {
        return caducidad instanceof Instant limite && Instant.now().isBefore(limite);
    }

    public static <T> T requerido(T dependencia) {
        return Objects.requireNonNull(dependencia);
    }
}
