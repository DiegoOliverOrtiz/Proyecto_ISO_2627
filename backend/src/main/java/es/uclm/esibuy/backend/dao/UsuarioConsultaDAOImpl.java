package es.uclm.esibuy.backend.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import es.uclm.esibuy.backend.model.Usuario;
import es.uclm.esibuy.backend.model.Administrador;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.Vendedor;
import es.uclm.esibuy.backend.dto.EstadisticasUsuariosResponse;

@Repository
public class UsuarioConsultaDAOImpl implements UsuarioConsultaDAO {

	private final MongoTemplate mongoTemplate;
	private final MongoConverter mongoConverter;

	public UsuarioConsultaDAOImpl(MongoTemplate mongoTemplate, MongoConverter mongoConverter) {
		this.mongoTemplate = mongoTemplate;
		this.mongoConverter = mongoConverter;
	}

	@Override
	public Page<Usuario> buscar(String texto, String estado, String rol, Pageable pageable) {
		Query query = new Query();
		List<Criteria> criterios = new ArrayList<>();

		if (texto != null && !texto.isBlank()) {
			String textoSeguro = Pattern.quote(texto.trim());
			criterios.add(new Criteria().orOperator(
					Criteria.where("nombre").regex(textoSeguro, "i"),
					Criteria.where("apellidos").regex(textoSeguro, "i"),
					Criteria.where("email").regex(textoSeguro, "i")));
		}

		if (estado != null && !estado.isBlank()) {
			criterios.add(criterioEstado(estado));
		}

		if (rol != null && !rol.isBlank()) {
			criterios.add(criterioRol(rol));
		}

		if (!criterios.isEmpty()) {
			query.addCriteria(new Criteria().andOperator(criterios));
		}
		query.with(pageable);

		List<Usuario> usuarios = mongoTemplate.find(query, Document.class, "usuarios").stream()
				.map(this::convertirUsuario)
				.toList();
		long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Usuario.class);
		return new PageImpl<>(usuarios, pageable, total);
	}

	@Override
	public Optional<Usuario> buscarPorId(String id) {
		Criteria criterioId = ObjectId.isValid(id)
				? new Criteria().orOperator(
						Criteria.where("_id").is(new ObjectId(id)),
						Criteria.where("_id").is(id))
				: Criteria.where("_id").is(id);
		Document documento = mongoTemplate.findOne(Query.query(criterioId), Document.class, "usuarios");
		return Optional.ofNullable(documento).map(this::convertirUsuario);
	}

	@Override
	public EstadisticasUsuariosResponse obtenerEstadisticas() {
		long total = mongoTemplate.count(new Query(), Usuario.class);
		long activos = contarEstado(Criteria.where("activada").is(true).and("bloqueada").is(false));
		long pendientes = contarEstado(Criteria.where("activada").is(false).and("bloqueada").is(false));
		long desactivados = contarEstado(Criteria.where("bloqueada").is(true));
		return new EstadisticasUsuariosResponse(total, activos, pendientes, desactivados);
	}

	private long contarEstado(Criteria criterio) {
		return mongoTemplate.count(Query.query(criterio), Usuario.class);
	}

	private Usuario convertirUsuario(Document documento) {
		return mongoConverter.read(resolverTipo(documento), documento);
	}

	private Class<? extends Usuario> resolverTipo(Document documento) {
		String tipo = documento.getString("_class");
		if (tipo != null) {
			String tipoNormalizado = tipo.toLowerCase(Locale.ROOT);
			if (tipoNormalizado.endsWith("administrador")) return Administrador.class;
			if (tipoNormalizado.endsWith("cliente")) return Cliente.class;
			if (tipoNormalizado.endsWith("vendedor")) return Vendedor.class;
		}
		if (documento.containsKey("nombreComercial")) return Vendedor.class;
		if (documento.containsKey("fechaNacimiento") || documento.containsKey("esPremium")) return Cliente.class;
		if (documento.containsKey("sede") || "ADMIN".equals(documento.getString("rol"))) return Administrador.class;
		throw new IllegalStateException("No se puede determinar el tipo del usuario almacenado.");
	}

	private Criteria criterioEstado(String estado) {
		return switch (estado.trim().toUpperCase(Locale.ROOT)) {
		case "ACTIVO" -> new Criteria().andOperator(
				Criteria.where("activada").is(true),
				Criteria.where("bloqueada").is(false));
		case "BLOQUEADO" -> Criteria.where("bloqueada").is(true);
		case "PENDIENTE" -> Criteria.where("activada").is(false);
		default -> throw new IllegalArgumentException("Estado de usuario no válido: " + estado);
		};
	}

	private Criteria criterioRol(String rol) {
		String alias = switch (rol.trim().toUpperCase(Locale.ROOT)) {
		case "ADMIN" -> "administrador";
		case "CLIENTE" -> "cliente";
		case "VENDEDOR" -> "vendedor";
		default -> throw new IllegalArgumentException("Rol de usuario no válido: " + rol);
		};
		return Criteria.where("_class").is(alias);
	}
}
