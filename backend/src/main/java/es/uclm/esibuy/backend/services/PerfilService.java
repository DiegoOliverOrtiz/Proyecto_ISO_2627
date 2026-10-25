package es.uclm.esibuy.backend.services;

import org.springframework.stereotype.Service;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import es.uclm.esibuy.backend.dao.UsuarioDAO;
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

    public PerfilService(UsuarioDAO usuarioDAO, MongoTemplate mongoTemplate) {
        this.usuarioDAO = usuarioDAO;
        this.mongoTemplate = mongoTemplate;
    }

    public PerfilResponse consultarPerfil(String emailAutenticado) {
        if (emailAutenticado == null || emailAutenticado.isBlank()) {
            throw new UsuarioNoEncontradoException();
        }

        Usuario usuario = usuarioDAO.findByEmail(emailAutenticado)
                .orElseThrow(UsuarioNoEncontradoException::new);

        return construirRespuesta(usuario);
    }

    private PerfilResponse construirRespuesta(Usuario usuario) {
        String tipoUsuario;
        String dniNif = null;
        java.time.LocalDate fechaNacimiento = null;
        Boolean esPremium = null;
        String nombreComercial = null;
        String categoria = null;
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
