package es.uclm.esibuy.backend.exceptions;

public class OperacionPedidoConflictivaException extends RuntimeException {
    public OperacionPedidoConflictivaException() {
        super("La clave de operación ya está utilizada por otro pedido.");
    }
}
