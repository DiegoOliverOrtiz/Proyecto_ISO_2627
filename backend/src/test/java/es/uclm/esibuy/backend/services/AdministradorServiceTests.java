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
import es.uclm.esibuy.backend.dto.ModificarUsuarioRequest;
import es.uclm.esibuy.backend.exceptions.DuplicateRegistrationException;
import es.uclm.esibuy.backend.model.Administrador;
import es.uclm.esibuy.backend.model.Cliente;
import java.time.Month;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

@ExtendWith(MockitoExtension.class)
class AdministradorServiceTests {

	private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	@Mock
	private AdministradorDAO administradorDAO;

	@Mock
	private es.uclm.esibuy.backend.dao.CategoriaDAO categoriaDAO;

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
				categoriaDAO,
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
				categoriaDAO,
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

	@Test
	void mantieneCamposProtegidosDelAdministradorDuranteLaModificacion() {
		Administrador administrador = new Administrador();
		administrador.setId("admin-1");
		administrador.setEmail("admin@example.com");
		administrador.setContrasenaHash("hash-original");
		administrador.setRol("ADMIN");
		administrador.setFechaIncorporacion(LocalDate.of(2024, 1, 10));
		administrador.setSede("Madrid");
		when(usuarioDAO.findById("admin-1")).thenReturn(Optional.of(administrador));
		when(usuarioDAO.save(any(Administrador.class))).thenAnswer(invocation -> invocation.getArgument(0));

		AdministradorService service = new AdministradorService(
				administradorDAO, categoriaDAO, usuarioDAO, passwordEncoder,
				new PasswordPolicy(), VALIDATOR, sesionService);
		ModificarUsuarioRequest request = new ModificarUsuarioRequest(
				"Nombre actualizado", "Apellidos actualizados", "600123123", null,
				null, null, "Toledo", null);

		service.modificarUsuario("admin-1", request);

		assertEquals("admin@example.com", administrador.getEmail());
		assertEquals("hash-original", administrador.getContrasenaHash());
		assertEquals("ADMIN", administrador.getRol());
		assertEquals(LocalDate.of(2024, 1, 10), administrador.getFechaIncorporacion());
		assertEquals("Nombre actualizado", administrador.getNombre());
		assertEquals("Toledo", administrador.getSede());
	}

	@Test
	void mantieneEmailContrasenaIdYPremiumDelClienteDuranteLaModificacion() {
		Cliente cliente = new Cliente();
		cliente.setId("cliente-1");
		cliente.setEmail("cliente@example.com");
		cliente.setContrasenaHash("hash-original");
		cliente.setEsPremium(true);
		cliente.setDniNif("12345678A");
		cliente.setFechaNacimiento(LocalDate.of(1990, 1, 1));
		when(usuarioDAO.findById("cliente-1")).thenReturn(Optional.of(cliente));
		when(usuarioDAO.save(any(Cliente.class))).thenAnswer(invocation -> invocation.getArgument(0));

		AdministradorService service = new AdministradorService(
				administradorDAO, categoriaDAO, usuarioDAO, passwordEncoder,
				new PasswordPolicy(), VALIDATOR, sesionService);
		ModificarUsuarioRequest request = new ModificarUsuarioRequest(
				"Ana actualizada", "García", "600123123", null,
				"87654321B", LocalDate.of(2000, 2, 2), "Sede ignorada", "cat-ignorada");

		service.modificarUsuario("cliente-1", request);

		assertEquals("cliente@example.com", cliente.getEmail());
		assertEquals("hash-original", cliente.getContrasenaHash());
		assertEquals("cliente-1", cliente.getId());
		assertEquals(true, cliente.isEsPremium());
		assertEquals("87654321B", cliente.getDniNif());
		assertEquals(LocalDate.of(2000, 2, 2), cliente.getFechaNacimiento());
	}
}
