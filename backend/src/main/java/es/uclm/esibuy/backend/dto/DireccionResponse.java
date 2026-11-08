package es.uclm.esibuy.backend.dto;

import es.uclm.esibuy.backend.model.Direccion;

public record DireccionResponse(
		String id,
		String nombre,
		String calle,
		String numero,
		String portal,
		String piso,
		String codigoPostal,
		String ciudad,
		String provincia) {

	public static DireccionResponse de(Direccion direccion) {
		return new DireccionResponse(
				direccion.getId(),
				direccion.getNombre(),
				direccion.getCalle(),
				direccion.getNumero(),
				direccion.getPortal(),
				direccion.getPiso(),
				direccion.getCodigoPostal(),
				direccion.getCiudad(),
				direccion.getProvincia());
	}
}