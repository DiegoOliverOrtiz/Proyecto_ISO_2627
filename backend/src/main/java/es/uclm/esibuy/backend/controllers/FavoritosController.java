package es.uclm.esibuy.backend.controllers;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.uclm.esibuy.backend.dto.FavoritosResponse;
import es.uclm.esibuy.backend.services.FavoritoService;

@RestController
@RequestMapping("/usuarios/favoritos")
public class FavoritosController {

    private final FavoritoService favoritoService;

    public FavoritosController(FavoritoService favoritoService) {
        this.favoritoService = favoritoService;
    }

    @GetMapping
    public FavoritosResponse consultar(
            Authentication authentication) {

        return favoritoService.consultar(authentication.getName());
    }

    @PutMapping("/{productoId}")
    public FavoritosResponse anadir(
            Authentication authentication,
            @PathVariable String productoId) {

        return favoritoService.anadir(
                authentication.getName(),
                productoId
        );
    }

    @DeleteMapping("/{productoId}")
    public FavoritosResponse quitar(
            Authentication authentication,
            @PathVariable String productoId) {

        return favoritoService.quitar(
                authentication.getName(),
                productoId
        );
    }
}
