package es.uclm.esibuy.backend.controllers;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.uclm.esibuy.backend.dto.AnadirProductoCarritoRequest;
import es.uclm.esibuy.backend.dto.CarritoResponse;
import es.uclm.esibuy.backend.dto.ModificarCantidadCarritoRequest;
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

    @GetMapping
    public CarritoResponse consultarCarrito() {
        return carritoService.consultarCarrito();
    }

    @PutMapping("/productos/{productoId}")
    public CarritoResponse modificarCantidad(@PathVariable String productoId,
            @Valid @RequestBody ModificarCantidadCarritoRequest request) {
        return carritoService.modificarCantidad(productoId, request.cantidad().intValueExact());
    }

    @DeleteMapping("/productos/{productoId}")
    public CarritoResponse eliminarProducto(@PathVariable String productoId) {
        return carritoService.eliminarProducto(productoId);
    }

    @DeleteMapping
    public CarritoResponse descartarCarrito() {
        return carritoService.descartarCarrito();
    }
}
