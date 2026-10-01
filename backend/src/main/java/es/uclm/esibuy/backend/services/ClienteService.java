package es.uclm.esibuy.backend.services;

import org.springframework.stereotype.Service;

import es.uclm.esibuy.backend.dao.ClienteDAO;
import es.uclm.esibuy.backend.dto.RegistroClienteRequest;
import es.uclm.esibuy.backend.dto.RegistroUsuarioResponse;
import es.uclm.esibuy.backend.exceptions.DuplicateRegistrationException;
import es.uclm.esibuy.backend.model.Cliente;

@Service
public class ClienteService {

	private final ClienteDAO clienteDAO;
	private final RegistrationSupport registrationSupport;

	public ClienteService(ClienteDAO clienteDAO, RegistrationSupport registrationSupport) {
		this.clienteDAO = clienteDAO;
		this.registrationSupport = registrationSupport;
	}

	public RegistroUsuarioResponse registrar(RegistroClienteRequest request) {
		registrationSupport.validate(request, request == null ? null : request.contrasena());
		String email = registrationSupport.normalizeEmail(request.email());
		registrationSupport.ensureEmailIsAvailable(email);

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
		cliente.registrarContrasenaHash(registrationSupport.encodePassword(request.contrasena()));

		Cliente guardado = registrationSupport.save(() -> clienteDAO.save(cliente));
		return new RegistroUsuarioResponse(guardado.getId(), "cliente");
	}
}