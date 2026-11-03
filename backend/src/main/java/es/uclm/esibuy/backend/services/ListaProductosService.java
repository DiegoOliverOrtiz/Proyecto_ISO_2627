package es.uclm.esibuy.backend.services;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import es.uclm.esibuy.backend.dao.ListaProductosDAO;
import es.uclm.esibuy.backend.dao.ProductoDAO;
import es.uclm.esibuy.backend.dao.UsuarioDAO;
import es.uclm.esibuy.backend.dto.ListaProductosResponse;
import es.uclm.esibuy.backend.dto.ProductoFavoritoResponse;
import es.uclm.esibuy.backend.dto.ResumenListaProductosResponse;
import es.uclm.esibuy.backend.exceptions.ListaNoEncontradaException;
import es.uclm.esibuy.backend.exceptions.NombreListaDuplicadoException;
import es.uclm.esibuy.backend.exceptions.OperacionListaNoPermitidaException;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.ListaProductos;
import es.uclm.esibuy.backend.model.Producto;
import es.uclm.esibuy.backend.model.Usuario;

/**
 * HU-39: listas de productos del usuario autenticado. Los favoritos (HU-31) son
 * la lista predeterminada: comparten modelo, pero no se renombran ni eliminan.
 */
@Service
public class ListaProductosService {

    static final int MAX_NOMBRE = 50;
    static final int MAX_LISTAS = 20;
    private static final String NOMBRE_RESERVADO = "favoritos";

    private final ListaProductosDAO listaProductosDAO;
    private final UsuarioDAO usuarioDAO;
    private final ProductoDAO productoDAO;
    private final FavoritosRegistroService favoritosRegistroService;

    public ListaProductosService(
            ListaProductosDAO listaProductosDAO,
            UsuarioDAO usuarioDAO,
            ProductoDAO productoDAO,
            FavoritosRegistroService favoritosRegistroService) {

        this.listaProductosDAO = listaProductosDAO;
        this.usuarioDAO = usuarioDAO;
        this.productoDAO = productoDAO;
        this.favoritosRegistroService = favoritosRegistroService;
    }

    public List<ResumenListaProductosResponse> consultarListas(String correoAutenticado) {
        Usuario usuario = obtenerUsuario(correoAutenticado);
        favoritosRegistroService.crearListaPredeterminada(usuario.getId());

        return listaProductosDAO.findByUsuarioId(usuario.getId()).stream()
                .sorted(Comparator.comparing(ListaProductos::isPredeterminada).reversed()
                        .thenComparing(lista -> lista.getNombre().toLowerCase(Locale.ROOT)))
                .map(lista -> new ResumenListaProductosResponse(
                        lista.getId(),
                        lista.getNombre(),
                        lista.isPredeterminada(),
                        List.copyOf(lista.getProductos())))
                .toList();
    }

    public ListaProductosResponse consultarLista(String correoAutenticado, String listaId) {
        Usuario usuario = obtenerUsuario(correoAutenticado);
        return construirRespuesta(obtenerListaPropia(usuario, listaId), usuario);
    }

    public ListaProductosResponse crear(String correoAutenticado, String nombre) {
        Usuario usuario = obtenerUsuario(correoAutenticado);
        String nombreValido = validarNombre(nombre);

        favoritosRegistroService.crearListaPredeterminada(usuario.getId());

        if (listaProductosDAO.countByUsuarioId(usuario.getId()) >= MAX_LISTAS) {
            throw new OperacionListaNoPermitidaException(
                    "Has alcanzado el máximo de " + MAX_LISTAS + " listas.");
        }

        comprobarNombreLibre(usuario.getId(), nombreValido, null);

        ListaProductos lista = listaProductosDAO.save(
                ListaProductos.crearLista(usuario.getId(), nombreValido));

        return construirRespuesta(lista, usuario);
    }

