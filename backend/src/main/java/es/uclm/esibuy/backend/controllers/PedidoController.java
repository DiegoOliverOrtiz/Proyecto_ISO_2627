package es.uclm.esibuy.backend.controllers;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.security.core.Authentication;

import es.uclm.esibuy.backend.dto.CrearPedidoRequest;
import es.uclm.esibuy.backend.dto.CrearValoracionRequest;
import es.uclm.esibuy.backend.dto.PedidoResponse;
import es.uclm.esibuy.backend.dto.ValoracionResponse;
import es.uclm.esibuy.backend.services.PedidoService;
import es.uclm.esibuy.backend.services.ValoracionService;
import es.uclm.esibuy.backend.dto.PedidoConsultaResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/cliente/pedidos")
public class PedidoController {
    private final PedidoService pedidoService;
    private final ValoracionService valoracionService;

    public PedidoController(PedidoService pedidoService, ValoracionService valoracionService) {
        this.pedidoService = pedidoService;
        this.valoracionService = valoracionService;
    }

    @PostMapping
    public ResponseEntity<PedidoResponse> realizarPedido(@Valid @RequestBody CrearPedidoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(PedidoResponse.de(pedidoService.realizarPedido(
                request.direccionId(), request.metodoPagoId(), request.operacionId())));
    }

    @GetMapping("/activos")
    public List<PedidoConsultaResponse> consultarActivos() {
        return pedidoService.consultarPedidosActivos();
    }

    @GetMapping("/historico")
    public List<PedidoConsultaResponse> consultarHistorico() {
        return pedidoService.consultarHistoricoPedidos();
    }

    @GetMapping("/{pedidoId}")
    public PedidoConsultaResponse consultarDetalle(@PathVariable String pedidoId) {
        return pedidoService.consultarPedido(pedidoId);
    }

    @PostMapping("/{pedidoId}/cancelar")
    public PedidoConsultaResponse cancelarPedido(@PathVariable String pedidoId) {
        return pedidoService.cancelarPedido(pedidoId);
    }

    @PostMapping("/{pedidoId}/confirmar-recepcion")
    public PedidoConsultaResponse confirmarRecepcion(
            @PathVariable String pedidoId) {
        return pedidoService.confirmarRecepcion(pedidoId);
    }

    @PostMapping("/{pedidoId}/productos/{productoId}/valoraciones")
    public ResponseEntity<ValoracionResponse> valorarProducto(
            @PathVariable String pedidoId,
            @PathVariable String productoId,
            @Valid @RequestBody CrearValoracionRequest request,
            Authentication authentication) {
        var valoracion = valoracionService.valorar(
                authentication.getName(),
                pedidoId,
                productoId,
                request.puntuacion(),
                request.comentario());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ValoracionResponse.de(valoracion));
    }
}
