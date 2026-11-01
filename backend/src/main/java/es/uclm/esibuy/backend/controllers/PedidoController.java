package es.uclm.esibuy.backend.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.uclm.esibuy.backend.dto.CrearPedidoRequest;
import es.uclm.esibuy.backend.dto.PedidoResponse;
import es.uclm.esibuy.backend.services.PedidoService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/cliente/pedidos")
public class PedidoController {
    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @PostMapping
    public ResponseEntity<PedidoResponse> realizarPedido(@Valid @RequestBody CrearPedidoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(PedidoResponse.de(pedidoService.realizarPedido(
                request.direccionId(), request.metodoPagoId(), request.operacionId())));
    }
}
