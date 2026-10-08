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
        validarCampos(categoria.getNombre(), categoria.getDescripcion());
        categoria.setNombre(categoria.getNombre().trim());
        categoria.setDescripcion(categoria.getDescripcion().trim());

        if (categoriaDAO.existsByNombreIgnoreCase(categoria.getNombre())) {
            throw new NombreCategoriaDuplicadoException();
        }

        return categoriaDAO.save(categoria);
    }

    public Categoria modificarCategoria(String id, ModificarCategoriaRequest request) {
        Categoria categoria = categoriaDAO.findById(id)
                .orElseThrow(CategoriaNoEncontradaException::new);

        validarCampos(request.nombre(), request.descripcion());
        String nombre = request.nombre().trim();
        String descripcion = request.descripcion().trim();

        if (categoriaDAO.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw new NombreCategoriaDuplicadoException();
        }

        categoria.setNombre(nombre);
        categoria.setDescripcion(descripcion);

        return categoriaDAO.save(categoria);
    }

    private void validarCampos(String nombre, String descripcion) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre es obligatorio");
        }
        if (descripcion == null || descripcion.isBlank()) {
            throw new IllegalArgumentException("La descripcion es obligatoria");
        }
    }
}
