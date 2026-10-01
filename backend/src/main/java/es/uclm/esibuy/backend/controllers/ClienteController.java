package es.uclm.esibuy.backend.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.uclm.esibuy.backend.dto.RegistroClienteRequest;
import es.uclm.esibuy.backend.dto.RegistroUsuarioResponse;
import es.uclm.esibuy.backend.services.ClienteService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/cliente")
public class ClienteController {

        private final ClienteService clienteService;

        public ClienteController(ClienteService clienteService) {
                this.clienteService = clienteService;
        }

        @PostMapping("/register")
        public ResponseEntity<RegistroUsuarioResponse> register(@Valid @RequestBody RegistroClienteRequest request) {
                return ResponseEntity.status(HttpStatus.CREATED).body(clienteService.registrar(request));
        }
}
