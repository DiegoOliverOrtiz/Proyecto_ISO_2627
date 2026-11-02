package es.uclm.esibuy.backend.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;

import es.uclm.esibuy.backend.dto.LoginRequest;
import es.uclm.esibuy.backend.dto.TwoFactorCodeRequest;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.services.AuthService;
import es.uclm.esibuy.backend.services.SesionService;
import es.uclm.esibuy.backend.services.TotpService;

class AuthControllerTwoFactorTests {
    private final AuthService auth = mock(AuthService.class);
    private final TotpService totp = mock(TotpService.class);
    private final SesionService sessions = mock(SesionService.class);
    private final SecurityContextRepository contexts = mock(SecurityContextRepository.class);
    private final AuthController controller = new AuthController(auth, contexts, sessions, totp);
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private Cliente user;

    @BeforeEach
    void preparar() {
        user = new Cliente();
        user.setId("usuario-1");
        user.setEmail("cliente@example.com");
        user.setActivada(true);
        user.setTotpActivado(true);
        user.setSecretoTotpCifrado("encrypted-secret");
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest iniciarPendiente() {
        var request = new MockHttpServletRequest();
        when(auth.autenticar(user.getEmail(), "password")).thenReturn(user);
        assertEquals(202, controller.iniciarSesion(new LoginRequest(user.getEmail(), "password"), request, response)
                .getStatusCode().value());
        return request;
    }

    @Test
    void noSePuedeConstruirElLoginSinElServicioDeSegundoFactor() {
        assertThrows(NullPointerException.class, () -> new AuthController(auth, contexts, sessions, null));
    }

    @Test
    void laContrasenaCorrectaNoConcedeAccesoAntesDelSegundoFactor() {
        var request = iniciarPendiente();
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(user.getEmail(), request.getSession().getAttribute(AuthController.SESION_EMAIL_2FA_PENDIENTE));
        assertNotNull(request.getSession().getAttribute(AuthController.SESION_2FA_CADUCIDAD));
        verifyNoInteractions(contexts, sessions);
    }

    @Test
    void laCaducidadEsAbsolutaAunqueLaSesionSigaActiva() {
        var request = iniciarPendiente();
        request.getSession().setAttribute(AuthController.SESION_2FA_CADUCIDAD, Instant.now().minusSeconds(1));
        assertEquals(410, controller.verificarSegundoFactor(new TwoFactorCodeRequest("123456"), request, response)
                .getStatusCode().value());
        assertNull(request.getSession().getAttribute(AuthController.SESION_EMAIL_2FA_PENDIENTE));
        verifyNoInteractions(totp, contexts, sessions);
    }

    @Test
    void rechazaUnaCuentaBloqueadaDespuesDeIntroducirLaContrasena() {
        var request = iniciarPendiente();
        user.setBloqueada(true);
        when(auth.buscarPorCorreo(user.getEmail())).thenReturn(user);
        assertEquals(401, controller.verificarSegundoFactor(new TwoFactorCodeRequest("123456"), request, response)
                .getStatusCode().value());
        verifyNoInteractions(totp, contexts, sessions);
    }

    @Test
    void rechazaUnaCuentaDesactivadaDespuesDelPrimerPaso() {
        var request = iniciarPendiente();
        user.setActivada(false);
        when(auth.buscarPorCorreo(user.getEmail())).thenReturn(user);
        assertEquals(401, controller.verificarSegundoFactor(new TwoFactorCodeRequest("123456"), request, response)
                .getStatusCode().value());
        verifyNoInteractions(totp, contexts, sessions);
    }

    @Test
    void unCambioDeConfiguracionInvalidaElRetoPendiente() {
        var request = iniciarPendiente();
        user.setSecretoTotpCifrado("new-encrypted-secret");
        when(auth.buscarPorCorreo(user.getEmail())).thenReturn(user);
        assertEquals(401, controller.verificarSegundoFactor(new TwoFactorCodeRequest("123456"), request, response)
                .getStatusCode().value());
        verifyNoInteractions(totp, contexts, sessions);
    }

    @Test
    void soloRegistraLaSesionDespuesDeValidarYNoPermiteRepetirElReto() {
        var request = iniciarPendiente();
        when(auth.buscarPorCorreo(user.getEmail())).thenReturn(user);
        when(totp.verificarSegundoFactor(user, "123456")).thenReturn(true);
        assertEquals(200, controller.verificarSegundoFactor(new TwoFactorCodeRequest("123456"), request, response)
                .getStatusCode().value());
        assertEquals(user.getEmail(), SecurityContextHolder.getContext().getAuthentication().getName());
        verify(sessions).registrarSesion(request.getSession().getId(), user.getEmail());
        assertEquals(410, controller.verificarSegundoFactor(new TwoFactorCodeRequest("123456"), request, response)
                .getStatusCode().value());
        verify(totp, times(1)).verificarSegundoFactor(user, "123456");
    }

    @Test
    void iniciarOtroLoginNoConservaLaAutenticacionAnteriorDuranteElSegundoFactor() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("otro@example.com", null, List.of()));
        iniciarPendiente();
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(contexts, sessions);
    }

    @Test
    void unCodigoIncorrectoNoCreaUnaSesionAutenticada() {
        var request = iniciarPendiente();
        when(auth.buscarPorCorreo(user.getEmail())).thenReturn(user);
        assertEquals(401, controller.verificarSegundoFactor(new TwoFactorCodeRequest("123456"), request, response)
                .getStatusCode().value());
        verifyNoInteractions(contexts, sessions);
    }
}
