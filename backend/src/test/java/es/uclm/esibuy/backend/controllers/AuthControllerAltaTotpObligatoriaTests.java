package es.uclm.esibuy.backend.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;

import es.uclm.esibuy.backend.dto.LoginRequest;
import es.uclm.esibuy.backend.dto.LoginResponse;
import es.uclm.esibuy.backend.dto.TwoFactorCodeRequest;
import es.uclm.esibuy.backend.dto.TwoFactorConfirmationResponse;
import es.uclm.esibuy.backend.model.Administrador;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.Usuario;
import es.uclm.esibuy.backend.model.Vendedor;
import es.uclm.esibuy.backend.services.AuthService;
import es.uclm.esibuy.backend.services.SesionService;
import es.uclm.esibuy.backend.services.TotpService;

/*
 * Alta obligatoria del segundo factor (TOTP) para vendedores y administradores durante el login:
 * POST /usuarios/login/2fa/configurar y POST /usuarios/login/2fa/confirmar-alta.
 */
class AuthControllerAltaTotpObligatoriaTests {
    private static final String CONTRASENA = "password";
    private static final String CODIGO = "123456";

    private final AuthService auth = mock(AuthService.class);
    private final TotpService totp = mock(TotpService.class);
    private final SesionService sesiones = mock(SesionService.class);
    private final SecurityContextRepository contextos = mock(SecurityContextRepository.class);
    private final AuthController controller = new AuthController(auth, contextos, sesiones, totp);
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private Vendedor vendedor;

