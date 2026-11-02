package es.uclm.esibuy.backend.controllers;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.uclm.esibuy.backend.dto.PedidoVendedorResponse;
import es.uclm.esibuy.backend.services.PedidosVendedorService;

@RestController
@RequestMapping("/vendedor/pedidos")
public class PedidosVendedorController {

    private final PedidosVendedorService pedidosVendedorService;

    public PedidosVendedorController(
            PedidosVendedorService pedidosVendedorService) {
        this.pedidosVendedorService = pedidosVendedorService;
    }

    @GetMapping("/activos")
    public List<PedidoVendedorResponse> consultarActivos(
            Authentication authentication) {

        return pedidosVendedorService.consultarActivos(authentication.getName()
        );
    }

    @GetMapping("/historico")
    public List<PedidoVendedorResponse> consultarHistorico(
            Authentication authentication) {

        return pedidosVendedorService.consultarHistorico(
                authentication.getName()
        );
    }

    @GetMapping("/{pedidoId}")
    public PedidoVendedorResponse consultarDetalle(
            Authentication authentication,
            @PathVariable String pedidoId) {

        return pedidosVendedorService.consultarDetalle(
                authentication.getName(),
                pedidoId
        );
    }
}

