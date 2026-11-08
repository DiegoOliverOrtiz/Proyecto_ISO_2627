package es.uclm.esibuy.backend.exceptions;

import java.util.List;
import java.util.Map;

public class MetodoPagoInvalidoException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final transient Map<String, List<String>> errores;

	public MetodoPagoInvalidoException(Map<String, List<String>> errores) {
		super("Revisa los datos del método de pago.");
		this.errores = Map.copyOf(errores);
	}

	public Map<String, List<String>> getErrores() {
		return errores;
	}
}