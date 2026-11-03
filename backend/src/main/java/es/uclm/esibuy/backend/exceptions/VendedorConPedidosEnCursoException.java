package es.uclm.esibuy.backend.exceptions;

public class VendedorConPedidosEnCursoException extends RuntimeException {
    public VendedorConPedidosEnCursoException() {
        super("No puedes darte de baja porque tienes pedidos en curso.");
    }
}
