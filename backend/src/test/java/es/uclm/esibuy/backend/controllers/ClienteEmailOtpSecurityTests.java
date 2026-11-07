package es.uclm.esibuy.backend.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;
import es.uclm.esibuy.backend.config.SecurityConfiguration;
import es.uclm.esibuy.backend.dao.UsuarioDAO;
import es.uclm.esibuy.backend.dto.TwoFactorConfirmationResponse;
import es.uclm.esibuy.backend.exceptions.EmailOtpRateLimitException;
import es.uclm.esibuy.backend.model.*;
import es.uclm.esibuy.backend.services.*;

@WebMvcTest({AuthController.class, PerfilController.class, SpaController.class})
@Import({SecurityConfiguration.class, PerfilService.class, SesionService.class})
class ClienteEmailOtpSecurityTests {
    private static final String EMAIL = "cliente@example.com";
    @Autowired MockMvc mvc;
    @MockitoBean AuthService auth;
    @MockitoBean TotpService totp;
    @MockitoBean EmailOtpService correo;
    @MockitoBean UsuarioDAO usuarios;
    @MockitoBean MongoTemplate mongo;
    @MockitoBean AdministradorService administradores;
    private Cliente cliente;

    @BeforeEach
    void preparar() {
        cliente = new Cliente();
        cliente.setId("cliente-1");
        cliente.setEmail(EMAIL);
        cliente.setActivada(true);
        cliente.setSecretoTotpCifrado("cifrado-prueba");
        when(auth.autenticar(eq(EMAIL), anyString(), anyString())).thenAnswer(i -> cliente);
        when(auth.buscarPorCorreo(EMAIL)).thenAnswer(i -> cliente);
        when(usuarios.findByEmail(EMAIL)).thenAnswer(i -> Optional.of(cliente));
        when(totp.buscarUsuario(EMAIL)).thenAnswer(i -> cliente);
    }

    private MockHttpSession login(int status) throws Exception {
        return (MockHttpSession) mvc.perform(post("/usuarios/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"password\":\"ClaveSegura9!\"}"))
                .andExpect(status().is(status)).andReturn().getRequest().getSession(false);
    }

    private void protegido(MockHttpSession session, boolean permitido) throws Exception {
        mvc.perform(get("/usuarios/perfil").session(session))
                .andExpect(status().is(permitido ? 200 : 403));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void clienteSoloAccedeTrasCompletarTodosSusFactores(int factores) throws Exception {
        cliente.setTotpActivado(factores >= 2);
        cliente.setEmailOtpActivado(factores == 3);
        MockHttpSession session = login(factores == 1 ? 200 : 202);
        protegido(session, factores == 1);
        if (factores == 1) {
            verifyNoInteractions(totp, correo);
            return;
        }
        when(totp.verificarSegundoFactor(cliente, "123456")).thenReturn(true);
        mvc.perform(post("/usuarios/login/2fa").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().is(factores == 2 ? 200 : 202))
                .andExpect(jsonPath("$.requiresEmailVerification").value(factores == 3));
        protegido(session, factores == 2);
        if (factores == 2) {
            verifyNoInteractions(correo);
            return;
        }
        when(correo.verificar(session, EMAIL, "654321")).thenReturn(EmailOtpService.ResultadoVerificacion.VALIDO);
        mvc.perform(post("/usuarios/login/3fa/verificar").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"654321\"}"))
                .andExpect(status().isOk());
        protegido(session, true);
    }

