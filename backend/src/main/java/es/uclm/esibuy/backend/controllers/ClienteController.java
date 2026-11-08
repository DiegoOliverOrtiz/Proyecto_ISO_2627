package es.uclm.esibuy.backend.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.uclm.esibuy.backend.dto.ApiErrorResponse;
import es.uclm.esibuy.backend.dto.CambiarTipoClienteRequest;
import es.uclm.esibuy.backend.dto.DireccionRequest;
import es.uclm.esibuy.backend.dto.DireccionResponse;
import es.uclm.esibuy.backend.dto.PerfilResponse;
import es.uclm.esibuy.backend.dto.FiltroProductosRequest;
import es.uclm.esibuy.backend.dto.ProductoCatalogoResponse;
import es.uclm.esibuy.backend.dto.RegistroClienteRequest;
import es.uclm.esibuy.backend.dto.RegistroPublicoResponse;
import es.uclm.esibuy.backend.exceptions.DireccionDuplicadaException;
import es.uclm.esibuy.backend.exceptions.DireccionNoEncontradaException;
import es.uclm.esibuy.backend.services.ClienteService;
import es.uclm.esibuy.backend.services.ProductoService;
import es.uclm.esibuy.backend.services.PerfilService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/cliente")
public class ClienteController {

        private final ClienteService clienteService;
        private final ProductoService productoService;
        private final PerfilService perfilService;

        @Autowired
        public ClienteController(
                        ClienteService clienteService,
                        ProductoService productoService,
                        PerfilService perfilService) {
                this.clienteService = clienteService;
                this.productoService = productoService;
                this.perfilService = perfilService;
        }

        public ClienteController(ClienteService clienteService, ProductoService productoService) {
                this(clienteService, productoService, null);
        }

        @PostMapping("/register")
        public ResponseEntity<RegistroPublicoResponse> register(@Valid @RequestBody RegistroClienteRequest request) {
                clienteService.registrar(request);
                return ResponseEntity.accepted().body(RegistroPublicoResponse.confirmacion());
        }

        @GetMapping("/productos")
        public List<ProductoCatalogoResponse> consultarProductos(
                        Authentication authentication,
                        @Valid @ModelAttribute FiltroProductosRequest filtros) {
                return productoService.consultarProductosParaClientes(authentication.getName(), filtros);
        }

        @GetMapping("/productos/{id}")
        public ProductoCatalogoResponse consultarDetalleProducto(
                        Authentication authentication,
                        @PathVariable String id) {
                return productoService.consultarDetalleParaCliente(authentication.getName(), id);
        }

        @PutMapping("/tipo")
        public ResponseEntity<PerfilResponse> cambiarTipo(
                        Authentication authentication,
                        @Valid @RequestBody CambiarTipoClienteRequest request) {
                clienteService.cambiarTipoCliente(authentication.getName(), request.esPremium());
                return ResponseEntity.ok(perfilService.consultarPerfil(authentication.getName()));
        }

        @GetMapping("/direcciones")
        public List<DireccionResponse> consultarDirecciones(Authentication authentication) {
                return clienteService.consultarDirecciones(authentication.getName());
        }

        @PostMapping("/direcciones")
        public ResponseEntity<DireccionResponse> agregarDireccion(
                        Authentication authentication,
                        @Valid @RequestBody DireccionRequest request) {
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(clienteService.agregarDireccion(authentication.getName(), request));
        }

        @PutMapping("/direcciones/{id}")
        public DireccionResponse modificarDireccion(
                        Authentication authentication,
                        @PathVariable String id,
                        @Valid @RequestBody DireccionRequest request) {
                return clienteService.modificarDireccion(authentication.getName(), id, request);
        }

        @DeleteMapping("/direcciones/{id}")
        public ResponseEntity<Void> eliminarDireccion(
                        Authentication authentication,
                        @PathVariable String id) {
                clienteService.eliminarDireccion(authentication.getName(), id);
                return ResponseEntity.noContent().build();
        }

        @DeleteMapping("/cuenta")
        public ResponseEntity<Void> darDeBaja(Authentication authentication) {
                clienteService.darDeBaja(authentication.getName());
                return ResponseEntity.noContent().build();
        }

        @ExceptionHandler(DireccionNoEncontradaException.class)
        public ResponseEntity<ApiErrorResponse> manejarDireccionNoEncontrada(
                        DireccionNoEncontradaException excepcion) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(new ApiErrorResponse(excepcion.getMessage()));
        }

        @ExceptionHandler(DireccionDuplicadaException.class)
        public ResponseEntity<ApiErrorResponse> manejarDireccionDuplicada(
                        DireccionDuplicadaException excepcion) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(new ApiErrorResponse(excepcion.getMessage()));
        }
}
