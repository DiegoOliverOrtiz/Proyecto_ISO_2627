package es.uclm.esibuy.backend.controllers;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.uclm.esibuy.backend.dto.AnadirProductoCarritoRequest;
import es.uclm.esibuy.backend.dto.CarritoResponse;
import es.uclm.esibuy.backend.services.CarritoService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/cliente/carrito")
public class CarritoController {
    private final CarritoService carritoService;

    public CarritoController(CarritoService carritoService) {
        this.carritoService = carritoService;
    }

    @PostMapping("/productos")
    public CarritoResponse anadirProducto(@Valid @RequestBody AnadirProductoCarritoRequest request) {
        return carritoService.anadirProductoConImporte(request.productoId(), request.cantidad());
    }
}
