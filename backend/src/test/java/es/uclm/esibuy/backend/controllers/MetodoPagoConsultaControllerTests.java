package es.uclm.esibuy.backend.controllers;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import es.uclm.esibuy.backend.config.SecurityConfiguration;
import es.uclm.esibuy.backend.dao.ClienteDAO;
import es.uclm.esibuy.backend.dao.MetodoPagoDAO;
import es.uclm.esibuy.backend.dao.TipoMetodoPagoDAO;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.MetodoPago;
import es.uclm.esibuy.backend.services.PagoSimuladoService;
import es.uclm.esibuy.backend.services.ValidadorMetodoPago;

@WebMvcTest(MetodoPagoController.class)
@Import({SecurityConfiguration.class, PagoSimuladoService.class})
class MetodoPagoConsultaControllerTests {
    @Autowired private MockMvc mvc;
    @MockitoBean private ClienteDAO clientes;
    @MockitoBean private MetodoPagoDAO metodos;
    @MockitoBean private TipoMetodoPagoDAO tipos;
    @MockitoBean private ValidadorMetodoPago validador;
    private Cliente cliente;

    @BeforeEach
    void configurar() {
        cliente = new Cliente();
        cliente.setId("cliente-a");
        cliente.setActivada(true);
        when(clientes.findByEmail("a@example.com")).thenReturn(Optional.of(cliente));
    }

    @Test
    void listaSoloMetodosPropiosEnmascaradosSinAceptarPropietario() throws Exception {
        MetodoPago metodo = new MetodoPago();
        metodo.setId("metodo-a");
        metodo.setClienteId("cliente-a");
        metodo.setNombre("Mi tarjeta");
        metodo.setTipo("TARJETA");
        metodo.setToken("tok_sim_no_exponer");
        metodo.setReferenciaEnmascarada("**** **** **** 4242");
        when(metodos.findByClienteId("cliente-a")).thenReturn(List.of(metodo));
        mvc.perform(get("/cliente/metodos-pago").param("clienteId", "cliente-b")
                        .header("X-Cliente-Id", "cliente-b").with(user("a@example.com").roles("CLIENTE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("metodo-a"))
                .andExpect(jsonPath("$[0].datosEnmascarados").value("**** **** **** 4242"))
                .andExpect(jsonPath("$[0].token").doesNotExist())
                .andExpect(jsonPath("$[0].clienteId").doesNotExist())
                .andExpect(jsonPath("$[0].numeroTarjeta").doesNotExist())
                .andExpect(content().string(not(containsString("tok_sim_no_exponer"))));
        verify(metodos).findByClienteId("cliente-a");
        verify(metodos, never()).findByClienteId("cliente-b");
    }

    @Test
    void devuelveListaVaciaSiNoTieneMetodos() throws Exception {
        when(metodos.findByClienteId("cliente-a")).thenReturn(List.of());
        mvc.perform(get("/cliente/metodos-pago").with(user("a@example.com").roles("CLIENTE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cuentaInactivaOBloqueadaNoConsultaMetodos(boolean bloqueada) throws Exception {
        cliente.setActivada(bloqueada);
        cliente.setBloqueada(bloqueada);
        mvc.perform(get("/cliente/metodos-pago").with(user("a@example.com").roles("CLIENTE")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(metodos);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "VENDEDOR", "USER"})
    void otroRolNoConsulta(String rol) throws Exception {
        mvc.perform(get("/cliente/metodos-pago").with(user("a@example.com").roles(rol)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(clientes, metodos);
    }

    @Test
    void exigeSesion() throws Exception {
        mvc.perform(get("/cliente/metodos-pago")).andExpect(status().isForbidden());
        verifyNoInteractions(clientes, metodos);
    }
}
