package es.uclm.esibuy.backend.security;

import es.uclm.esibuy.backend.model.Administrador;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.Usuario;
import es.uclm.esibuy.backend.model.Vendedor;

/**
 * Rol de Spring Security (sin el prefijo {@code ROLE_}) que corresponde a cada tipo de usuario.
 */
public final class RolUsuario {

    private RolUsuario() {
    }

    public static String de(Usuario usuario) {
        if (usuario instanceof Administrador) {
            return "ADMIN";
        }
        if (usuario instanceof Cliente) {
            return "CLIENTE";
        }
        if (usuario instanceof Vendedor) {
            return "VENDEDOR";
        }
        throw new IllegalStateException("Tipo de usuario no soportado para el inicio de sesión.");
    }

    // El segundo factor (TOTP) es obligatorio para vendedores y administradores.
    public static boolean requiereTotpObligatorio(Usuario usuario) {
        return usuario instanceof Vendedor
                || usuario instanceof Administrador;
    }
    public static boolean requiereTercerFactor(Usuario usuario) {
        return requiereTotpObligatorio(usuario)
                || (usuario instanceof Cliente && usuario.isTotpActivado() && usuario.isEmailOtpActivado());
    }
}
