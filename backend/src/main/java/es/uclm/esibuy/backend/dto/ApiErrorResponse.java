package es.uclm.esibuy.backend.dto;

import java.util.List;
import java.util.Map;

public record ApiErrorResponse(String error, Map<String, List<String>> errores) {
	public ApiErrorResponse(String error) {
		this(error, Map.of());
	}
}