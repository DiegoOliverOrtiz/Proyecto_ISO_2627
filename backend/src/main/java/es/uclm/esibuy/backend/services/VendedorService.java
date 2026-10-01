
package es.uclm.esibuy.backend.services;

import org.springframework.stereotype.Service;

import es.uclm.esibuy.backend.dao.CategoriaDAO;
import es.uclm.esibuy.backend.dao.VendedorDAO;
import es.uclm.esibuy.backend.dto.RegistroUsuarioResponse;
import es.uclm.esibuy.backend.dto.RegistroVendedorRequest;
import es.uclm.esibuy.backend.exceptions.DuplicateRegistrationException;
import es.uclm.esibuy.backend.exceptions.InvalidRegistrationException;
import es.uclm.esibuy.backend.model.Vendedor;

@Service
public class VendedorService {

    private final VendedorDAO vendedorDAO;
    private final CategoriaDAO categoriaDAO;
    private final RegistrationSupport registrationSupport;

    public VendedorService(
            VendedorDAO vendedorDAO,
            CategoriaDAO categoriaDAO,
            RegistrationSupport registrationSupport) {

        this.vendedorDAO = vendedorDAO;
        this.categoriaDAO = categoriaDAO;
        this.registrationSupport = registrationSupport;
    }

    public RegistroUsuarioResponse registrar(RegistroVendedorRequest request) {
		registrationSupport.validate(request, request == null ? null : request.contrasena());
		String email = registrationSupport.normalizeEmail(request.email());
        String nombreComercial = request.nombreComercial().trim();
        String categoriaId = request.categoriaId().trim();

        // La categoría seleccionada debe existir realmente en MongoDB.
        if (!categoriaDAO.existsById(categoriaId)) {
            throw new InvalidRegistrationException();
        }

        if (vendedorDAO.existsByNombreComercial(nombreComercial)) {
            throw new DuplicateRegistrationException();
        }
        registrationSupport.ensureEmailIsAvailable(email);

        Vendedor vendedor = new Vendedor();

        vendedor.setNombre(request.nombre().trim());
        vendedor.setApellidos(request.apellidos().trim());
        vendedor.setEmail(email);
        vendedor.setCategoriaId(categoriaId);
        vendedor.setDniNif(request.dniNif().trim());
        vendedor.setNombreComercial(nombreComercial);
        vendedor.setTelefono(request.telefono());
        vendedor.setImagenPerfil(request.imagenPerfil());
        vendedor.setBloqueada(false);
        vendedor.setActivada(false);
        vendedor.registrarContrasenaHash(registrationSupport.encodePassword(request.contrasena()));

        Vendedor guardado = registrationSupport.save(() -> vendedorDAO.save(vendedor));
        return new RegistroUsuarioResponse(guardado.getId(), "vendedor");
    }
}
