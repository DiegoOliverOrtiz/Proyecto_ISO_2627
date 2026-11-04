package es.uclm.esibuy.backend.exceptions;

public class CancelacionPedidoException extends RuntimeException {
    public CancelacionPedidoException(String mensaje) {
        super(mensaje);
    }
}
