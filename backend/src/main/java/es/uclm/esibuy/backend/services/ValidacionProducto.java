package es.uclm.esibuy.backend.services;

import es.uclm.esibuy.backend.dao.CategoriaDAO;
import es.uclm.esibuy.backend.dto.DatosProductoRequest;
import es.uclm.esibuy.backend.exceptions.InvalidProductException;
import es.uclm.esibuy.backend.model.Producto;
import jakarta.validation.Validator;

/*
 * HU-17 y HU-18 - Validación de los datos que envía el vendedor al crear o modificar un producto.
 */
final class ValidacionProducto {

    private ValidacionProducto() {
    }

    // Devuelve el nombre de la categoría normalizado si los datos son válidos.
    static String validarDatos(DatosProductoRequest request, Validator validar, CategoriaDAO categoriaDAO) {
        if (request == null || !validar.validate(request).isEmpty()) {
            throw new InvalidProductException();
        }

        String nombreCategoria = request.categoria() == null ? "" : request.categoria().trim();
        if (nombreCategoria.isBlank() || !categoriaDAO.existsByNombreIgnoreCase(nombreCategoria)) {
            throw new InvalidProductException();
        }

        if (!sonValoresValidos(request)) {
            throw new InvalidProductException();
        }
        return nombreCategoria;
    }

    // Al crear el stock inicial debe ser mayor que 0 (al modificar se admite 0).
    static void validarStockInicial(DatosProductoRequest request) {
        if (request.stock() <= 0) {
            throw new InvalidProductException();
        }
    }

    static void asignarDatos(Producto producto, DatosProductoRequest request, String nombreCategoria) {
        producto.setNombre(request.nombre().trim());
        producto.setCategoria(nombreCategoria);
        producto.setDescripcion(request.descripcion() == null ? null : request.descripcion().trim());
        producto.setPrecio(request.precio());
        producto.setStock(request.stock());
        producto.setDescuento(request.descuento());
        producto.setDescuentoPremium(request.descuentoPremium());
    }

    private static boolean sonValoresValidos(DatosProductoRequest request) {
        return request.nombre() != null && !request.nombre().isBlank()
                && request.precio() != null && request.precio() > 0
                && request.stock() >= 0
                && esDescuentoValido(request.descuento())
                && esDescuentoValido(request.descuentoPremium());
    }

    private static boolean esDescuentoValido(int descuento) {
        return descuento >= 0 && descuento <= 100;
    }
}
