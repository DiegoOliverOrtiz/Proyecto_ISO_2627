package es.uclm.esibuy.backend.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.uclm.esibuy.backend.dto.CategoriaRegistroResponse;
import es.uclm.esibuy.backend.dto.CrearCategoriaRequest;
import es.uclm.esibuy.backend.model.Categoria;
import es.uclm.esibuy.backend.services.CategoriaService;

@RestController
@RequestMapping("/categorias")
public class CategoriaController {

    private final CategoriaService categoriaService;

    public CategoriaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @GetMapping
    public List<Categoria> obtenerTodas() {
        return categoriaService.obtenerTodas();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Categoria> obtenerPorId(@PathVariable String id) {
        return categoriaService.obtenerPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/registro")
    public List<CategoriaRegistroResponse> obtenerCategoriasRegistro() {
        return categoriaService.obtenerTodas()
                .stream()
                .map(categoria -> new CategoriaRegistroResponse(
                        categoria.getId(),
                        categoria.getNombre()))
                .toList();
    }

    @PostMapping
    public Categoria crearCategoria(@RequestBody CrearCategoriaRequest request) {
        Categoria categoria = new Categoria(
                request.nombre(),
                request.descripcion());

        return categoriaService.crearCategoria(categoria);
    }
}