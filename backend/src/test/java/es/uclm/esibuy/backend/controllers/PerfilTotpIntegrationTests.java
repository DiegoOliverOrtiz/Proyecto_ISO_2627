package es.uclm.esibuy.backend.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import com.mongodb.client.result.UpdateResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import es.uclm.esibuy.backend.dao.UsuarioDAO;
import es.uclm.esibuy.backend.dao.TotpSecurityStore;
import es.uclm.esibuy.backend.model.Usuario;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.services.PerfilService;
import es.uclm.esibuy.backend.services.TotpCryptoService;
import es.uclm.esibuy.backend.services.TotpService;

class PerfilTotpIntegrationTests {

    private static final String EMAIL = "cliente@example.com";
    // Vector público de pruebas TOTP; no pertenece a ninguna cuenta real.
    private static final String SECRETO_RFC = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"; // gitleaks:allow

    private Cliente usuario;
    private TotpService totpService;
    private MockMvc mockMvc;
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(EMAIL, null, List.of());

    @BeforeEach
    void preparar() {
        UsuarioDAO usuarioDAO = mock(UsuarioDAO.class);
        usuario = new Cliente();
        usuario.setId("usuario-1");
        usuario.setActivada(true);
        usuario.setEmail(EMAIL);
        when(usuarioDAO.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        TotpCryptoService cryptoService = new TotpCryptoService(java.util.Base64.getEncoder().encodeToString(new byte[32]));
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Usuario.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        TotpSecurityStore securityStore = mock(TotpSecurityStore.class);
        when(securityStore.reservarIntento(eq("usuario-1"), any(Instant.class))).thenReturn(true);
        when(securityStore.consumirIntervalo(eq("usuario-1"), eq(SECRETO_RFC), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(true);
        totpService = spy(new TotpService(usuarioDAO, cryptoService, new BCryptPasswordEncoder(4), securityStore, mongoTemplate));
        when(totpService.generarSecreto()).thenReturn(SECRETO_RFC);
        PerfilController controller = new PerfilController(mock(PerfilService.class), totpService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new RegistrationExceptionHandler()).build();
    }

    @Test
    void configuraYActivaConUnCodigoOficialYGuardaLosRespaldoComoHashes() throws Exception {
        configurar();
        Instant instanteRfc = Instant.ofEpochSecond(59);

        try (var tiempo = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            tiempo.when(Instant::now).thenReturn(instanteRfc);
            mockMvc.perform(post("/usuarios/2fa/confirmar").principal(authentication)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"287082\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(true))
                    .andExpect(jsonPath("$.backupCodes.length()").value(8))
                    .andExpect(jsonPath("$.backupCodes[0]").value(org.hamcrest.Matchers.matchesPattern("[A-Z2-7]{16}")));
        }

        assertTrue(usuario.isTotpActivado());
        assertNull(usuario.getSecretoTotpPendienteCifrado());
        assertEquals(SECRETO_RFC, totpService.descifrar(usuario.getSecretoTotpCifrado()));
        assertEquals(8, usuario.getCodigosRespaldoTotp().size());
        assertTrue(usuario.getCodigosRespaldoTotp().stream().allMatch(hash -> hash.startsWith("$2a$")));
    }

    @Test
    void rechazaUnCodigoIncorrectoSinActivarNiBorrarLaConfiguracionPendiente() throws Exception {
        configurar();
        Instant instanteRfc = Instant.ofEpochSecond(59);

        try (var tiempo = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            tiempo.when(Instant::now).thenReturn(instanteRfc);
            mockMvc.perform(post("/usuarios/2fa/confirmar").principal(authentication)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"000000\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("El código TOTP no es válido o ya se ha utilizado."));
        }

        assertFalse(usuario.isTotpActivado());
        assertNull(usuario.getSecretoTotpCifrado());
        assertEquals(SECRETO_RFC, totpService.obtenerSecretoPendiente(usuario));
        assertTrue(usuario.getCodigosRespaldoTotp().isEmpty());
    }

    private void configurar() throws Exception {
        mockMvc.perform(post("/usuarios/2fa/configurar").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.qrDataUrl").value(org.hamcrest.Matchers.startsWith("data:image/png;base64,")))
                .andExpect(jsonPath("$.otpauthUri").value(totpService.crearUriAlta(EMAIL, SECRETO_RFC)));
        assertEquals(SECRETO_RFC, totpService.obtenerSecretoPendiente(usuario));
    }
}
