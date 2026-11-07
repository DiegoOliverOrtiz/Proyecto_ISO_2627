package es.uclm.esibuy.backend.exceptions;

public class ProductoConPedidosEnCursoException extends RuntimeException {
    public ProductoConPedidosEnCursoException() {
        super("No se puede eliminar el producto porque figura en pedidos en curso (pagados o enviados).");
    }
}
