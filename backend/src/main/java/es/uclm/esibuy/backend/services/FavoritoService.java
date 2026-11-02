package es.uclm.esibuy.backend.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import es.uclm.esibuy.backend.dao.ListaProductosDAO;
import es.uclm.esibuy.backend.dao.ProductoDAO;
import es.uclm.esibuy.backend.dao.UsuarioDAO;
import es.uclm.esibuy.backend.dto.FavoritosResponse;
import es.uclm.esibuy.backend.dto.ProductoFavoritoResponse;
import es.uclm.esibuy.backend.exceptions.ProductoNoEncontradoException;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.ListaProductos;
import es.uclm.esibuy.backend.model.Producto;
import es.uclm.esibuy.backend.model.Usuario;

@Service
public class FavoritoService {

    private static final int MAX_INTENTOS = 3;

    private final ListaProductosDAO listaProductosDAO;
    private final UsuarioDAO usuarioDAO;
    private final ProductoDAO productoDAO;

    public FavoritoService(
            ListaProductosDAO listaProductosDAO,
            UsuarioDAO usuarioDAO,
            ProductoDAO productoDAO) {

        this.listaProductosDAO = listaProductosDAO;
        this.usuarioDAO = usuarioDAO;
        this.productoDAO = productoDAO;
    }

    public FavoritosResponse consultar(String correoAutenticado) {
        Usuario usuario = obtenerUsuario(correoAutenticado);

        return construirRespuesta(
                obtenerOCrearLista(usuario.getId()),
                usuario
        );
    }

    public FavoritosResponse anadir(
            String correoAutenticado,
            String productoId) {

        validarIdProducto(productoId);

        Producto producto = productoDAO.findById(productoId)
                .filter(Producto::isEsVisible)
                .orElseThrow(ProductoNoEncontradoException::new);

        return modificar(correoAutenticado, producto.getId(), true);
    }

    public FavoritosResponse quitar(
            String correoAutenticado,
            String productoId) {

        validarIdProducto(productoId);

        return modificar(correoAutenticado, productoId, false);
    }

    private FavoritosResponse modificar(
            String correo,
            String productoId,
            boolean anadir) {

        Usuario usuario = obtenerUsuario(correo);

        for (int intento = 0; intento < MAX_INTENTOS; intento++) {

            ListaProductos lista = obtenerOCrearLista(usuario.getId());

            boolean cambio = anadir
                    ? lista.agregarProducto(productoId)
                    : lista.quitarProducto(productoId);

            if (!cambio) {
                return construirRespuesta(lista, usuario);
            }

            try {
                listaProductosDAO.save(lista);
                return construirRespuesta(lista, usuario);

            } catch (OptimisticLockingFailureException
                    | DuplicateKeyException excepcion) {

                if (intento == MAX_INTENTOS - 1) {
                    throw excepcion;
                }
            }
        }

        throw new IllegalStateException(
                "No se han podido actualizar los favoritos."
        );
    }

    private Usuario obtenerUsuario(String correo) {

        if (correo == null || correo.isBlank()) {
            throw new AccessDeniedException(
                    "No se puede acceder a favoritos."
            );
        }

        return usuarioDAO.findByEmail(
                correo.trim().toLowerCase(Locale.ROOT))
                .filter(usuario ->
                        usuario.isActivada() && !usuario.isBloqueada())
                .orElseThrow(() -> new AccessDeniedException(
                        "No se puede acceder a favoritos."
                ));
    }

    private ListaProductos obtenerOCrearLista(String usuarioId) {

        String idLista = "favoritos:" + usuarioId;

        return listaProductosDAO.findById(idLista)
                .orElseGet(() -> {
                    try {
                        return listaProductosDAO.save(
                                ListaProductos.crearFavoritos(usuarioId)
                        );

                    } catch (DuplicateKeyException
                            | OptimisticLockingFailureException excepcion) {

                        return listaProductosDAO.findById(idLista)
                                .orElseThrow(() -> excepcion);
                    }
                });
    }

    private FavoritosResponse construirRespuesta(
            ListaProductos lista,
            Usuario usuario) {

        List<String> ids = new ArrayList<>(lista.getProductos());

        Map<String, Producto> productos = new HashMap<>();

        productoDAO.findAllById(ids).forEach(
                producto -> productos.put(producto.getId(), producto)
        );

        boolean premium = usuario instanceof Cliente cliente
                && cliente.isEsPremium();

        List<ProductoFavoritoResponse> detalles = ids.stream()
                .map(id -> presentarProducto(
                        id,
                        productos.get(id),
                        premium
                ))
                .toList();

        return new FavoritosResponse(
                List.copyOf(ids),
                detalles
        );
    }

    private ProductoFavoritoResponse presentarProducto(
            String id,
            Producto producto,
            boolean premium) {

        if (producto == null) {
            return new ProductoFavoritoResponse(
                    id,
                    "Producto eliminado",
                    null,
                    null,
                    null,
                    false,
                    "Producto no disponible"
            );
        }

        String aviso = null;

        if (!producto.isEsVisible()) {
            aviso = "Producto no disponible";

        } else if (producto.isSoloPremium() && !premium) {
            aviso = "Solo disponible para clientes Premium";

        } else if (producto.getStock() <= 0) {
            aviso = "Sin stock";
        }

        Double precio = producto.isSoloPremium() && !premium
                ? null
                : ProductoService.calcularPrecioParaCliente(
                        producto, premium
                );

        return new ProductoFavoritoResponse(
                id,
                producto.getNombre(),
                producto.getImagen(),
                precio,
                producto.getStock(),
                aviso == null,
                aviso
        );
    }

    private void validarIdProducto(String productoId) {

        if (productoId == null || productoId.isBlank()) {
            throw new IllegalArgumentException(
                    "El identificador del producto es obligatorio."
            );
        }
    }
}
