package es.uclm.esibuy.backend.controllers;

import com.mongodb.MongoException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import es.uclm.esibuy.backend.dto.ApiErrorResponse;
import es.uclm.esibuy.backend.exceptions.CancelacionPedidoException;
import es.uclm.esibuy.backend.exceptions.OperacionPedidoConflictivaException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = PedidoController.class)
public class PedidoExceptionHandler {
    @ExceptionHandler(CancelacionPedidoException.class)
    public ResponseEntity<ApiErrorResponse> cancelacionNoPermitida(
            CancelacionPedidoException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> accesoDenegado() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiErrorResponse("Solo un cliente activo puede realizar pedidos."));
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, DuplicateKeyException.class,
            OperacionPedidoConflictivaException.class})
    public ResponseEntity<ApiErrorResponse> conflicto() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErrorResponse(
                "La compra ha cambiado o se está procesando. Revisa el carrito y repite con la misma clave de operación."));
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class, MongoException.class})
    public ResponseEntity<ApiErrorResponse> persistencia(RuntimeException exception) {
        boolean commitIncierto = false;
        for (Throwable causa = exception; causa != null; causa = causa.getCause()) {
            if (causa instanceof MongoException mongo
                    && mongo.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)) {
                commitIncierto = true;
                break;
            }
        }
        String mensaje = commitIncierto
                ? "No se ha podido confirmar el resultado del pago. Repite la petición con la misma clave de operación."
                : "No se ha podido completar la compra. Repite la petición con la misma clave de operación.";
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiErrorResponse(mensaje));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> peticionInvalida() {
        return ResponseEntity.badRequest().body(new ApiErrorResponse("El cuerpo de la petición no es válido."));
    }
}
