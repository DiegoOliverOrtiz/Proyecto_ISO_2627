package es.uclm.esibuy.backend.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.uclm.esibuy.backend.dto.ConsultarProductosResponse;
import es.uclm.esibuy.backend.dto.CambiarTipoClienteRequest;
import es.uclm.esibuy.backend.dto.PerfilResponse;
import es.uclm.esibuy.backend.dto.RegistroClienteRequest;
import es.uclm.esibuy.backend.dto.RegistroUsuarioResponse;
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
        public ResponseEntity<RegistroUsuarioResponse> register(@Valid @RequestBody RegistroClienteRequest request) {
                return ResponseEntity.status(HttpStatus.CREATED).body(clienteService.registrar(request));
        }

        @GetMapping("/productos")
        public List<ConsultarProductosResponse> consultarProductos(Authentication authentication) {
                return productoService.consultarProductosParaClientes(authentication.getName());
        }

        @PutMapping("/tipo")
        public ResponseEntity<PerfilResponse> cambiarTipo(
                        Authentication authentication,
                        @Valid @RequestBody CambiarTipoClienteRequest request) {
                clienteService.cambiarTipoCliente(authentication.getName(), request.esPremium());
                return ResponseEntity.ok(perfilService.consultarPerfil(authentication.getName()));
        }
}
