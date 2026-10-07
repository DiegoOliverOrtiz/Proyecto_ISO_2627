package es.uclm.esibuy.backend.dto;

public record EstadisticasUsuariosResponse(
		long totalUsuarios,
		long activos,
		long pendientes,
		long desactivados) {
}