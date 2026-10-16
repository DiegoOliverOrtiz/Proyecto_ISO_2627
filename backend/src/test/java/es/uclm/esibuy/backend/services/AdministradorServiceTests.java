package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import es.uclm.esibuy.backend.config.MongoUserDetailsService;
import es.uclm.esibuy.backend.dao.AdministradorDAO;
import es.uclm.esibuy.backend.dao.UsuarioDAO;
import es.uclm.esibuy.backend.dto.RegistroAdministradorRequest;
import es.uclm.esibuy.backend.exceptions.DuplicateRegistrationException;
import es.uclm.esibuy.backend.model.Administrador;
import java.time.Month;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

@ExtendWith(MockitoExtension.class)
class AdministradorServiceTests {

	private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	@Mock
	private AdministradorDAO administradorDAO;

	@Mock
	private UsuarioDAO usuarioDAO;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
    private Validator validator;

	@Mock
	private SesionService sesionService;

	@Test
	void registersAdministratorWithEncodedPasswordAndOptionalAdministrativeData() {
		when(usuarioDAO.existsByEmail("admin@example.com")).thenReturn(false);
		when(passwordEncoder.encode("GestorX9!Clave")).thenReturn("bcrypt-admin-hash");
		when(administradorDAO.save(any(Administrador.class))).thenAnswer(invocation -> {
			Administrador administrador = invocation.getArgument(0);
			administrador.setId("admin-id");
			return administrador;
		});

		AdministradorService service = new AdministradorService(
				administradorDAO,
				usuarioDAO,
				passwordEncoder,
				new PasswordPolicy(),
				VALIDATOR,
				sesionService);
		RegistroAdministradorRequest request = new RegistroAdministradorRequest(
				"Primero",
				"Administrador",
				"admin@example.com",
				"GestorX9!Clave",
				"Sede central",
				LocalDate.of(2026, Month.OCTOBER, 1),
				"perfil.png");

		var response = service.registrar(request);

		assertEquals("admin-id", response.id());
		assertEquals("administrador", response.tipoUsuario());
		ArgumentCaptor<Administrador> saved = ArgumentCaptor.forClass(Administrador.class);
		verify(administradorDAO).save(saved.capture());
		assertEquals("bcrypt-admin-hash", saved.getValue().getContrasenaHash());
		assertEquals(List.of("bcrypt-admin-hash"), saved.getValue().getHistorialContrasenasHash());
		assertEquals("ADMIN", saved.getValue().getRol());
	}

	@Test
	void rejectsEmailAlreadyUsedByAnotherUser() {
		when(usuarioDAO.existsByEmail("admin@example.com")).thenReturn(true);
		AdministradorService service = new AdministradorService(
				administradorDAO,
				usuarioDAO,
				passwordEncoder,
				new PasswordPolicy(),
				VALIDATOR,
				sesionService);
		RegistroAdministradorRequest request = new RegistroAdministradorRequest(
				"Primero", "Administrador", "admin@example.com", "GestorX9!Clave", null, null, null);

		assertThrows(DuplicateRegistrationException.class, () -> service.registrar(request));
	}

	@Test
	void grantsAdminRoleOnlyToAdministratorDocuments() {
		Administrador administrador = new Administrador();
		administrador.setEmail("admin@example.com");
		administrador.setContrasenaHash("bcrypt-admin-hash");
		administrador.setActivada(true);

		when(usuarioDAO.findByEmail("admin@example.com")).thenReturn(Optional.of(administrador));

		UserDetails userDetails = new MongoUserDetailsService(usuarioDAO).loadUserByUsername(" admin@example.com ");

		assertEquals("admin@example.com", userDetails.getUsername());
		assertEquals("bcrypt-admin-hash", userDetails.getPassword());
		assertEquals("ROLE_ADMIN", userDetails.getAuthorities().iterator().next().getAuthority());
	}
}