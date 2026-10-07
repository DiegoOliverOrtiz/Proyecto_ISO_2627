package es.uclm.esibuy.backend.services;

import org.springframework.stereotype.Service;

import es.uclm.esibuy.backend.dao.ClienteDAO;
import es.uclm.esibuy.backend.dto.RegistroClienteRequest;
import es.uclm.esibuy.backend.dto.RegistroUsuarioResponse;
import es.uclm.esibuy.backend.model.Cliente;

@Service
public class ClienteService {

	private final ClienteDAO clienteDAO;
	private final SoporteRegistro soporteRegistro;

	public ClienteService(ClienteDAO clienteDAO, SoporteRegistro soporteRegistro) {
		this.clienteDAO = clienteDAO;
		this.soporteRegistro = soporteRegistro;
	}

	public RegistroUsuarioResponse registrar(RegistroClienteRequest request) {
		soporteRegistro.validarSolicitud(request, request == null ? null : request.contrasena());
		String email = soporteRegistro.normalizarCorreo(request.email());
		soporteRegistro.comprobarCorreoDisponible(email);

		Cliente cliente = new Cliente();
		cliente.setNombre(request.nombre().trim());
		cliente.setApellidos(request.apellidos().trim());
		cliente.setEmail(email);
		cliente.setFechaNacimiento(request.fechaNacimiento());
		cliente.setDniNif(request.dniNif().trim());
		cliente.setTelefono(request.telefono());
		cliente.setImagenPerfil(request.imagenPerfil());
		cliente.setEsPremium(request.esPremium());
		cliente.setBloqueada(false);
		cliente.setActivada(false);
		cliente.registrarContrasenaHash(soporteRegistro.codificarContrasena(request.contrasena()));

		Cliente guardado = soporteRegistro.guardarRegistro(() -> clienteDAO.save(cliente));
		return new RegistroUsuarioResponse(guardado.getId(), "cliente");
	}
}