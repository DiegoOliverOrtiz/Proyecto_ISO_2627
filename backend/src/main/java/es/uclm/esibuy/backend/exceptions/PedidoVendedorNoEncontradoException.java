package es.uclm.esibuy.backend.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class PedidoVendedorNoEncontradoException
        extends RuntimeException {

    public PedidoVendedorNoEncontradoException() {
        super("El pedido no existe o no está disponible.");
    }
}
