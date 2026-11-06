package es.uclm.esibuy.backend.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.SimpleTransactionStatus;

import es.uclm.esibuy.backend.dao.TokenRecuperacionDAO;
import es.uclm.esibuy.backend.dao.UsuarioDAO;
import es.uclm.esibuy.backend.exceptions.InvalidRegistrationException;
import es.uclm.esibuy.backend.exceptions.TokenRecuperacionInvalidoException;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.TokenRecuperacion;

@ExtendWith(MockitoExtension.class)
class RecuperacionContrasenaServiceTests {

    private static final String EMAIL = "cliente@example.com";
    private static final String TOKEN = "token-valido-para-recuperacion";
    private static final String NUEVA_CONTRASENA = "NuevaClaveSegura9!";

    @Mock
    private UsuarioDAO usuarioDAO;
    @Mock
    private TokenRecuperacionDAO tokenDAO;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PasswordPolicy passwordPolicy;
    @Mock
    private SesionService sesionService;
    @Mock
    private BrevoEmailService brevoEmailService;
    @Mock
    private MongoTransactionManager transactionManager;

    private RecuperacionContrasenaService servicio;
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        servicio = new RecuperacionContrasenaService(
                usuarioDAO,
                tokenDAO,
                passwordEncoder,
                passwordPolicy,
                sesionService,
                brevoEmailService,
                transactionManager,
                "https://esibuy.example/");
        lenient().when(transactionManager.getTransaction(any()))
                .thenReturn(new SimpleTransactionStatus());
        cliente = new Cliente();
        cliente.setId("usuario-1");
        cliente.setEmail(EMAIL);
    }

    @Test
    void solicitarNormalizaCorreoYNoDaIndicacionDeSiNoExiste() {
        when(usuarioDAO.findByEmail(EMAIL)).thenReturn(Optional.empty());

        servicio.solicitar("  CLIENTE@EXAMPLE.COM ");

        verify(usuarioDAO).findByEmail(EMAIL);
        verifyNoInteractions(tokenDAO, brevoEmailService);
    }

    @Test
    void solicitarGuardaSoloHashConCaducidadDeTreintaMinutosYEnviaEnlace() throws Exception {
        when(usuarioDAO.findByEmail(EMAIL)).thenReturn(Optional.of(cliente));
        ArgumentCaptor<TokenRecuperacion> tokenCaptor =
                ArgumentCaptor.forClass(TokenRecuperacion.class);
        Instant antes = Instant.now();

        servicio.solicitar(EMAIL);

        Instant despues = Instant.now();
        verify(tokenDAO).deleteByUsuarioId("usuario-1");
        verify(tokenDAO).save(tokenCaptor.capture());
        TokenRecuperacion guardado = tokenCaptor.getValue();
        assertEquals("usuario-1", guardado.getUsuarioId());
        assertTrue(guardado.getCaducidad().isAfter(antes.plusSeconds(29 * 60)));
        assertTrue(guardado.getCaducidad().isBefore(despues.plusSeconds(31 * 60)));
        assertEquals(64, guardado.getHashToken().length());
        assertTrue(guardado.getHashToken().matches("[0-9a-f]{64}"));

        ArgumentCaptor<String> enlaceCaptor = ArgumentCaptor.forClass(String.class);
        verify(brevoEmailService).enviarRecuperacionEnSegundoPlano(eq(EMAIL), enlaceCaptor.capture());
        String enlace = enlaceCaptor.getValue();
        assertTrue(enlace.startsWith("https://esibuy.example/recuperar-contrasena?token="));
        String tokenPlano = enlace.substring(enlace.lastIndexOf('=') + 1);
        assertTrue(tokenPlano.matches("[A-Za-z0-9_-]{43}"));
        assertEquals(hash(tokenPlano), guardado.getHashToken());
    }

    @Test
    void completarConTokenValidoActualizaHashYRevocaSesiones() {
        TokenRecuperacion tokenGuardado = tokenValido();
        when(tokenDAO.findByHashToken(hash(TOKEN))).thenReturn(Optional.of(tokenGuardado));
        when(usuarioDAO.findById("usuario-1")).thenReturn(Optional.of(cliente));
        when(passwordEncoder.encode(NUEVA_CONTRASENA)).thenReturn("hash-nuevo");
        when(tokenDAO.consumirSiValido(eq("token-doc-1"), any(Instant.class))).thenReturn(1L);

        servicio.completar(TOKEN, NUEVA_CONTRASENA);

        verify(passwordPolicy).validate(NUEVA_CONTRASENA);
        verify(passwordPolicy).matchesRecentPassword(
                eq(NUEVA_CONTRASENA), any(), eq(passwordEncoder));
        verify(tokenDAO).consumirSiValido(eq("token-doc-1"), any(Instant.class));
        verify(usuarioDAO).save(cliente);
        assertEquals("hash-nuevo", cliente.getContrasenaHash());
        assertEquals("hash-nuevo", cliente.getHistorialContrasenasHash().getFirst());
        verify(sesionService).cerrarSesiones(EMAIL);
    }

    @Test
    void rechazaTokenManipuladoSinConsultarUsuarioNiModificarDatos() {
        when(tokenDAO.findByHashToken(anyString())).thenReturn(Optional.empty());

        assertThrows(TokenRecuperacionInvalidoException.class,
                () -> servicio.completar("token-manipulado", NUEVA_CONTRASENA));

        verifyNoInteractions(usuarioDAO, passwordEncoder, passwordPolicy, sesionService);
        verify(tokenDAO, never()).consumirSiValido(anyString(), any(Instant.class));
    }

    @Test
    void rechazaTokenCaducadoOSiYaFueConsumido() {
        TokenRecuperacion caducado = new TokenRecuperacion(
                hash(TOKEN), "usuario-1", Instant.now().minusSeconds(1));
        ReflectionTestUtils.setField(caducado, "id", "token-caducado");
        when(tokenDAO.findByHashToken(hash(TOKEN))).thenReturn(Optional.of(caducado));

        assertThrows(TokenRecuperacionInvalidoException.class,
                () -> servicio.completar(TOKEN, NUEVA_CONTRASENA));
        verifyNoInteractions(usuarioDAO, passwordEncoder, passwordPolicy, sesionService);
        verify(tokenDAO, never()).consumirSiValido(anyString(), any(Instant.class));

        TokenRecuperacion consumido = tokenValido();
        ReflectionTestUtils.setField(consumido, "consumido", true);
        when(tokenDAO.findByHashToken(hash(TOKEN))).thenReturn(Optional.of(consumido));

        assertThrows(TokenRecuperacionInvalidoException.class,
                () -> servicio.completar(TOKEN, NUEVA_CONTRASENA));
        verifyNoInteractions(usuarioDAO, passwordEncoder, passwordPolicy, sesionService);
        verify(tokenDAO, never()).consumirSiValido(anyString(), any(Instant.class));
    }

    @Test
    void contraseñaNoValidaNoConsumeTokenNiCambiaUsuario() {
        TokenRecuperacion tokenGuardado = tokenValido();
        when(tokenDAO.findByHashToken(hash(TOKEN))).thenReturn(Optional.of(tokenGuardado));
        when(usuarioDAO.findById("usuario-1")).thenReturn(Optional.of(cliente));
        org.mockito.Mockito.doThrow(new InvalidRegistrationException(
                "contrasena", "La contraseña no cumple los requisitos."))
                .when(passwordPolicy).validate(NUEVA_CONTRASENA);

        assertThrows(InvalidRegistrationException.class,
                () -> servicio.completar(TOKEN, NUEVA_CONTRASENA));

        verify(tokenDAO, never()).consumirSiValido(anyString(), any(Instant.class));
        verify(usuarioDAO, never()).save(any(Cliente.class));
        verifyNoInteractions(sesionService);
    }

    @Test
    void noPermiteReutilizarUnaContrasenaReciente() {
        TokenRecuperacion tokenGuardado = tokenValido();
        cliente.registrarContrasenaHash("hash-reciente");
        when(tokenDAO.findByHashToken(hash(TOKEN))).thenReturn(Optional.of(tokenGuardado));
        when(usuarioDAO.findById("usuario-1")).thenReturn(Optional.of(cliente));
        when(passwordPolicy.matchesRecentPassword(
                eq(NUEVA_CONTRASENA), any(), eq(passwordEncoder))).thenReturn(true);

        assertThrows(InvalidRegistrationException.class,
                () -> servicio.completar(TOKEN, NUEVA_CONTRASENA));

        verify(tokenDAO, never()).consumirSiValido(anyString(), any(Instant.class));
        verify(usuarioDAO, never()).save(any(Cliente.class));
        verifyNoInteractions(sesionService);
    }

    @Test
    void unSegundoConsumoConcurrenteNoCambiaLaContrasenaNiLasSesiones() {
        TokenRecuperacion tokenGuardado = tokenValido();
        when(tokenDAO.findByHashToken(hash(TOKEN))).thenReturn(Optional.of(tokenGuardado));
        when(usuarioDAO.findById("usuario-1")).thenReturn(Optional.of(cliente));
        when(tokenDAO.consumirSiValido(eq("token-doc-1"), any(Instant.class))).thenReturn(0L);

        assertThrows(TokenRecuperacionInvalidoException.class,
                () -> servicio.completar(TOKEN, NUEVA_CONTRASENA));

        verify(usuarioDAO, never()).save(any(Cliente.class));
        verifyNoInteractions(sesionService);
    }

    @Test
    void falloAlGuardarUsuarioRevierteElConsumoDelToken() {
        TokenRecuperacion tokenGuardado = tokenValido();
        when(tokenDAO.findByHashToken(hash(TOKEN))).thenReturn(Optional.of(tokenGuardado));
        when(usuarioDAO.findById("usuario-1")).thenReturn(Optional.of(cliente));
        when(tokenDAO.consumirSiValido(eq("token-doc-1"), any(Instant.class))).thenReturn(1L);
        when(passwordEncoder.encode(NUEVA_CONTRASENA)).thenReturn("hash-nuevo");
        when(usuarioDAO.save(cliente)).thenThrow(new IllegalStateException("Fallo de persistencia"));

        assertThrows(IllegalStateException.class,
                () -> servicio.completar(TOKEN, NUEVA_CONTRASENA));

        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
        verifyNoInteractions(sesionService);
    }

    private TokenRecuperacion tokenValido() {
        TokenRecuperacion token = new TokenRecuperacion(
                hash(TOKEN), "usuario-1", Instant.now().plusSeconds(30 * 60));
        ReflectionTestUtils.setField(token, "id", "token-doc-1");
        return token;
    }

    private static String hash(String valor) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
