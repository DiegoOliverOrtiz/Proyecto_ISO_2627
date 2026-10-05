package es.uclm.esibuy.backend.services;

import java.util.Locale;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import es.uclm.esibuy.backend.dao.UsuarioDAO;
import es.uclm.esibuy.backend.exceptions.AccountPendingException;
import es.uclm.esibuy.backend.model.Usuario;

@Service
public class AuthService {

    private static final String MENSAJE_ERROR_CREDENCIALES = "Correo o contraseña incorrectos.";

    private final UsuarioDAO usuarioDAO;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UsuarioDAO usuarioDAO, PasswordEncoder passwordEncoder) {
        this.usuarioDAO = usuarioDAO;
        this.passwordEncoder = passwordEncoder;
    }

    public Usuario autenticar(String correo, String contrasena) {
        if (correo == null || contrasena == null) {
            throw new BadCredentialsException(MENSAJE_ERROR_CREDENCIALES);
        }

        String correoNormalizado = correo.trim().toLowerCase(Locale.ROOT);
        Usuario usuario = usuarioDAO.findByEmail(correoNormalizado)
                .orElseThrow(() -> new BadCredentialsException(MENSAJE_ERROR_CREDENCIALES));

        if (usuario.getContrasenaHash() == null
                || !passwordEncoder.matches(contrasena, usuario.getContrasenaHash())
                || usuario.isBloqueada()) {
            throw new BadCredentialsException(MENSAJE_ERROR_CREDENCIALES);
        }

        if (!usuario.isActivada()) {
            throw new AccountPendingException();
        }

        return usuario;
    }
}