package es.uclm.esibuy.backend.controllers;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.uclm.esibuy.backend.dto.ValoracionesProductoResponse;
import es.uclm.esibuy.backend.services.ProductoService;
import es.uclm.esibuy.backend.services.ValoracionService;

@RestController
@RequestMapping("/cliente/productos")
public class ValoracionesProductoController {
    private final ProductoService productoService;
    private final ValoracionService valoracionService;

    public ValoracionesProductoController(
            ProductoService productoService,
            ValoracionService valoracionService) {
        this.productoService = productoService;
        this.valoracionService = valoracionService;
    }

    @GetMapping("/{productoId}/valoraciones")
    public ValoracionesProductoResponse consultarValoraciones(
            @PathVariable String productoId,
            Authentication authentication) {
        productoService.consultarDetalleParaCliente(authentication.getName(), productoId);
        return valoracionService.consultarValoracionesProducto(productoId);
    }
}
