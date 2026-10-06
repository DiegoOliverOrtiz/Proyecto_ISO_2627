package es.uclm.esibuy.backend.dto;

import java.util.List;

public record ListadoUsuariosResponse(
		List<UsuarioAdministracionResponse> contenido,
		int pagina,
		int tamano,
		long totalElementos,
		int totalPaginas,
		EstadisticasUsuariosResponse estadisticas) {
}