    @BeforeEach
    void preparar() {
        vendedor = new Vendedor();
        vendedor.setId("vendedor-1");
        vendedor.setEmail("vendedor@example.com");
        vendedor.setActivada(true);
        vendedor.setTotpActivado(false);
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private LoginResponse iniciarSesion(Usuario usuario, MockHttpServletRequest request, int estadoEsperado) {
        when(auth.autenticar(eq(usuario.getEmail()), eq(CONTRASENA), anyString())).thenReturn(usuario);
        var respuesta = controller.iniciarSesion(new LoginRequest(usuario.getEmail(), CONTRASENA), request, response);
        assertEquals(estadoEsperado, respuesta.getStatusCode().value());
        return respuesta.getBody();
    }

    private MockHttpServletRequest iniciarAltaPendiente() {
        var request = new MockHttpServletRequest();
        iniciarSesion(vendedor, request, HttpStatus.ACCEPTED.value());
        when(auth.buscarPorCorreo(vendedor.getEmail())).thenReturn(vendedor);
        return request;
    }

    @Test
    void unVendedorSinTotpNoEntraYDebeDarDeAltaElSegundoFactor() {
        var request = new MockHttpServletRequest();

        LoginResponse respuesta = iniciarSesion(vendedor, request, HttpStatus.ACCEPTED.value());

        assertTrue(respuesta.requiresTwoFactor());
        assertTrue(respuesta.requiresTwoFactorSetup());
        assertEquals("VENDEDOR", respuesta.role());
        assertEquals(Boolean.TRUE, request.getSession().getAttribute(AuthController.SESION_2FA_ALTA));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(contextos, sesiones);
    }

    @Test
    void unAdministradorSinTotpTambienDebeDarDeAltaElSegundoFactor() {
        var administrador = new Administrador();
        administrador.setEmail("admin@example.com");
        administrador.setActivada(true);
        var request = new MockHttpServletRequest();

        LoginResponse respuesta = iniciarSesion(administrador, request, HttpStatus.ACCEPTED.value());

        assertTrue(respuesta.requiresTwoFactorSetup());
        assertEquals("ADMIN", respuesta.role());
    }

    @Test
    void unClienteSinTotpIniciaSesionSinAltaObligatoria() {
        var cliente = new Cliente();
        cliente.setEmail("cliente@example.com");
        cliente.setActivada(true);
        var request = new MockHttpServletRequest();

        LoginResponse respuesta = iniciarSesion(cliente, request, HttpStatus.OK.value());

        assertFalse(respuesta.requiresTwoFactorSetup());
        assertEquals(cliente.getEmail(), SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Test
    void configurarSinSesionResponde401() {
        var respuesta = controller.configurarAltaObligatoria(new MockHttpServletRequest());

        assertEquals(HttpStatus.UNAUTHORIZED, respuesta.getStatusCode());
        verifyNoInteractions(totp);
    }

    @Test
    void configurarConLaSesionDeAltaGeneraElQrYGuardaElSecretoPendiente() {
        var request = iniciarAltaPendiente();
        when(totp.generarSecreto()).thenReturn("SECRETO");
        when(totp.crearUriAlta(vendedor.getEmail(), "SECRETO")).thenReturn("otpauth://totp/ESIBuy");
        when(totp.generarQrDataUrl("otpauth://totp/ESIBuy")).thenReturn("data:image/png;base64,QR");

        var respuesta = controller.configurarAltaObligatoria(request);

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        assertEquals("otpauth://totp/ESIBuy", respuesta.getBody().otpauthUri());
        assertEquals("data:image/png;base64,QR", respuesta.getBody().qrDataUrl());
        assertFalse(respuesta.getBody().enabled());
        verify(totp).guardarSecretoPendiente(vendedor, "SECRETO");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void configurarConLaSesionCaducadaResponde401YLimpiaElRetoPendiente() {
        var request = iniciarAltaPendiente();
        request.getSession().setAttribute(AuthController.SESION_2FA_CADUCIDAD, Instant.now().minusSeconds(1));

        var respuesta = controller.configurarAltaObligatoria(request);

        assertEquals(HttpStatus.UNAUTHORIZED, respuesta.getStatusCode());
        assertNull(request.getSession().getAttribute(AuthController.SESION_EMAIL_2FA_PENDIENTE));
        assertNull(request.getSession().getAttribute(AuthController.SESION_2FA_ALTA));
        verify(totp, never()).generarSecreto();
    }

    @Test
    void configurarSinUnaSesionDeAltaResponde401() {
        var request = iniciarAltaPendiente();
        request.getSession().setAttribute(AuthController.SESION_2FA_ALTA, false);

        assertEquals(HttpStatus.UNAUTHORIZED, controller.configurarAltaObligatoria(request).getStatusCode());
        verify(totp, never()).generarSecreto();
    }

    @Test
    void configurarRechazaUnaCuentaBloqueadaTrasLaContrasena() {
        var request = iniciarAltaPendiente();
        vendedor.setBloqueada(true);

        assertEquals(HttpStatus.UNAUTHORIZED, controller.configurarAltaObligatoria(request).getStatusCode());
        assertNull(request.getSession().getAttribute(AuthController.SESION_EMAIL_2FA_PENDIENTE));
        verify(totp, never()).generarSecreto();
    }

    @Test
    void configurarRechazaUnaCuentaQueYaTieneElSegundoFactorActivo() {
        var request = iniciarAltaPendiente();
        vendedor.setTotpActivado(true);

        assertEquals(HttpStatus.UNAUTHORIZED, controller.configurarAltaObligatoria(request).getStatusCode());
        verify(totp, never()).generarSecreto();
    }

    @Test
    void confirmarSinSesionResponde401() {
        var respuesta = controller.confirmarAltaObligatoria(
                new TwoFactorCodeRequest(CODIGO), new MockHttpServletRequest(), response);

        assertEquals(HttpStatus.UNAUTHORIZED, respuesta.getStatusCode());
        verifyNoInteractions(totp, sesiones);
    }

    @Test
    void confirmarConUnCodigoValidoCompletaElLoginYDevuelveLosCodigosDeRespaldo() {
        var request = iniciarAltaPendiente();
        when(totp.confirmarConfiguracion(vendedor, CODIGO))
                .thenReturn(new TwoFactorConfirmationResponse(true, List.of("respaldo-1", "respaldo-2")));

        var respuesta = controller.confirmarAltaObligatoria(new TwoFactorCodeRequest(CODIGO), request, response);

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        assertEquals(vendedor.getEmail(), respuesta.getBody().login().email());
        assertEquals("VENDEDOR", respuesta.getBody().login().role());
        assertEquals(List.of("respaldo-1", "respaldo-2"), respuesta.getBody().backupCodes());
        assertEquals(vendedor.getEmail(), SecurityContextHolder.getContext().getAuthentication().getName());
        verify(sesiones).registrarSesion(request.getSession().getId(), vendedor.getEmail());
        assertNull(request.getSession().getAttribute(AuthController.SESION_EMAIL_2FA_PENDIENTE));
        assertNull(request.getSession().getAttribute(AuthController.SESION_2FA_ALTA));
    }

    @Test
    void confirmarConUnCodigoInvalidoNoIniciaSesionYConservaElRetoParaReintentar() {
        var request = iniciarAltaPendiente();
        when(totp.confirmarConfiguracion(vendedor, CODIGO))
                .thenThrow(new IllegalArgumentException("El código TOTP no es válido o ya se ha utilizado."));

        assertThrows(IllegalArgumentException.class,
                () -> controller.confirmarAltaObligatoria(new TwoFactorCodeRequest(CODIGO), request, response));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(vendedor.getEmail(), request.getSession().getAttribute(AuthController.SESION_EMAIL_2FA_PENDIENTE));
        verifyNoInteractions(sesiones, contextos);
    }

    @Test
    void confirmarConLaSesionCaducadaResponde401SinConfirmarNada() {
        var request = iniciarAltaPendiente();
        request.getSession().setAttribute(AuthController.SESION_2FA_CADUCIDAD, Instant.now().minusSeconds(1));

        var respuesta = controller.confirmarAltaObligatoria(new TwoFactorCodeRequest(CODIGO), request, response);

        assertEquals(HttpStatus.UNAUTHORIZED, respuesta.getStatusCode());
        verify(totp, never()).confirmarConfiguracion(vendedor, CODIGO);
        verifyNoInteractions(sesiones);
    }
}
