package es.uclm.esibuy.backend.services;

import java.time.LocalDate;

import es.uclm.esibuy.backend.dao.CategoriaDAO;
import es.uclm.esibuy.backend.dto.ModificarUsuarioRequest;
import es.uclm.esibuy.backend.model.Administrador;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.Usuario;
import es.uclm.esibuy.backend.model.Vendedor;

/*
 * HU07 - Campos específicos de cada tipo de usuario que el administrador puede modificar.
 */
final class DatosRolUsuario {

	private DatosRolUsuario() {
	}

	static void modificar(Usuario usuario, ModificarUsuarioRequest request, CategoriaDAO categoriaDAO) {
		switch (usuario) {

			case Cliente cliente -> modificarDatosCliente(cliente, request);

			case Vendedor vendedor -> modificarDatosVendedor(vendedor, request, categoriaDAO);

			case Administrador administrador -> administrador.setSede(request.sede());

			default -> throw new IllegalStateException(
					"Tipo de usuario no soportado.");
		}
	}

	private static void modificarDatosVendedor(Vendedor vendedor, ModificarUsuarioRequest request,
			CategoriaDAO categoriaDAO) {
		if (request.dniNif() != null) {
			validarDniNif(request.dniNif());
			vendedor.setDniNif(request.dniNif());
		}

		if (request.categoriaId() != null) {
			String categoriaId = request.categoriaId().trim();
			if (categoriaId.isBlank() || !categoriaDAO.existsById(categoriaId)) {
				throw new IllegalArgumentException("La categoría seleccionada no existe.");
			}
			vendedor.setCategoriaId(categoriaId);
		}
	}

	private static void modificarDatosCliente(
			Cliente cliente,
			ModificarUsuarioRequest request) {

		if (request.fechaNacimiento() == null) {
			throw new IllegalArgumentException(
					"La fecha de nacimiento es obligatoria.");
		}

		LocalDate fechaLimite =
				LocalDate.now().minusYears(18);

		if (request.fechaNacimiento()
				.isAfter(fechaLimite)) {

			throw new IllegalArgumentException(
					"El cliente debe ser mayor de edad.");
		}

		if (request.dniNif() != null) {
			validarDniNif(request.dniNif());
			cliente.setDniNif(request.dniNif());
		}

		cliente.setFechaNacimiento(
				request.fechaNacimiento());

		if (request.esPremium() != null) {
			cliente.setEsPremium(request.esPremium());
		}
	}

	private static void validarDniNif(String dniNif) {
		if (!dniNif.matches("^\\d{8}[A-Za-z]$")) {
			throw new IllegalArgumentException("El DNI/NIF debe tener 8 números y una letra.");
		}
	}
}
