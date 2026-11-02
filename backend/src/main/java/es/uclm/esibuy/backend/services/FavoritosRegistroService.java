package es.uclm.esibuy.backend.services;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import es.uclm.esibuy.backend.dao.ListaProductosDAO;
import es.uclm.esibuy.backend.model.ListaProductos;

@Service
public class FavoritosRegistroService {

    private final ListaProductosDAO listaProductosDAO;

    public FavoritosRegistroService(
            ListaProductosDAO listaProductosDAO) {
        this.listaProductosDAO = listaProductosDAO;
    }

    public void crearListaPredeterminada(String usuarioId) {
        if (usuarioId == null || usuarioId.isBlank()) {
            throw new IllegalArgumentException(
                    "El identificador del usuario es obligatorio."
            );
        }

        ListaProductos favoritos =
                ListaProductos.crearFavoritos(usuarioId);

        if (listaProductosDAO.existsById(favoritos.getId())) {
            return;
        }

        try {
            listaProductosDAO.save(favoritos);
        } catch (DuplicateKeyException excepcion) {
            // Otra petición pudo crear la misma lista.
            if (!listaProductosDAO.existsById(favoritos.getId())) {
                throw excepcion;
            }
        }
    }
}
