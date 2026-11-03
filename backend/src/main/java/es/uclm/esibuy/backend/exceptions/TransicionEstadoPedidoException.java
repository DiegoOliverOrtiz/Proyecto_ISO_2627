package es.uclm.esibuy.backend.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class TransicionEstadoPedidoException
        extends RuntimeException {

    public TransicionEstadoPedidoException() {
        super("Solo se pueden aceptar pedidos pagados pendientes de tramitar.");
    }
}
