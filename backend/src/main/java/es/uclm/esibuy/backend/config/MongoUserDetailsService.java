package es.uclm.esibuy.backend.config;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import es.uclm.esibuy.backend.dao.UsuarioDAO;
import es.uclm.esibuy.backend.model.Administrador;
import es.uclm.esibuy.backend.model.Usuario;

@Service
public class MongoUserDetailsService implements UserDetailsService {

	private final UsuarioDAO usuarioDAO;

	public MongoUserDetailsService(UsuarioDAO usuarioDAO) {
		this.usuarioDAO = usuarioDAO;
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		Usuario usuario = usuarioDAO.findByEmail(username.trim().toLowerCase(java.util.Locale.ROOT))
				.orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado."));
		String authority = usuario instanceof Administrador ? "ROLE_ADMIN" : "ROLE_USER";
		return User.withUsername(usuario.getEmail())
				.password(usuario.getContrasenaHash())
				.authorities(authority)
				.accountLocked(usuario.isBloqueada())
				.disabled(!usuario.isActivada())
				.build();
	}
}