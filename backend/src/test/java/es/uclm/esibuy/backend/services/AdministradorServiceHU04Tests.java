package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import es.uclm.esibuy.backend.dao.AdministradorDAO;
import es.uclm.esibuy.backend.dao.UsuarioDAO;
import es.uclm.esibuy.backend.dto.EstadisticasUsuariosResponse;
import es.uclm.esibuy.backend.dto.ListadoUsuariosResponse;
import es.uclm.esibuy.backend.dto.UsuarioAdministracionResponse;
import es.uclm.esibuy.backend.exceptions.UsuarioNoEncontradoException;
import es.uclm.esibuy.backend.model.Administrador;
import jakarta.validation.Validator;

@ExtendWith(MockitoExtension.class)
class AdministradorServiceHU04Tests {

    @Mock
    private AdministradorDAO administradorDAO;

    @Mock
    private UsuarioDAO usuarioDAO;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordPolicy passwordPolicy;

    @Mock
    private Validator validator;

    private AdministradorService servicio;

    @BeforeEach
    void setUp() {
        servicio = new AdministradorService(
                administradorDAO,
                usuarioDAO,
                passwordEncoder,
                passwordPolicy,
                validator);
    }

    @Test
    void consultaUsuariosDevuelvePaginaEstadisticasYDatosAdministrativos() {
        Administrador administrador = new Administrador();
        administrador.setId("admin-id");
        administrador.setNombre("Ana");
        administrador.setApellidos("Admin");
        administrador.setEmail("ana@example.com");
        administrador.setTelefono("600000001");
        administrador.setSede("Madrid");
        administrador.setActivada(true);
        administrador.setBloqueada(false);
        administrador.setContrasenaHash("hash-no-debe-exponerse");

        PageRequest pageable = PageRequest.of(1, 20);
        when(usuarioDAO.buscar("Ana", "ACTIVO", "ADMIN", pageable))
                .thenReturn(new PageImpl<>(List.of(administrador), pageable, 21));
        when(usuarioDAO.obtenerEstadisticas())
                .thenReturn(new EstadisticasUsuariosResponse(21, 18, 2, 1));

        ListadoUsuariosResponse respuesta = servicio.consultarUsuarios(
                "Ana", "ACTIVO", "ADMIN", pageable);

        assertEquals(1, respuesta.contenido().size());
        assertEquals(1, respuesta.pagina());
        assertEquals(20, respuesta.tamano());
        assertEquals(21, respuesta.totalElementos());
        assertEquals(2, respuesta.totalPaginas());
        assertEquals(21, respuesta.estadisticas().totalUsuarios());

        UsuarioAdministracionResponse usuario = respuesta.contenido().get(0);
        assertEquals("Ana", usuario.nombre());
        assertEquals("ACTIVO", usuario.estado());
        assertEquals("ADMIN", usuario.rol());
        assertEquals("Madrid", usuario.sede());
        assertFalse(Arrays.stream(UsuarioAdministracionResponse.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase())
                .anyMatch(nombre -> nombre.contains("contrasena")
                        || nombre.contains("hash")
                        || nombre.contains("token")));
    }

    @Test
    void consultaUsuariosSinResultadosDevuelvePaginaVacia() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(usuarioDAO.buscar(null, "PENDIENTE", "NO_ADMIN", pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));
        when(usuarioDAO.obtenerEstadisticas())
                .thenReturn(new EstadisticasUsuariosResponse(0, 0, 0, 0));

        ListadoUsuariosResponse respuesta = servicio.consultarUsuarios(
                null, "PENDIENTE", null, pageable);

        assertEquals(0, respuesta.contenido().size());
        assertEquals(0, respuesta.totalElementos());
        assertEquals(0, respuesta.estadisticas().totalUsuarios());
    }

    @Test
    void consultaDetalleInexistenteLanzaExcepcion() {
        when(usuarioDAO.buscarPorId("no-existe")).thenReturn(Optional.empty());

        assertThrows(
                UsuarioNoEncontradoException.class,
                () -> servicio.consultarUsuario("no-existe"));
    }
}
