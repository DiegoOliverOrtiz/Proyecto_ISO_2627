package es.uclm.esibuy.backend.dto;

import es.uclm.esibuy.backend.model.MetodoPago;

public record MetodoPagoResponse(
		String id,
		String tipo,
		String nombre,
		String datosEnmascarados,
		String titular,
		String caducidad) {

	public static MetodoPagoResponse de(MetodoPago metodo) {
		return new MetodoPagoResponse(
				metodo.getId(),
				metodo.getTipo(),
				metodo.getNombre(),
				metodo.getReferenciaEnmascarada(),
				metodo.getTitular(),
				metodo.getCaducidad());
	}
}