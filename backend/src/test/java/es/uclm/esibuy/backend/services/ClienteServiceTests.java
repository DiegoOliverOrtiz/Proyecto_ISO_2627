package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import es.uclm.esibuy.backend.dao.ClienteDAO;
import es.uclm.esibuy.backend.exceptions.UsuarioNoEncontradoException;
import es.uclm.esibuy.backend.model.Cliente;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTests {

    @Mock
    private ClienteDAO clienteDAO;

    @Mock
    private SoporteRegistro soporteRegistro;

    @Test
    void cambiaUnClienteNormalAPremiumYConservaElRestoDeDatos() {
        Cliente cliente = cliente("cliente@example.com", false);
        when(clienteDAO.findByEmail("cliente@example.com")).thenReturn(Optional.of(cliente));
        when(clienteDAO.save(cliente)).thenReturn(cliente);

        Cliente resultado = new ClienteService(clienteDAO, soporteRegistro)
                .cambiarTipoCliente("cliente@example.com", true);

        assertEquals(true, resultado.isEsPremium());
        assertEquals("Nombre", resultado.getNombre());
        assertEquals("cliente@example.com", resultado.getEmail());
        verify(clienteDAO).save(cliente);
    }

    @Test
    void cambiaUnClientePremiumAPremiumNormal() {
        Cliente cliente = cliente("cliente@example.com", true);
        when(clienteDAO.findByEmail("cliente@example.com")).thenReturn(Optional.of(cliente));
        when(clienteDAO.save(cliente)).thenReturn(cliente);

        Cliente resultado = new ClienteService(clienteDAO, soporteRegistro)
                .cambiarTipoCliente("cliente@example.com", false);

        assertEquals(false, resultado.isEsPremium());
        verify(clienteDAO).save(cliente);
    }

    @Test
    void rechazaUnUsuarioNoEncontrado() {
        when(clienteDAO.findByEmail("desconocido@example.com")).thenReturn(Optional.empty());

        assertThrows(
                UsuarioNoEncontradoException.class,
                () -> new ClienteService(clienteDAO, soporteRegistro)
                        .cambiarTipoCliente("desconocido@example.com", true));

        verify(clienteDAO, never()).save(org.mockito.ArgumentMatchers.any(Cliente.class));
    }

    @Test
    void rechazaUnTipoNulo() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ClienteService(clienteDAO, soporteRegistro)
                        .cambiarTipoCliente("cliente@example.com", null));

        verify(clienteDAO, never()).findByEmail(org.mockito.ArgumentMatchers.anyString());
    }

    private Cliente cliente(String email, boolean esPremium) {
        Cliente cliente = new Cliente();
        cliente.setId("cliente-id");
        cliente.setNombre("Nombre");
        cliente.setApellidos("Apellidos");
        cliente.setEmail(email);
        cliente.setEsPremium(esPremium);
        return cliente;
    }
}
