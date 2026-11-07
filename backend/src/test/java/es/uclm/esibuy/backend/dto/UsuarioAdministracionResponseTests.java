package es.uclm.esibuy.backend.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import es.uclm.esibuy.backend.model.Cliente;

class UsuarioAdministracionResponseTests {

    @Test
    void ocultaDniYTelefonoEnRespuestasDeAdministracion() {
        Cliente cliente = new Cliente();
        cliente.setId("cliente-1");
        cliente.setNombre("Ana");
        cliente.setApellidos("García");
        cliente.setEmail("ana@example.com");
        cliente.setDniNif("12345678A");
        cliente.setTelefono("600123123");

        UsuarioAdministracionResponse respuesta = UsuarioAdministracionResponse.desde(cliente);

        assertEquals("12*******", respuesta.dniNif());
        assertEquals("******123", respuesta.telefono());
        assertFalse(respuesta.toString().contains("12345678A"));
        assertFalse(respuesta.toString().contains("600123123"));
    }
}
