package es.uclm.esibuy.backend.services;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import es.uclm.esibuy.backend.dao.CategoriaDAO;
import es.uclm.esibuy.backend.dto.ModificarCategoriaRequest;
import es.uclm.esibuy.backend.exceptions.CategoriaNoEncontradaException;
import es.uclm.esibuy.backend.exceptions.NombreCategoriaDuplicadoException;
import es.uclm.esibuy.backend.model.Categoria;

@Service
public class CategoriaService {

    private final CategoriaDAO categoriaDAO;

    public CategoriaService(CategoriaDAO categoriaDAO) {
        this.categoriaDAO = categoriaDAO;
    }

    public List<Categoria> obtenerTodas() {
        return categoriaDAO.findAll();
    }

    public Optional<Categoria> obtenerPorId(String id) {
        return categoriaDAO.findById(id);
    }

    public Categoria crearCategoria(Categoria categoria) {
        if (categoria.getNombre() == null || categoria.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre es obligatorio");
        }

        if (categoria.getDescripcion() == null || categoria.getDescripcion().isBlank()) {
            throw new IllegalArgumentException("La descripcion es obligatoria");
        }

        if (categoriaDAO.existsByNombreIgnoreCase(categoria.getNombre())) {
            throw new IllegalArgumentException("Ya existe una categoria con ese nombre");
        }

        return categoriaDAO.save(categoria);
    }

    public Categoria modificarCategoria(String id, ModificarCategoriaRequest request) {
        Categoria categoria = categoriaDAO.findById(id)
                .orElseThrow(CategoriaNoEncontradaException::new);

        if (request.nombre() == null || request.nombre().isBlank()) {
            throw new IllegalArgumentException("El nombre es obligatorio");
        }

        if (request.descripcion() == null || request.descripcion().isBlank()) {
            throw new IllegalArgumentException("La descripcion es obligatoria");
        }

        if (categoriaDAO.existsByNombreIgnoreCaseAndIdNot(request.nombre(), id)) {
            throw new NombreCategoriaDuplicadoException();
        }

        categoria.setNombre(request.nombre());
        categoria.setDescripcion(request.descripcion());

        return categoriaDAO.save(categoria);
    }
}