
package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import es.uclm.esibuy.backend.dao.CategoriaDAO;
import es.uclm.esibuy.backend.dao.ClienteDAO;
import es.uclm.esibuy.backend.dao.UsuarioDAO;
import es.uclm.esibuy.backend.dao.VendedorDAO;
import es.uclm.esibuy.backend.dto.RegistroClienteRequest;
import es.uclm.esibuy.backend.dto.RegistroVendedorRequest;
import es.uclm.esibuy.backend.exceptions.InvalidRegistrationException;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.Vendedor;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

@ExtendWith(MockitoExtension.class)
class RegistrationSecurityTests {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Mock
    private ClienteDAO clienteDAO;

    @Mock
    private UsuarioDAO usuarioDAO;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private VendedorDAO vendedorDAO;

    @Mock
    private CategoriaDAO categoriaDAO;

    @Mock
    private BajaUsuarioService bajaUsuarioService;

    private SoporteRegistro soporteRegistro() {
        return new SoporteRegistro(
                usuarioDAO,
                passwordEncoder,
                new PasswordPolicy(),
                VALIDATOR);
    }

    @Test
    void acceptsStrongPasswordAndRejectsWeakOrCommonPasswords() {
        PasswordPolicy policy = new PasswordPolicy();

        policy.validate("ClaveSegura9!");
        assertThrows(
                InvalidRegistrationException.class,
                () -> policy.validate("clavecorta"));

        assertThrows(
                InvalidRegistrationException.class,
                () -> policy.validate("Password123!"));
    }

    @Test
    void rejectsPasswordContainingNormalizedPersonalDataOrApplicationName() {
        PasswordPolicy policy = new PasswordPolicy();
        List<String> customerIdentifiers = List.of("Ana", "García", "ana@example.com");
        List<String> otherIdentifiers = List.of("Otra persona");
        List<String> sellerIdentifiers = List.of("Tienda Luna");

        assertThrows(
                InvalidRegistrationException.class,
                () -> policy.validate("GarcíaClave9!", customerIdentifiers));
        assertThrows(
                InvalidRegistrationException.class,
                () -> policy.validate("MiEsibuyClave9!", otherIdentifiers));
        assertThrows(
                InvalidRegistrationException.class,
                () -> policy.validate("TiendaLunaClave9!", sellerIdentifiers));
        policy.validate("ClaveSegura9!", List.of("Ana", "García", "ana@example.com", "Tienda Luna"));
    }

    @Test
    void checksRecentPasswordHashesAndKeepsHistoryBounded() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        String encoded = encoder.encode("ClaveSegura9!");

        PasswordPolicy policy = new PasswordPolicy();

        assertTrue(policy.matchesRecentPassword(
                "ClaveSegura9!", List.of(encoded), encoder));

        assertFalse(policy.matchesRecentPassword(
                "OtraClaveSegura8!", List.of(encoded), encoder));

        Cliente cliente = new Cliente();

        for (int index = 0; index < 6; index++) {
            cliente.registrarContrasenaHash("hash-" + index);
        }

