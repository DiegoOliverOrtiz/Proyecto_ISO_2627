
package es.uclm.esibuy.backend.services;

import org.springframework.security.access.AccessDeniedException;
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
    private final SoporteRegistro soporteRegistro;

    public VendedorService(
            VendedorDAO vendedorDAO,
            CategoriaDAO categoriaDAO,
            SoporteRegistro soporteRegistro) {

        this.vendedorDAO = vendedorDAO;
        this.categoriaDAO = categoriaDAO;
        this.soporteRegistro = soporteRegistro;
    }

    public RegistroUsuarioResponse registrar(RegistroVendedorRequest request) {
        if (request == null) {
            throw new InvalidRegistrationException();
        }
        soporteRegistro.validarSolicitud(request, request.contrasena());
		String email = soporteRegistro.normalizarCorreo(request.email());
        String nombreComercial = request.nombreComercial().trim();
        String categoriaId = request.categoriaId().trim();

        // La categoría seleccionada debe existir realmente en MongoDB.
        if (!categoriaDAO.existsById(categoriaId)) {
            throw new InvalidRegistrationException();
        }

        if (vendedorDAO.existsByNombreComercial(nombreComercial)) {
            throw new DuplicateRegistrationException();
        }
        soporteRegistro.comprobarCorreoDisponible(email);

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
        vendedor.registrarContrasenaHash(soporteRegistro.codificarContrasena(request.contrasena()));

        Vendedor guardado = soporteRegistro.guardarRegistro(() -> vendedorDAO.save(vendedor));
        return new RegistroUsuarioResponse(guardado.getId(), "vendedor");
    }

    public String obtenerNombreComercial(String email) {
        return vendedorDAO.findByEmail(email)
                .filter(vendedor -> vendedor.isActivada() && !vendedor.isBloqueada())
                .map(Vendedor::getNombreComercial)
                .orElseThrow(() -> new AccessDeniedException(
                        "Solo un vendedor activo puede consultar su perfil."));
    }
}
