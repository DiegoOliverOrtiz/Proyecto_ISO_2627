package es.uclm.esibuy.backend.exceptions;

public class CorreoYaRegistradoException extends RuntimeException {
    public CorreoYaRegistradoException() {
        super("No se pudo completar la solicitud de registro.");
    }
}