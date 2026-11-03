package es.uclm.esibuy.backend.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class SpaController {

    @GetMapping({
            "/login",
            "/registro",
            "/recuperar-contrasena",
            "/perfil",
            "/perfil/direcciones",
            "/perfil/pagos",
            "/admin",
            "/admin/usuarios",
            "/admin/categorias",
            "/admin/administradores",
            "/admin/administradores/nuevo",
            "/cliente",
            "/cliente/favoritos",
            "/carrito",
            "/cliente/checkout",
            "/vendedor",
            "/vendedor/pedidos"
    })
    public String mostrarAngular() {
        return "forward:/index.html";
    }

    @GetMapping("/admin/usuarios/{id}")
    public String mostrarDetalleUsuario(@PathVariable String id) {
        return "forward:/index.html";
    }
}
