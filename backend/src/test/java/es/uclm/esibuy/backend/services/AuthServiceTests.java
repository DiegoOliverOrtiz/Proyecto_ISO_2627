package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import es.uclm.esibuy.backend.dao.UsuarioDAO;
import es.uclm.esibuy.backend.exceptions.AccountPendingException;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.Usuario;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {

    private static final String CORREO = "cliente@example.com";
    private static final String CONTRASENA = "ClaveSegura9!";
    private static final String HASH = "hash-guardado";

    @Mock
    private UsuarioDAO usuarioDAO;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(usuarioDAO, passwordEncoder);
    }

    @Test
    void autenticaUsuarioActivoYNormalizaElCorreo() {
        Cliente cliente = crearCliente(true, false);
        when(usuarioDAO.findByEmail(CORREO)).thenReturn(Optional.of(cliente));
        when(passwordEncoder.matches(CONTRASENA, HASH)).thenReturn(true);

        Usuario autenticado = authService.autenticar("  CLIENTE@EXAMPLE.COM ", CONTRASENA);

        assertSame(cliente, autenticado);
        verify(usuarioDAO).findByEmail(CORREO);
        verify(passwordEncoder).matches(CONTRASENA, HASH);
    }

    @Test
    void rechazaCorreoONullConErrorGenericoSinConsultarLaBaseDeDatos() {
        BadCredentialsException errorCorreo = assertThrows(
                BadCredentialsException.class,
                () -> authService.autenticar(null, CONTRASENA));
        BadCredentialsException errorContrasena = assertThrows(
                BadCredentialsException.class,
                () -> authService.autenticar(CORREO, null));

        assertEquals("Correo o contraseña incorrectos.", errorCorreo.getMessage());
        assertEquals(errorCorreo.getMessage(), errorContrasena.getMessage());
        verifyNoInteractions(usuarioDAO, passwordEncoder);
    }

    @Test
    void rechazaCorreoInexistenteConElMismoErrorGenerico() {
        when(usuarioDAO.findByEmail(CORREO)).thenReturn(Optional.empty());

        BadCredentialsException error = assertThrows(
                BadCredentialsException.class,
                () -> authService.autenticar(CORREO, CONTRASENA));

        assertEquals("Correo o contraseña incorrectos.", error.getMessage());
    }

    @Test
    void rechazaContrasenaIncorrectaConElMismoErrorGenerico() {
        Cliente cliente = crearCliente(true, false);
        when(usuarioDAO.findByEmail(CORREO)).thenReturn(Optional.of(cliente));
        when(passwordEncoder.matches(CONTRASENA, HASH)).thenReturn(false);

        BadCredentialsException error = assertThrows(
                BadCredentialsException.class,
                () -> authService.autenticar(CORREO, CONTRASENA));

        assertEquals("Correo o contraseña incorrectos.", error.getMessage());
    }

    @Test
    void rechazaUsuarioBloqueadoConElMismoErrorGenerico() {
        Cliente cliente = crearCliente(true, true);
        when(usuarioDAO.findByEmail(CORREO)).thenReturn(Optional.of(cliente));
        when(passwordEncoder.matches(CONTRASENA, HASH)).thenReturn(true);

        BadCredentialsException error = assertThrows(
                BadCredentialsException.class,
                () -> authService.autenticar(CORREO, CONTRASENA));

        assertEquals("Correo o contraseña incorrectos.", error.getMessage());
    }

    @Test
    void rechazaUsuarioInactivoConAvisoDeCuentaPendiente() {
        Cliente cliente = crearCliente(false, false);
        when(usuarioDAO.findByEmail(CORREO)).thenReturn(Optional.of(cliente));
        when(passwordEncoder.matches(CONTRASENA, HASH)).thenReturn(true);

        assertThrows(
                AccountPendingException.class,
                () -> authService.autenticar(CORREO, CONTRASENA));
    }

    private Cliente crearCliente(boolean activado, boolean bloqueado) {
        Cliente cliente = new Cliente();
        cliente.setEmail(CORREO);
        cliente.setContrasenaHash(HASH);
        cliente.setActivada(activado);
        cliente.setBloqueada(bloqueado);
        return cliente;
    }
}
