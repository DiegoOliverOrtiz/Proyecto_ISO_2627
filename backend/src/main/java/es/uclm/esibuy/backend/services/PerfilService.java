package es.uclm.esibuy.backend.services;

import org.springframework.stereotype.Service;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import es.uclm.esibuy.backend.dao.UsuarioDAO;
import es.uclm.esibuy.backend.dto.ModificarUsuarioRequest;
import es.uclm.esibuy.backend.dto.PerfilResponse;
import es.uclm.esibuy.backend.exceptions.UsuarioNoEncontradoException;
import es.uclm.esibuy.backend.model.Administrador;
import es.uclm.esibuy.backend.model.Categoria;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.Usuario;
import es.uclm.esibuy.backend.model.Vendedor;

@Service
public class PerfilService {

    private final UsuarioDAO usuarioDAO;
    private final MongoTemplate mongoTemplate;
    private final AdministradorService administradorService;

    public PerfilService(
            UsuarioDAO usuarioDAO,
            MongoTemplate mongoTemplate,
            AdministradorService administradorService) {
        this.usuarioDAO = usuarioDAO;
        this.mongoTemplate = mongoTemplate;
        this.administradorService = administradorService;
    }

    public PerfilResponse consultarPerfil(String emailAutenticado) {
        return construirRespuesta(buscarUsuarioAutenticado(emailAutenticado));
    }

    /**
     * HU-23: modifica únicamente el perfil asociado a la identidad autenticada.
     * Las reglas y la lista de campos permitidos se reutilizan de HU-07.
     */
    public PerfilResponse modificarPerfil(
            String emailAutenticado,
            ModificarUsuarioRequest request) {

        Usuario usuario = buscarUsuarioAutenticado(emailAutenticado);

        administradorService.modificarUsuario(usuario.getId(), request);

        return consultarPerfil(emailAutenticado);
    }

    private Usuario buscarUsuarioAutenticado(String emailAutenticado) {
        if (emailAutenticado == null || emailAutenticado.isBlank()) {
            throw new UsuarioNoEncontradoException();
        }

        return usuarioDAO.findByEmail(emailAutenticado)
                .orElseThrow(UsuarioNoEncontradoException::new);
    }

    private PerfilResponse construirRespuesta(Usuario usuario) {
        String tipoUsuario;
        String dniNif = null;
        java.time.LocalDate fechaNacimiento = null;
        Boolean esPremium = null;
        String nombreComercial = null;
        String categoria = null;
        String categoriaId = null;
        String sede = null;
        java.time.LocalDate fechaIncorporacion = null;
        String rol = null;

        if (usuario instanceof Cliente cliente) {
            tipoUsuario = "CLIENTE";
            dniNif = enmascararDniNif(cliente.getDniNif());
            fechaNacimiento = cliente.getFechaNacimiento();
            esPremium = cliente.isEsPremium();
        } else if (usuario instanceof Vendedor vendedor) {
            tipoUsuario = "VENDEDOR";
            dniNif = enmascararDniNif(vendedor.getDniNif());
            nombreComercial = vendedor.getNombreComercial();
            categoria = buscarNombreCategoria(vendedor.getCategoriaId());
            categoriaId = vendedor.getCategoriaId();
        } else if (usuario instanceof Administrador administrador) {
            tipoUsuario = "ADMIN";
            sede = administrador.getSede();
            fechaIncorporacion = administrador.getFechaIncorporacion();
            rol = administrador.getRol();
        } else {
            throw new IllegalStateException("Tipo de usuario no soportado.");
        }

        return new PerfilResponse(
                tipoUsuario,
                usuario.getNombre(),
                usuario.getApellidos(),
                usuario.getEmail(),
                usuario.getTelefono(),
                usuario.getImagenPerfil(),
                dniNif,
                fechaNacimiento,
                esPremium,
                nombreComercial,
                categoria,
                categoriaId,
                sede,
                fechaIncorporacion,
                rol);
    }

    private String enmascararDniNif(String dniNif) {
        if (dniNif == null || dniNif.isBlank()) {
            return dniNif;
        }

        int caracteresVisibles = Math.min(2, dniNif.length());
        return dniNif.substring(0, caracteresVisibles)
                + "*".repeat(Math.max(0, dniNif.length() - caracteresVisibles));
    }

    private String buscarNombreCategoria(String categoriaId) {
        if (categoriaId == null || categoriaId.isBlank()) {
            return null;
        }

        Criteria criterioId = ObjectId.isValid(categoriaId)
                ? new Criteria().orOperator(
                        Criteria.where("_id").is(new ObjectId(categoriaId)),
                        Criteria.where("_id").is(categoriaId))
                : Criteria.where("_id").is(categoriaId);

        return java.util.Optional.ofNullable(
                mongoTemplate.findOne(Query.query(criterioId), Categoria.class))
                .map(Categoria::getNombre)
                .orElse(null);
    }
}