    public ListaProductosResponse renombrar(
            String correoAutenticado,
            String listaId,
            String nombre) {

        Usuario usuario = obtenerUsuario(correoAutenticado);
        ListaProductos lista = obtenerListaPropia(usuario, listaId);

        if (lista.isPredeterminada()) {
            throw new OperacionListaNoPermitidaException(
                    "La lista de favoritos no se puede renombrar.");
        }

        String nombreValido = validarNombre(nombre);
        comprobarNombreLibre(usuario.getId(), nombreValido, lista.getId());

        lista.renombrar(nombreValido);

        return construirRespuesta(listaProductosDAO.save(lista), usuario);
    }

    public void eliminar(String correoAutenticado, String listaId) {
        Usuario usuario = obtenerUsuario(correoAutenticado);
        ListaProductos lista = obtenerListaPropia(usuario, listaId);

        if (lista.isPredeterminada()) {
            throw new OperacionListaNoPermitidaException(
                    "La lista de favoritos no se puede eliminar.");
        }

        // Solo se borra la lista: los productos siguen en el catálogo.
        listaProductosDAO.delete(lista);
    }

    private Usuario obtenerUsuario(String correo) {
        if (correo == null || correo.isBlank()) {
            throw new AccessDeniedException("No se puede acceder a las listas.");
        }

        return usuarioDAO.findByEmail(correo.trim().toLowerCase(Locale.ROOT))
                .filter(usuario -> usuario.isActivada() && !usuario.isBloqueada())
                .orElseThrow(() -> new AccessDeniedException(
                        "No se puede acceder a las listas."));
    }

    // Una lista ajena se trata igual que una inexistente para no revelar que existe.
    private ListaProductos obtenerListaPropia(Usuario usuario, String listaId) {
        if (listaId == null || listaId.isBlank()) {
            throw new ListaNoEncontradaException();
        }

        return listaProductosDAO.findById(listaId)
                .filter(lista -> usuario.getId().equals(lista.getUsuarioId()))
                .orElseThrow(ListaNoEncontradaException::new);
    }

    private String validarNombre(String nombre) {
        String recortado = nombre == null ? "" : nombre.trim();

        if (recortado.isEmpty()) {
            throw new IllegalArgumentException("El nombre de la lista es obligatorio.");
        }

        if (recortado.length() > MAX_NOMBRE) {
            throw new IllegalArgumentException(
                    "El nombre de la lista no puede superar " + MAX_NOMBRE + " caracteres.");
        }

        if (recortado.toLowerCase(Locale.ROOT).equals(NOMBRE_RESERVADO)) {
            throw new NombreListaDuplicadoException();
        }

        return recortado;
    }

    private void comprobarNombreLibre(String usuarioId, String nombre, String listaIdActual) {
        String normalizado = nombre.toLowerCase(Locale.ROOT);

        boolean repetido = listaProductosDAO.findByUsuarioId(usuarioId).stream()
                .filter(lista -> !lista.getId().equals(listaIdActual))
                .anyMatch(lista -> lista.getNombre().toLowerCase(Locale.ROOT).equals(normalizado));

        if (repetido) {
            throw new NombreListaDuplicadoException();
        }
    }

    private ListaProductosResponse construirRespuesta(ListaProductos lista, Usuario usuario) {
        List<String> ids = new ArrayList<>(lista.getProductos());
        Map<String, Producto> productos = new HashMap<>();

        if (!ids.isEmpty()) {
            productoDAO.findAllById(ids).forEach(
                    producto -> productos.put(producto.getId(), producto));
        }

        boolean premium = usuario instanceof Cliente cliente && cliente.isEsPremium();

        List<ProductoFavoritoResponse> detalles = ids.stream()
                .map(id -> FavoritoService.presentarProducto(id, productos.get(id), premium))
                .toList();

        return new ListaProductosResponse(
                lista.getId(),
                lista.getNombre(),
                lista.isPredeterminada(),
                detalles);
    }
}
