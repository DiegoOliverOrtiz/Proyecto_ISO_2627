package es.uclm.esibuy.backend.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.uclm.esibuy.backend.dto.ListaProductosResponse;
import es.uclm.esibuy.backend.dto.NombreListaRequest;
import es.uclm.esibuy.backend.dto.ResumenListaProductosResponse;
import es.uclm.esibuy.backend.services.ListaProductosService;
import jakarta.validation.Valid;

// HU-39: el propietario siempre es el usuario de la sesión, nunca un dato de la petición.
@RestController
@RequestMapping("/usuarios/listas")
public class ListasProductosController {

    private final ListaProductosService listaProductosService;

    public ListasProductosController(ListaProductosService listaProductosService) {
        this.listaProductosService = listaProductosService;
    }

    @GetMapping
    public List<ResumenListaProductosResponse> consultarListas(Authentication authentication) {
        return listaProductosService.consultarListas(authentication.getName());
    }

    @PostMapping
    public ResponseEntity<ListaProductosResponse> crear(
            Authentication authentication,
            @Valid @RequestBody NombreListaRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(listaProductosService.crear(authentication.getName(), request.nombre()));
    }

    @GetMapping("/{listaId}")
    public ListaProductosResponse consultarLista(
            Authentication authentication,
            @PathVariable String listaId) {

        return listaProductosService.consultarLista(authentication.getName(), listaId);
    }

    @PutMapping("/{listaId}")
    public ListaProductosResponse renombrar(
            Authentication authentication,
            @PathVariable String listaId,
            @Valid @RequestBody NombreListaRequest request) {

        return listaProductosService.renombrar(
                authentication.getName(), listaId, request.nombre());
    }

    @DeleteMapping("/{listaId}")
    public ResponseEntity<Void> eliminar(
            Authentication authentication,
            @PathVariable String listaId) {

        listaProductosService.eliminar(authentication.getName(), listaId);
        return ResponseEntity.noContent().build();
    }
}