    @Test
    void noPuedeVerificarCorreoSinSesionPendienteNiSinCsrf() throws Exception {
        cliente.setTotpActivado(true);
        cliente.setEmailOtpActivado(true);
        mvc.perform(post("/usuarios/login/3fa/verificar").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isUnauthorized());
        MockHttpSession session = login(202);
        mvc.perform(post("/usuarios/login/3fa/verificar").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isGone());
        mvc.perform(post("/usuarios/login/3fa/verificar").session(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isForbidden());
        protegido(session, false);
        verify(correo, never()).verificar(any(), anyString(), anyString());
    }

    @Test
    void preferenciaExigeSesionCsrfYBooleano() throws Exception {
        mvc.perform(put("/usuarios/3fa/preferencia").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"activado\":true}")).andExpect(status().isForbidden());
        mvc.perform(put("/usuarios/3fa/preferencia").with(user(EMAIL).roles("CLIENTE"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"activado\":true}"))
                .andExpect(status().isForbidden());
        for (String body : List.of("{}", "{\"activado\":null}")) {
            mvc.perform(put("/usuarios/3fa/preferencia").with(user(EMAIL).roles("CLIENTE")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        verify(usuarios, never()).save(any());
    }

    private void preferencia(boolean activado, int status) throws Exception {
        mvc.perform(put("/usuarios/3fa/preferencia").with(user(EMAIL).roles("CLIENTE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"activado\":" + activado + "}"))
                .andExpect(status().is(status));
    }

    @Test
    void preferenciaSeGuardaYSeConsultaTrasOtraSesionYBloqueaDesactivarTotp() throws Exception {
        assertFalse(cliente.isEmailOtpActivado());
        preferencia(true, 409);
        verify(usuarios, never()).save(any());
        cliente.setTotpActivado(true);
        preferencia(true, 204);
        verify(usuarios).save(cliente);
        mvc.perform(get("/usuarios/perfil").with(user(EMAIL).roles("CLIENTE")))
                .andExpect(jsonPath("$.emailOtpActivado").value(true));
        mvc.perform(delete("/usuarios/2fa").with(user(EMAIL).roles("CLIENTE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isConflict());
        verify(totp, never()).desactivar(any(), anyString());
        preferencia(false, 204);
        assertFalse(cliente.isEmailOtpActivado());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "VENDEDOR"})
    void rolesObligadosNoPuedenDesactivarNingunFactor(String rol) throws Exception {
        Usuario usuario = rol.equals("ADMIN") ? new Administrador() : new Vendedor();
        when(usuarios.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(totp.buscarUsuario(EMAIL)).thenReturn(usuario);
        preferencia(false, 403);
        mvc.perform(delete("/usuarios/2fa").with(user(EMAIL).roles(rol)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isForbidden());
        verify(usuarios, never()).save(any());
        verify(totp, never()).desactivar(any(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "VENDEDOR"})
    void altaObligatoriaConRespaldoSiguePendienteDelCorreo(String rol) throws Exception {
        Usuario usuario = rol.equals("ADMIN") ? new Administrador() : new Vendedor();
        usuario.setEmail(EMAIL);
        usuario.setActivada(true);
        when(auth.autenticar(eq(EMAIL), anyString(), anyString())).thenReturn(usuario);
        when(auth.buscarPorCorreo(EMAIL)).thenReturn(usuario);
        when(usuarios.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        MockHttpSession session = login(202);
        when(totp.confirmarConfiguracion(usuario, "123456")).thenAnswer(i -> {
            usuario.setTotpActivado(true);
            usuario.setSecretoTotpCifrado("cifrado-prueba");
            return new TwoFactorConfirmationResponse(true, List.of("RESPALDO"));
        });
        mvc.perform(post("/usuarios/login/2fa/confirmar-alta").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.backupCodes[0]").value("RESPALDO"))
                .andExpect(jsonPath("$.login.requiresEmailVerification").value(true));
        protegido(session, false);
        when(correo.verificar(session, EMAIL, "654321")).thenReturn(EmailOtpService.ResultadoVerificacion.VALIDO);
        mvc.perform(post("/usuarios/login/3fa/verificar").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"654321\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value(rol));
        protegido(session, true);
    }

    @Test
    void brevoFallaYReintentosRespetanLimitesSinAutenticar() throws Exception {
        cliente.setTotpActivado(true);
        cliente.setEmailOtpActivado(true);
        MockHttpSession session = login(202);
        when(totp.verificarSegundoFactor(cliente, "123456")).thenReturn(true);
        doThrow(new ResourceAccessException("Fallo simulado")).when(correo).iniciar(session, EMAIL);
        mvc.perform(post("/usuarios/login/2fa").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.emailVerificationEmailSent").value(false));
        protegido(session, false);
        when(correo.reenviar(session, EMAIL)).thenThrow(new EmailOtpRateLimitException(60));
        mvc.perform(post("/usuarios/login/3fa/reenviar").session(session).with(csrf()))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"));
        protegido(session, false);
        when(correo.verificar(session, EMAIL, "000000")).thenReturn(EmailOtpService.ResultadoVerificacion.CADUCADO);
        mvc.perform(post("/usuarios/login/3fa/verificar").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"000000\"}"))
                .andExpect(status().isGone());
        protegido(session, false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVALIDO", "BLOQUEADO", "CADUCADO"})
    void correoRechazadoNuncaAutenticaYNoPermiteReabrirRetoBloqueado(String resultado) throws Exception {
        cliente.setTotpActivado(true);
        cliente.setEmailOtpActivado(true);
        MockHttpSession session = login(202);
        when(totp.verificarSegundoFactor(cliente, "123456")).thenReturn(true);
        mvc.perform(post("/usuarios/login/2fa").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isAccepted());
        var estado = EmailOtpService.ResultadoVerificacion.valueOf(resultado);
        when(correo.verificar(session, EMAIL, "000000")).thenReturn(estado);
        int codigo = switch (estado) {
            case INVALIDO -> 401;
            case BLOQUEADO -> 429;
            default -> 410;
        };
        mvc.perform(post("/usuarios/login/3fa/verificar").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"000000\"}"))
                .andExpect(status().is(codigo));
        protegido(session, false);
        if (estado != EmailOtpService.ResultadoVerificacion.INVALIDO) {
            mvc.perform(post("/usuarios/login/3fa/reenviar").session(session).with(csrf()))
                    .andExpect(status().isGone());
            verify(correo, never()).reenviar(any(), anyString());
        }
    }

    @Test
    void falloDeReenvioNuncaAutenticaYPuedeReintentarse() throws Exception {
        cliente.setTotpActivado(true);
        cliente.setEmailOtpActivado(true);
        MockHttpSession session = login(202);
        when(totp.verificarSegundoFactor(cliente, "123456")).thenReturn(true);
        mvc.perform(post("/usuarios/login/2fa").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isAccepted());
        when(correo.reenviar(session, EMAIL))
                .thenThrow(new ResourceAccessException("Brevo no disponible"))
                .thenReturn(new EmailOtpService.ResultadoReenvio(EmailOtpService.EstadoReenvio.ENVIADO, 0));
        mvc.perform(post("/usuarios/login/3fa/reenviar").session(session).with(csrf()))
                .andExpect(status().isServiceUnavailable());
        protegido(session, false);
        mvc.perform(post("/usuarios/login/3fa/reenviar").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        protegido(session, false);
    }

    @Test
    void preferenciaSobreviveAlLogoutYSeAplicaEnElSiguienteLogin() throws Exception {
        MockHttpSession session = login(200);
        cliente.setTotpActivado(true);
        mvc.perform(put("/usuarios/3fa/preferencia").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"activado\":true}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/usuarios/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        assertTrue(session.isInvalid());
        MockHttpSession siguiente = login(202);
        when(totp.verificarSegundoFactor(cliente, "123456")).thenReturn(true);
        mvc.perform(post("/usuarios/login/2fa").session(siguiente).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.requiresEmailVerification").value(true));
        protegido(siguiente, false);
        verify(usuarios).save(cliente);
    }

    @Test
    void spaPublicaNoAbreLasApis() throws Exception {
        for (String ruta : List.of("/cliente/pedidos", "/admin/productos")) {
            mvc.perform(get(ruta)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        }
        for (String ruta : List.of("/cliente/pedidos/activos", "/administrador/productos")) {
            mvc.perform(get(ruta)).andExpect(status().isForbidden());
        }
    }
}
