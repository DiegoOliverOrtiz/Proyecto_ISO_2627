package es.uclm.esibuy.backend.services;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import es.uclm.esibuy.backend.dao.CategoriaDAO;
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
        return categoriaDAO.save(categoria);
    }
}