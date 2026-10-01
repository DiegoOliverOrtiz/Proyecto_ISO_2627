
package es.uclm.esibuy.backend.controllers;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.uclm.esibuy.backend.dto.CategoriaRegistroResponse;
import es.uclm.esibuy.backend.services.CategoriaService;

@RestController
@RequestMapping("/categorias")
public class CategoriaController {

    private final CategoriaService categoriaService;

    public CategoriaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @GetMapping("/registro")
    public List<CategoriaRegistroResponse> obtenerCategoriasRegistro() {
        return categoriaService.obtenerTodas().stream()
                .map(categoria -> new CategoriaRegistroResponse(
                        categoria.getId(),
                        categoria.getNombre()))
                .toList();
    }
}
