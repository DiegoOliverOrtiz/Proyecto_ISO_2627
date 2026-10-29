package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import es.uclm.esibuy.backend.dao.AdministradorDAO;
import es.uclm.esibuy.backend.dao.UsuarioDAO;
import es.uclm.esibuy.backend.exceptions.AutoBloqueoAdministradorException;
import es.uclm.esibuy.backend.exceptions.UsuarioNoEncontradoException;
import es.uclm.esibuy.backend.model.Administrador;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.Usuario;
import jakarta.validation.Validator;

@ExtendWith(MockitoExtension.class)
class AdministradorServiceHU06Tests {

        @Mock
        private AdministradorDAO administradorDAO;

        @Mock
        private es.uclm.esibuy.backend.dao.CategoriaDAO categoriaDAO;

        @Mock
        private UsuarioDAO usuarioDAO;

        @Mock
        private PasswordEncoder passwordEncoder;

        @Mock
        private PasswordPolicy passwordPolicy;

        @Mock
        private Validator validator;

        @Mock
        private SesionService sesionService;

        private AdministradorService servicio;

        @BeforeEach
        void setUp() {
                servicio = new AdministradorService(
                                administradorDAO,
                                categoriaDAO,
                                usuarioDAO,
                                passwordEncoder,
                                passwordPolicy,
                                validator,
                                sesionService);
        }

        @Test
        void bloquearUsuarioCambiaEstadoPersisteYCierraSesiones() {
                Cliente usuario = new Cliente();
                usuario.setId("usuario-id");
                usuario.setEmail("cliente@example.com");
                usuario.setActivada(true);
                usuario.setBloqueada(false);

                when(usuarioDAO.findById("usuario-id"))
                                .thenReturn(Optional.of(usuario));

                servicio.bloquearUsuario(
                                "usuario-id",
                                "admin@esibuy.es");

                assertTrue(usuario.isBloqueada());
                assertTrue(usuario.isActivada());

                verify(usuarioDAO).save(usuario);
                verify(sesionService).cerrarSesiones("cliente@example.com");
        }

        @Test
        void bloquearUsuarioPendienteNoLoActiva() {
                Cliente usuario = new Cliente();
                usuario.setId("usuario-id");
                usuario.setEmail("pendiente@example.com");
                usuario.setActivada(false);
                usuario.setBloqueada(false);

                when(usuarioDAO.findById("usuario-id"))
                                .thenReturn(Optional.of(usuario));

                servicio.bloquearUsuario(
                                "usuario-id",
                                "admin@esibuy.es");

                assertTrue(usuario.isBloqueada());
                assertFalse(usuario.isActivada());

                verify(usuarioDAO).save(usuario);
                verify(sesionService).cerrarSesiones("pendiente@example.com");
        }

        @Test
        void desbloquearUsuarioPreservaEstadoDeActivacion() {
                Cliente usuario = new Cliente();
                usuario.setId("usuario-id");
                usuario.setEmail("pendiente@example.com");
                usuario.setActivada(false);
                usuario.setBloqueada(true);

                when(usuarioDAO.findById("usuario-id"))
                                .thenReturn(Optional.of(usuario));

                servicio.desbloquearUsuario(
                                "usuario-id",
                                "admin@esibuy.es");

                assertFalse(usuario.isBloqueada());
                assertFalse(usuario.isActivada());

                verify(usuarioDAO).save(usuario);
                verifyNoInteractions(sesionService);
        }

        @Test
        void administradorNoPuedeBloquearseASiMismo() {
                Administrador administrador = new Administrador();
                administrador.setId("admin-id");
                administrador.setEmail("admin@esibuy.es");
                administrador.setActivada(true);
                administrador.setBloqueada(false);

                when(usuarioDAO.findById("admin-id"))
                                .thenReturn(Optional.of(administrador));

                assertThrows(
                                AutoBloqueoAdministradorException.class,
                                () -> servicio.bloquearUsuario(
                                                "admin-id",
                                                "admin@esibuy.es"));

                assertFalse(administrador.isBloqueada());

                verify(usuarioDAO, never()).save(any(Usuario.class));
                verifyNoInteractions(sesionService);
        }

        @Test
        void administradorNoPuedeDesbloquearseASiMismo() {
                Administrador administrador = new Administrador();
                administrador.setId("admin-id");
                administrador.setEmail("admin@esibuy.es");
                administrador.setActivada(true);
                administrador.setBloqueada(true);

                when(usuarioDAO.findById("admin-id"))
                                .thenReturn(Optional.of(administrador));

                assertThrows(
                                AutoBloqueoAdministradorException.class,
                                () -> servicio.desbloquearUsuario(
                                                "admin-id",
                                                "admin@esibuy.es"));

                assertTrue(administrador.isBloqueada());

                verify(usuarioDAO, never()).save(any(Usuario.class));
                verifyNoInteractions(sesionService);
        }

        @Test
        void bloquearUsuarioInexistenteLanzaExcepcion() {
                when(usuarioDAO.findById("no-existe"))
                                .thenReturn(Optional.empty());

                assertThrows(
                                UsuarioNoEncontradoException.class,
                                () -> servicio.bloquearUsuario(
                                                "no-existe",
                                                "admin@esibuy.es"));

                verifyNoInteractions(sesionService);
        }

        @Test
        void usuarioYaBloqueadoNoSeGuardaNiSeCierranSesionesDeNuevo() {
                Cliente usuario = new Cliente();
                usuario.setId("usuario-id");
                usuario.setEmail("cliente@example.com");
                usuario.setActivada(true);
                usuario.setBloqueada(true);

                when(usuarioDAO.findById("usuario-id"))
                                .thenReturn(Optional.of(usuario));

                servicio.bloquearUsuario(
                                "usuario-id",
                                "admin@esibuy.es");

                verify(usuarioDAO, never()).save(any(Usuario.class));
                verifyNoInteractions(sesionService);
        }

        @Test
        void desbloquearUsuarioInexistenteLanzaExcepcion() {
                when(usuarioDAO.findById("no-existe"))
                                .thenReturn(Optional.empty());

                assertThrows(
                                UsuarioNoEncontradoException.class,
                                () -> servicio.desbloquearUsuario(
                                                "no-existe",
                                                "admin@esibuy.es"));

                verifyNoInteractions(sesionService);
        }

        @Test
        void usuarioYaDesbloqueadoNoSeGuardaDeNuevo() {
                Cliente usuario = new Cliente();
                usuario.setId("usuario-id");
                usuario.setEmail("cliente@example.com");
                usuario.setActivada(true);
                usuario.setBloqueada(false);

                when(usuarioDAO.findById("usuario-id"))
                                .thenReturn(Optional.of(usuario));

                servicio.desbloquearUsuario(
                                "usuario-id",
                                "admin@esibuy.es");

                verify(usuarioDAO, never()).save(any(Usuario.class));
                verifyNoInteractions(sesionService);
        }
}
