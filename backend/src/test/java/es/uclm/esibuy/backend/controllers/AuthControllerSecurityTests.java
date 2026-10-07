package es.uclm.esibuy.backend.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import es.uclm.esibuy.backend.config.SecurityConfiguration;
import es.uclm.esibuy.backend.dto.ConsultarProductosResponse;
import es.uclm.esibuy.backend.dto.RegistroUsuarioResponse;
import es.uclm.esibuy.backend.exceptions.AccountPendingException;
import es.uclm.esibuy.backend.model.Administrador;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.Vendedor;
import es.uclm.esibuy.backend.services.AdministradorService;
import es.uclm.esibuy.backend.services.AuthService;
import es.uclm.esibuy.backend.services.ProductoService;
import es.uclm.esibuy.backend.services.VendedorService;
import es.uclm.esibuy.backend.services.SesionService;

@WebMvcTest(controllers = {
        AuthController.class,
        AdministradorController.class,
        VendedorController.class
})
@Import(SecurityConfiguration.class)
class AuthControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AdministradorService administradorService;

    @MockitoBean
    private VendedorService vendedorService;

    @MockitoBean
    private ProductoService productoService;

    @MockitoBean
    private SesionService sesionService;

    @Test
    void loginGuardaLaSesionYPermiteRecuperarla() throws Exception {
        Cliente cliente = crearCliente(true);
        when(authService.autenticar("cliente@example.com", "ClaveSegura9!"))
                .thenReturn(cliente);

        MvcResult resultadoLogin = mockMvc.perform(post("/usuarios/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"cliente@example.com","password":"ClaveSegura9!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("cliente@example.com"))
                .andExpect(jsonPath("$.role").value("CLIENTE"))
                .andReturn();

        MockHttpSession sesion = (MockHttpSession) resultadoLogin.getRequest().getSession(false);
        assertNotNull(sesion);
        assertEquals(30 * 60, sesion.getMaxInactiveInterval());

        mockMvc.perform(get("/usuarios/sesion-actual").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("cliente@example.com"))
                .andExpect(jsonPath("$.role").value("CLIENTE"));
    }

    @Test
    void loginDevuelveElRolAdminParaAdministradores() throws Exception {
        Administrador administrador = new Administrador();
        administrador.setEmail("admin@example.com");
        when(authService.autenticar("admin@example.com", "ClaveSegura9!"))
                .thenReturn(administrador);

        mockMvc.perform(post("/usuarios/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@example.com","password":"ClaveSegura9!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void loginDevuelveElRolVendedorParaVendedores() throws Exception {
        Vendedor vendedor = new Vendedor();
        vendedor.setEmail("vendedor@example.com");
        when(authService.autenticar("vendedor@example.com", "ClaveSegura9!"))
                .thenReturn(vendedor);

        mockMvc.perform(post("/usuarios/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"vendedor@example.com","password":"ClaveSegura9!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("VENDEDOR"));
    }

    @Test
    void loginRechazaCredencialesIncorrectasConRespuesta401() throws Exception {
        when(authService.autenticar("cliente@example.com", "incorrecta"))
                .thenThrow(new org.springframework.security.authentication.BadCredentialsException(
                        "Correo o contraseña incorrectos."));

        mockMvc.perform(post("/usuarios/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"cliente@example.com","password":"incorrecta"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Correo o contraseña incorrectos."));
    }

    @Test
    void loginInactivoDevuelveAvisoDeCuentaPendiente() throws Exception {
        when(authService.autenticar("cliente@example.com", "ClaveSegura9!"))
                .thenThrow(new AccountPendingException());

        mockMvc.perform(post("/usuarios/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"cliente@example.com","password":"ClaveSegura9!"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Tu cuenta está pendiente de revisión."));
    }

    @Test
    void logoutInvalidaLaSesion() throws Exception {
        when(authService.autenticar("cliente@example.com", "ClaveSegura9!"))
                .thenReturn(crearCliente(true));

        MvcResult resultadoLogin = mockMvc.perform(post("/usuarios/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"cliente@example.com","password":"ClaveSegura9!"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession sesion = (MockHttpSession) resultadoLogin.getRequest().getSession(false);
        assertNotNull(sesion);

        mockMvc.perform(post("/usuarios/logout")
                        .with(csrf())
                        .session(sesion))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/usuarios/sesion-actual").session(sesion))
                .andExpect(status().isForbidden());
    }

    @Test
    void impideAccesoAlRegistroAdministrativoSinElRolAdmin() throws Exception {
        mockMvc.perform(post("/administrador/register")
                        .with(user("cliente").roles("CLIENTE"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre":"Admin",
                                  "apellidos":"Prueba",
                                  "email":"admin@example.com",
                                  "contrasena":"ClaveSegura9!"
                                }
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(administradorService);
    }

    @Test
    void permiteElRegistroAdministrativoAlRolAdmin() throws Exception {
        when(administradorService.registrar(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new RegistroUsuarioResponse("admin-id", "administrador"));

        mockMvc.perform(post("/administrador/register")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre":"Admin",
                                  "apellidos":"Prueba",
                                  "email":"admin@example.com",
                                  "contrasena":"ClaveSegura9!"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoUsuario").value("administrador"));

        verify(administradorService).registrar(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void impideAccesoARecursosProtegidosSinAutenticar() throws Exception {
        mockMvc.perform(get("/usuarios/sesion-actual"))
                .andExpect(status().isForbidden());
    }

    @Test
    void sesionActualDevuelve401SiLaAutenticacionEsNulaOVacia() {
        AuthController controller = new AuthController(
                authService,
                mock(SecurityContextRepository.class),
                mock(SesionService.class));
        Authentication autenticacionSinAutoridades = mock(Authentication.class);
        when(autenticacionSinAutoridades.getAuthorities()).thenReturn(List.of());

        assertEquals(401, controller.obtenerSesionActual(null).getStatusCode().value());
        assertEquals(
                401,
                controller.obtenerSesionActual(autenticacionSinAutoridades)
                        .getStatusCode().value());
    }

    @Test
    void permiteElAccesoDeClienteSoloABajoDeSuRutaProtegida() throws Exception {
        mockMvc.perform(get("/cliente/recurso-inexistente")
                        .with(user("cliente").roles("CLIENTE")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/cliente/recurso-inexistente")
                        .with(user("vendedor").roles("VENDEDOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void permiteElAccesoDeVendedorSoloABajoDeSuRutaProtegida() throws Exception {
        mockMvc.perform(get("/vendedor/recurso-inexistente")
                        .with(user("vendedor").roles("VENDEDOR")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/vendedor/recurso-inexistente")
                        .with(user("cliente").roles("CLIENTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void consultaProductosUsandoElEmailDeLaSesion() throws Exception {
        when(productoService.consultarProductosPropios("vendedor@example.com"))
                .thenReturn(List.of(new ConsultarProductosResponse(
                        "Teclado",
                        "Informática",
                        4,
                        "Teclado mecánico",
                        49.99,
                        0,
                        0,
                        true)));

        mockMvc.perform(get("/vendedor/productos")
                        .with(user("vendedor@example.com").roles("VENDEDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Teclado"))
                .andExpect(jsonPath("$[0].categoria").value("Informática"))
                .andExpect(jsonPath("$[0].esVisible").value(true));

        verify(productoService).consultarProductosPropios("vendedor@example.com");
    }

    @Test
    void deniegaLaConsultaDeProductosAOtrosRolesYSesionesAnonimas() throws Exception {
        mockMvc.perform(get("/vendedor/productos")
                        .with(user("cliente@example.com").roles("CLIENTE")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/vendedor/productos")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/vendedor/productos"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productoService);
    }

    @Test
    void devuelveNombreComercialDelVendedorAutenticado() throws Exception {
        when(vendedorService.obtenerNombreComercial("vendedor@example.com"))
                .thenReturn("Tienda Ejemplo");

        mockMvc.perform(get("/vendedor/perfil")
                        .with(user("vendedor@example.com").roles("VENDEDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreComercial").value("Tienda Ejemplo"));

        verify(vendedorService).obtenerNombreComercial("vendedor@example.com");
    }

    @Test
    void deniegaElPerfilDelVendedorAOtrosRolesYSesionesAnonimas() throws Exception {
        mockMvc.perform(get("/vendedor/perfil")
                        .with(user("cliente@example.com").roles("CLIENTE")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/vendedor/perfil")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/vendedor/perfil"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(vendedorService);
    }

    private Cliente crearCliente(boolean activado) {
        Cliente cliente = new Cliente();
        cliente.setEmail("cliente@example.com");
        cliente.setActivada(activado);
        return cliente;
    }
}
