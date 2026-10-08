package es.uclm.esibuy.backend.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import es.uclm.esibuy.backend.dto.ConsultarProductosResponse;
import es.uclm.esibuy.backend.dto.CrearProductoRequest;
import es.uclm.esibuy.backend.dto.CrearProductoResponse;
import es.uclm.esibuy.backend.dto.NombreComercialResponse;
import es.uclm.esibuy.backend.dto.RegistroUsuarioResponse;
import es.uclm.esibuy.backend.dto.RegistroVendedorRequest;
import es.uclm.esibuy.backend.services.ProductoService;
import es.uclm.esibuy.backend.services.VendedorService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/vendedor")
public class VendedorController {
    private final VendedorService vendedorService;
    private final ProductoService productoService;

    public VendedorController(VendedorService vendedorService, ProductoService productoService) {
        this.vendedorService = vendedorService;
        this.productoService = productoService;
    }

    @GetMapping("/productos")
    public List<ConsultarProductosResponse> consultarProductos(Authentication authentication) {
        return productoService.consultarProductosPropios(authentication.getName());
    }

    @PostMapping("/productos")
    public ResponseEntity<CrearProductoResponse> crearProducto(
            Authentication authentication,
            @Valid @RequestPart("producto") CrearProductoRequest request,
            @RequestPart(value = "imagen", required = false) MultipartFile imagen) {
        CrearProductoResponse response = productoService.crearProducto(authentication.getName(), request, imagen);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/register")
    public ResponseEntity<RegistroUsuarioResponse> register(@Valid @RequestBody RegistroVendedorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(vendedorService.registrar(request));
    }

    @GetMapping("/perfil")
    public NombreComercialResponse mostrarNombreComercial(Authentication authentication) {
        String nombreComercial = vendedorService.obtenerNombreComercial(authentication.getName());
        return new NombreComercialResponse(nombreComercial);
    }
}
