package es.uclm.esibuy.backend.services;

import es.uclm.esibuy.backend.dao.UsuarioDAO;
import es.uclm.esibuy.backend.dto.ModificarUsuarioRequest;
import es.uclm.esibuy.backend.exceptions.UsuarioNoEncontradoException;
import es.uclm.esibuy.backend.model.Categoria;
import es.uclm.esibuy.backend.model.Administrador;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.Usuario;
import es.uclm.esibuy.backend.model.Vendedor;
import es.uclm.esibuy.backend.dto.PerfilResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Optional;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerfilServiceTests {

    @Mock
    private UsuarioDAO usuarioDAO;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private AdministradorService administradorService;

    private PerfilService perfilService;

    @BeforeEach
    void prepararServicio() {
        perfilService = new PerfilService(usuarioDAO, mongoTemplate, administradorService);
    }

    @Test
    void devuelvePerfilDeClienteSinExponerDatosSensibles() {
        Cliente usuario = new Cliente();
        usuario.setEmail("cliente@esibuy.com");
        usuario.setNombre("Ana");
        usuario.setApellidos("García López");
        usuario.setTelefono("600123123");
        usuario.setDniNif("12345678A");
        usuario.setEsPremium(true);
        usuario.setContrasenaHash("hash-de-la-contrasena");
        when(usuarioDAO.findByEmail("cliente@esibuy.com")).thenReturn(Optional.of(usuario));

        PerfilResponse perfil = perfilService.consultarPerfil("cliente@esibuy.com");

        assertEquals("CLIENTE", perfil.tipoUsuario());
        assertEquals("Ana", perfil.nombre());
        assertEquals("García López", perfil.apellidos());
        assertEquals("cliente@esibuy.com", perfil.email());
        assertEquals("12*******", perfil.dniNif());
        assertEquals(true, perfil.esPremium());
        for (var componente : PerfilResponse.class.getRecordComponents()) {
            assertFalse(componente.getName().toLowerCase().contains("contrasena"));
            assertFalse(componente.getName().toLowerCase().contains("password"));
            assertFalse(componente.getName().toLowerCase().contains("token"));
            assertFalse(componente.getName().toLowerCase().contains("hash"));
        }
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void devuelveNombreDeCategoriaDelVendedor() {
        Vendedor usuario = new Vendedor();
        usuario.setEmail("vendedor@esibuy.com");
        usuario.setNombre("Luis");
        usuario.setApellidos("Martín");
        usuario.setDniNif("45678901B");
        usuario.setNombreComercial("UrbanStyle");
        usuario.setCategoriaId("507f1f77bcf86cd799439011");
        when(usuarioDAO.findByEmail("vendedor@esibuy.com")).thenReturn(Optional.of(usuario));

        Categoria categoria = new Categoria();
        categoria.setNombre("Tecnología");
        when(mongoTemplate.findOne(any(Query.class), eq(Categoria.class))).thenReturn(categoria);

        PerfilResponse perfil = perfilService.consultarPerfil("vendedor@esibuy.com");

        assertEquals("VENDEDOR", perfil.tipoUsuario());
        assertEquals("UrbanStyle", perfil.nombreComercial());
        assertEquals("Tecnología", perfil.categoria());
        assertEquals("45*******", perfil.dniNif());
    }

    @Test
    void informaCategoriaNulaCuandoNoExisteCategoriaAsociada() {
        Vendedor usuario = new Vendedor();
        usuario.setEmail("vendedor@esibuy.com");
        usuario.setNombre("Luis");
        usuario.setCategoriaId("507f1f77bcf86cd799439011");
        when(usuarioDAO.findByEmail("vendedor@esibuy.com")).thenReturn(Optional.of(usuario));
        when(mongoTemplate.findOne(any(Query.class), eq(Categoria.class))).thenReturn(null);

        PerfilResponse perfil = perfilService.consultarPerfil("vendedor@esibuy.com");

        assertNull(perfil.categoria());
    }

    @Test
    void devuelvePerfilDeAdministrador() {
        Administrador usuario = new Administrador();
        usuario.setEmail("admin@esibuy.com");
        usuario.setNombre("Marta");
        usuario.setApellidos("Sanz");
        usuario.setRol("ADMIN");
        usuario.setSede("Madrid");
        when(usuarioDAO.findByEmail("admin@esibuy.com")).thenReturn(Optional.of(usuario));

        PerfilResponse perfil = perfilService.consultarPerfil("admin@esibuy.com");

        assertEquals("ADMIN", perfil.tipoUsuario());
        assertEquals("ADMIN", perfil.rol());
        assertEquals("Madrid", perfil.sede());
        assertNull(perfil.categoria());
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void lanzaExcepcionCuandoNoExisteElUsuarioAutenticado() {
        when(usuarioDAO.findByEmail("ausente@esibuy.com")).thenReturn(Optional.empty());

        assertThrows(UsuarioNoEncontradoException.class,
                () -> perfilService.consultarPerfil("ausente@esibuy.com"));
    }

    @Test
    void modificaElPerfilUsandoLaIdentidadAutenticada() {
        Cliente usuario = new Cliente();
        usuario.setId("cliente-1");
        usuario.setEmail("cliente@esibuy.com");
        usuario.setNombre("Ana");
        usuario.setApellidos("García");
        when(usuarioDAO.findByEmail("cliente@esibuy.com")).thenReturn(Optional.of(usuario));

        ModificarUsuarioRequest solicitud = new ModificarUsuarioRequest(
                "Ana Nueva", "García López", "600123123", null,
                null, LocalDate.of(2000, 1, 1), null, null);
        when(administradorService.modificarUsuario(eq("cliente-1"), eq(solicitud)))
                .thenReturn(null);

        PerfilResponse respuesta = perfilService.modificarPerfil("cliente@esibuy.com", solicitud);

        verify(administradorService).modificarUsuario("cliente-1", solicitud);
        assertEquals("Ana", respuesta.nombre());
        assertEquals("cliente@esibuy.com", respuesta.email());
    }

    @Test
    void noModificaNingunUsuarioSiLaIdentidadAutenticadaNoExiste() {
        ModificarUsuarioRequest solicitud = new ModificarUsuarioRequest(
                "Ana", "García", "600123123", null,
                null, null, null, null);
        when(usuarioDAO.findByEmail("ausente@esibuy.com")).thenReturn(Optional.empty());

        assertThrows(UsuarioNoEncontradoException.class,
                () -> perfilService.modificarPerfil("ausente@esibuy.com", solicitud));
        verifyNoInteractions(administradorService);
    }
}
