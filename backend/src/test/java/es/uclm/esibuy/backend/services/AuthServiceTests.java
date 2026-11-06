package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

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
import es.uclm.esibuy.backend.exceptions.LoginRateLimitException;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.Usuario;
import es.uclm.esibuy.backend.security.PepperedBCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {

    private static final String CORREO = "cliente@example.com";
    private static final String CONTRASENA = "ClaveSegura9!";
    private static final String HASH = "hash-guardado";

    @Mock
    private UsuarioDAO usuarioDAO;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private AuditoriaService auditoriaService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(usuarioDAO, passwordEncoder, loginAttemptService, auditoriaService);
    }

    @Test
    void autenticaUsuarioActivoYNormalizaElCorreo() {
        Cliente cliente = crearCliente(true, false);
        when(usuarioDAO.findByEmail(CORREO)).thenReturn(Optional.of(cliente));
        when(passwordEncoder.matches(CONTRASENA, HASH)).thenReturn(true);

        Usuario autenticado = authService.autenticar("  CLIENTE@EXAMPLE.COM ", CONTRASENA, "127.0.0.1");

        assertSame(cliente, autenticado);
        verify(usuarioDAO).findByEmail(CORREO);
        verify(passwordEncoder).matches(CONTRASENA, HASH);
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void rechazaCorreoONullConErrorGenericoSinConsultarLaBaseDeDatos() {
        BadCredentialsException errorCorreo = assertThrows(
                BadCredentialsException.class,
                () -> authService.autenticar(null, CONTRASENA, "127.0.0.1"));
        BadCredentialsException errorContrasena = assertThrows(
                BadCredentialsException.class,
                () -> authService.autenticar(CORREO, null, "127.0.0.1"));

        assertEquals("Correo o contraseña incorrectos.", errorCorreo.getMessage());
        assertEquals(errorCorreo.getMessage(), errorContrasena.getMessage());
        verifyNoInteractions(usuarioDAO, passwordEncoder, loginAttemptService, auditoriaService);
    }

    @Test
    void rechazaCorreoInexistenteConElMismoErrorGenerico() {
        when(usuarioDAO.findByEmail(CORREO)).thenReturn(Optional.empty());

        BadCredentialsException error = assertThrows(
                BadCredentialsException.class,
                () -> authService.autenticar(CORREO, CONTRASENA, "127.0.0.1"));

        assertEquals("Correo o contraseña incorrectos.", error.getMessage());
        verify(auditoriaService).registrarIntentoLoginFallido();
    }

    @Test
    void rechazaContrasenaIncorrectaConElMismoErrorGenerico() {
        Cliente cliente = crearCliente(true, false);
        when(usuarioDAO.findByEmail(CORREO)).thenReturn(Optional.of(cliente));
        when(passwordEncoder.matches(CONTRASENA, HASH)).thenReturn(false);

        BadCredentialsException error = assertThrows(
                BadCredentialsException.class,
                () -> authService.autenticar(CORREO, CONTRASENA, "127.0.0.1"));

        assertEquals("Correo o contraseña incorrectos.", error.getMessage());
        verify(loginAttemptService).registrarFallo(CORREO);
        verify(auditoriaService).registrarIntentoLoginFallido();
    }

    @Test
    void registraBloqueoCuandoElLimiteSeAlcanzaAntesDeConsultarLaCuenta() {
        doThrow(new LoginRateLimitException(30))
                .when(loginAttemptService).reservarIntento("127.0.0.1", CORREO);

        assertEquals(30, assertThrows(
                LoginRateLimitException.class,
                () -> authService.autenticar(CORREO, CONTRASENA, "127.0.0.1"))
                .getRetryAfterSeconds());

        verify(auditoriaService).registrarBloqueoLogin(30);
        verifyNoInteractions(usuarioDAO);
    }

    @Test
    void aplicaRetryAfterCuandoSeActivaElBackoffDeCuenta() {
        Cliente cliente = crearCliente(true, false);
        when(usuarioDAO.findByEmail(CORREO)).thenReturn(Optional.of(cliente));
        when(passwordEncoder.matches(CONTRASENA, HASH)).thenReturn(false);
        when(loginAttemptService.registrarFallo(CORREO)).thenReturn(30);

        assertEquals(30, assertThrows(
                LoginRateLimitException.class,
                () -> authService.autenticar(CORREO, CONTRASENA, "127.0.0.1"))
                .getRetryAfterSeconds());
        verify(auditoriaService).registrarIntentoLoginFallido();
        verify(auditoriaService).registrarBloqueoLogin(30);
    }

            @Test
            void actualizaHashBcryptAntiguoTrasAutenticacionCorrecta() {
            Cliente cliente = crearCliente(true, false);
            cliente.setContrasenaHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4)
                .encode(CONTRASENA));
            PasswordEncoder pepperedEncoder = new PepperedBCryptPasswordEncoder(
                "test-pepper-that-is-never-used-outside-tests-123456", 4);
            when(usuarioDAO.findByEmail(CORREO)).thenReturn(Optional.of(cliente));

            AuthService service = new AuthService(usuarioDAO, pepperedEncoder, loginAttemptService, auditoriaService);
            service.autenticar(CORREO, CONTRASENA, "127.0.0.1");

            assertTrue(cliente.getContrasenaHash().startsWith("{bcrypt-pepper-v1}"));
            assertTrue(pepperedEncoder.matches(CONTRASENA, cliente.getContrasenaHash()));
            verify(usuarioDAO).save(cliente);
            }

    @Test
    void rechazaUsuarioBloqueadoConElMismoErrorGenerico() {
        Cliente cliente = crearCliente(true, true);
        when(usuarioDAO.findByEmail(CORREO)).thenReturn(Optional.of(cliente));
        when(passwordEncoder.matches(CONTRASENA, HASH)).thenReturn(true);

        BadCredentialsException error = assertThrows(
                BadCredentialsException.class,
                () -> authService.autenticar(CORREO, CONTRASENA, "127.0.0.1"));

        assertEquals("Correo o contraseña incorrectos.", error.getMessage());
    }

    @Test
    void rechazaUsuarioInactivoConAvisoDeCuentaPendiente() {
        Cliente cliente = crearCliente(false, false);
        when(usuarioDAO.findByEmail(CORREO)).thenReturn(Optional.of(cliente));
        when(passwordEncoder.matches(CONTRASENA, HASH)).thenReturn(true);

        assertThrows(
                AccountPendingException.class,
                () -> authService.autenticar(CORREO, CONTRASENA, "127.0.0.1"));
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