        assertEquals(5, cliente.getHistorialContrasenasHash().size());
        assertEquals("hash-5", cliente.getContrasenaHash());
        assertEquals(
                "hash-5",
                cliente.getHistorialContrasenasHash().get(0));
    }

    @Test
    void storesOnlyEncodedPasswordAndInitialHistoryForCustomer() {
        String rawPassword = "ClaveSegura9!";

        when(usuarioDAO.existsByEmail("ana@example.com"))
                .thenReturn(false);

        when(passwordEncoder.encode(rawPassword))
                .thenReturn("bcrypt-hash");

        when(clienteDAO.save(any(Cliente.class))).thenAnswer(invocation -> {
            Cliente cliente = invocation.getArgument(0);
            cliente.setId("customer-id");
            return cliente;
        });

        ClienteService service = new ClienteService(clienteDAO, soporteRegistro(), bajaUsuarioService);

        RegistroClienteRequest request = new RegistroClienteRequest(
                "Ana",
                "García",
                LocalDate.of(1998, Month.APRIL, 12),
                "12345678Z",
                " Ana@Example.com ",
                rawPassword,
                null,
                null,
                false);

        var response = service.registrar(request);

        assertEquals("customer-id", response.id());
        assertEquals("cliente", response.tipoUsuario());

        ArgumentCaptor<Cliente> savedCustomer =
                ArgumentCaptor.forClass(Cliente.class);

        verify(clienteDAO).save(savedCustomer.capture());

        assertEquals(
                "ana@example.com",
                savedCustomer.getValue().getEmail());

        assertEquals(
                "bcrypt-hash",
                savedCustomer.getValue().getContrasenaHash());

        assertEquals(
                List.of("bcrypt-hash"),
                savedCustomer.getValue().getHistorialContrasenasHash());

        assertNotEquals(
                rawPassword,
                savedCustomer.getValue().getContrasenaHash());

        assertFalse(savedCustomer.getValue().isBloqueada());
        assertFalse(savedCustomer.getValue().isActivada());
    }

    @Test
    void rejectsEmailAlreadyUsedByAnyUserBeforePersisting() {
        when(usuarioDAO.existsByEmail("ana@example.com"))
                .thenReturn(true);

        ClienteService service = new ClienteService(clienteDAO, soporteRegistro(), bajaUsuarioService);

        RegistroClienteRequest request = new RegistroClienteRequest(
                "Ana",
                "García",
                LocalDate.of(1998, Month.APRIL, 12),
                "12345678Z",
                "ana@example.com",
                "ClaveSegura9!",
                null,
                null,
                false);

        InvalidRegistrationException exception = assertThrows(
                InvalidRegistrationException.class,
                () -> service.registrar(request));

        // A21: mensaje genérico que no revela que el correo ya está en uso.
        assertEquals(SoporteRegistro.MENSAJE_DATOS_NO_VALIDOS, exception.getMessage());
        assertTrue(exception.getFieldErrors().isEmpty());
        verify(clienteDAO, never()).save(any(Cliente.class));
    }

    @Test
    void sellerWithEmailAlreadyUsedGetsGenericErrorWithoutPersisting() {
        String categoriaId = "6abe34098deff53da8a2c052";
        when(categoriaDAO.existsById(categoriaId)).thenReturn(true);
        when(vendedorDAO.existsByNombreComercial("Tienda Segura")).thenReturn(false);
        when(usuarioDAO.existsByEmail("tienda@example.com")).thenReturn(true);

        VendedorService service = new VendedorService(
                vendedorDAO,
                categoriaDAO,
                soporteRegistro(),
                bajaUsuarioService);

        RegistroVendedorRequest request = new RegistroVendedorRequest(
                "Ana",
                "García",
                "12345678Z",
                "tienda@example.com",
                "V4!qZ8#nLp",
                categoriaId,
                "Tienda Segura",
                null,
                null);

        InvalidRegistrationException exception = assertThrows(
                InvalidRegistrationException.class,
                () -> service.registrar(request));

        // A21: el mismo mensaje genérico que en el registro de cliente.
        assertEquals(SoporteRegistro.MENSAJE_DATOS_NO_VALIDOS, exception.getMessage());
        assertTrue(exception.getFieldErrors().isEmpty());
        verify(vendedorDAO, never()).save(any(Vendedor.class));
    }

    @Test
    void concurrentDuplicateEmailAlsoGetsGenericError() {
        when(usuarioDAO.existsByEmail("ana@example.com")).thenReturn(false);
        when(clienteDAO.save(any(Cliente.class)))
                .thenThrow(new DuplicateKeyException("uq_usuario_email"));

        ClienteService service = new ClienteService(clienteDAO, soporteRegistro(), bajaUsuarioService);

        RegistroClienteRequest request = new RegistroClienteRequest(
                "Ana",
                "García",
                LocalDate.of(1998, Month.APRIL, 12),
                "12345678Z",
                "ana@example.com",
                "ClaveSegura9!",
                null,
                null,
                false);

        InvalidRegistrationException exception = assertThrows(
                InvalidRegistrationException.class,
                () -> service.registrar(request));

        // A21: la carrera con el índice único tampoco revela el motivo.
        assertEquals(SoporteRegistro.MENSAJE_DATOS_NO_VALIDOS, exception.getMessage());
        assertTrue(exception.getFieldErrors().isEmpty());
    }

    @Test
    void storesSellerWithEncodedPasswordAndRejectsDuplicateCommercialName() {

        String rawPassword = "V4!qZ8#nLp";
        String categoriaId = "6abe34098deff53da8a2c052";

        // Simulamos que la categoría existe.
        when(categoriaDAO.existsById(categoriaId))
                .thenReturn(true);

        when(usuarioDAO.existsByEmail("tienda@example.com"))
                .thenReturn(false);

        when(vendedorDAO.existsByNombreComercial("Tienda Segura"))
                .thenReturn(false);

        when(passwordEncoder.encode(rawPassword))
                .thenReturn("seller-bcrypt-hash");

        when(vendedorDAO.save(any(Vendedor.class))).thenAnswer(invocation -> {
            Vendedor vendedor = invocation.getArgument(0);
            vendedor.setId("seller-id");
            return vendedor;
        });

        VendedorService service = new VendedorService(
                vendedorDAO,
                categoriaDAO,
                soporteRegistro(),
                bajaUsuarioService);

        RegistroVendedorRequest request = new RegistroVendedorRequest(
                "Ana",
                "García",
                "12345678Z",
                "tienda@example.com",
                rawPassword,
                categoriaId,
                "Tienda Segura",
                null,
                null);

        var response = service.registrar(request);

        assertEquals("seller-id", response.id());
        assertEquals("vendedor", response.tipoUsuario());

        ArgumentCaptor<Vendedor> savedSeller =
                ArgumentCaptor.forClass(Vendedor.class);

        verify(vendedorDAO).save(savedSeller.capture());

        // Debe almacenarse el identificador, no el nombre.
        assertEquals(
                categoriaId,
                savedSeller.getValue().getCategoriaId());

        assertEquals(
                "seller-bcrypt-hash",
                savedSeller.getValue().getContrasenaHash());

        assertEquals(
                List.of("seller-bcrypt-hash"),
                savedSeller.getValue().getHistorialContrasenasHash());

        assertNotEquals(
                rawPassword,
                savedSeller.getValue().getContrasenaHash());

        assertFalse(savedSeller.getValue().isBloqueada());
        assertFalse(savedSeller.getValue().isActivada());

        // Un segundo registro con el mismo nombre comercial debe rechazarse.
        when(vendedorDAO.existsByNombreComercial("Tienda Segura"))
                .thenReturn(true);

        InvalidRegistrationException exception = assertThrows(
                InvalidRegistrationException.class,
                () -> service.registrar(request));

        // El nombre comercial es público: se indica en su campo.
        assertEquals(
                List.of("Ese nombre comercial ya está en uso. Elige otro."),
                exception.getFieldErrors().get("nombreComercial"));
    }

    @Test
    void rejectsSellerRegistrationWhenCategoryDoesNotExist() {

        String categoriaId = "000000000000000000000000";

        VendedorService service = new VendedorService(
                vendedorDAO,
                categoriaDAO,
                soporteRegistro(),
                bajaUsuarioService);

        RegistroVendedorRequest request = new RegistroVendedorRequest(
                "Ana",
                "García",
                "12345678Z",
                "tienda@example.com",
                "V4!qZ8#nLp",
                categoriaId,
                "Tienda Segura",
                null,
                null);

        // Mockito devuelve false por defecto cuando no hemos configurado
        // una respuesta booleana. Así simulamos una categoría inexistente.
        assertThrows(
                InvalidRegistrationException.class,
                () -> service.registrar(request));

        verify(categoriaDAO).existsById(categoriaId);

        // No se debe persistir un vendedor con una categoría inexistente.
        verify(vendedorDAO, never()).save(any(Vendedor.class));
    }

    @Test
    void serviceRejectsPasswordThatDoesNotMeetStrengthPolicy() {
        ClienteService service = new ClienteService(clienteDAO, soporteRegistro(), bajaUsuarioService);

        RegistroClienteRequest request = new RegistroClienteRequest(
                "Ana",
                "García",
                LocalDate.of(1998, Month.APRIL, 12),
                "12345678Z",
                "ana@example.com",
                "weak",
                null,
                null,
                false);

        assertThrows(
                InvalidRegistrationException.class,
                () -> service.registrar(request));

        verify(clienteDAO, never()).save(any(Cliente.class));
    }

        @Test
        void rejectsCustomersYoungerThanEighteenBeforePersisting() {
                ClienteService service = new ClienteService(clienteDAO, soporteRegistro(), bajaUsuarioService);
                RegistroClienteRequest request = new RegistroClienteRequest(
                                "Ana",
                                "García",
                                LocalDate.now().minusYears(17),
                                "12345678Z",
                                "ana@example.com",
                                "ClaveSegura9!",
                                null,
                                null,
                                false);

                assertThrows(InvalidRegistrationException.class, () -> service.registrar(request));
                verify(clienteDAO, never()).save(any(Cliente.class));
        }

        @Test
        void acceptsCustomerOnEighteenthBirthday() {
                when(usuarioDAO.existsByEmail("ana@example.com")).thenReturn(false);
                when(passwordEncoder.encode("ClaveSegura9!")).thenReturn("bcrypt-hash");
                when(clienteDAO.save(any(Cliente.class))).thenAnswer(invocation -> invocation.getArgument(0));
                ClienteService service = new ClienteService(clienteDAO, soporteRegistro(), bajaUsuarioService);
                RegistroClienteRequest request = new RegistroClienteRequest(
                                "Ana",
                                "García",
                                LocalDate.now().minusYears(18),
                                "12345678Z",
                                "ana@example.com",
                                "ClaveSegura9!",
                                null,
                                null,
                                false);

                service.registrar(request);

                verify(clienteDAO).save(any(Cliente.class));
        }
}
