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

	private static final String CAMPO_ACTIVADA = "activada";
	private static final String CAMPO_BLOQUEADA = "bloqueada";
	private static final String CAMPO_TIPO = "_class";
	private static final String TIPO_ADMINISTRADOR = "administrador";
	private static final String TIPO_CLIENTE = "cliente";
	private static final String TIPO_VENDEDOR = "vendedor";

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
		long activos = contarEstado(Criteria.where(CAMPO_ACTIVADA).is(true).and(CAMPO_BLOQUEADA).is(false));
		long pendientes = contarEstado(Criteria.where(CAMPO_ACTIVADA).is(false).and(CAMPO_BLOQUEADA).is(false));
		long desactivados = contarEstado(Criteria.where(CAMPO_BLOQUEADA).is(true));
		return new EstadisticasUsuariosResponse(total, activos, pendientes, desactivados);
	}

	private long contarEstado(Criteria criterio) {
		return mongoTemplate.count(Query.query(criterio), Usuario.class);
	}

	private Usuario convertirUsuario(Document documento) {
		return mongoConverter.read(resolverTipo(documento), documento);
	}

	private Class<? extends Usuario> resolverTipo(Document documento) {
		String tipo = documento.getString(CAMPO_TIPO);
		if (tipo != null) {
			String tipoNormalizado = tipo.toLowerCase(Locale.ROOT);
			if (tipoNormalizado.endsWith(TIPO_ADMINISTRADOR)) return Administrador.class;
			if (tipoNormalizado.endsWith(TIPO_CLIENTE)) return Cliente.class;
			if (tipoNormalizado.endsWith(TIPO_VENDEDOR)) return Vendedor.class;
		}
		if (documento.containsKey("nombreComercial")) return Vendedor.class;
		if (documento.containsKey("fechaNacimiento") || documento.containsKey("esPremium")) return Cliente.class;
		if (documento.containsKey("sede") || "ADMIN".equals(documento.getString("rol"))) return Administrador.class;
		throw new IllegalStateException("No se puede determinar el tipo del usuario almacenado.");
	}

	private Criteria criterioEstado(String estado) {
		return switch (estado.trim().toUpperCase(Locale.ROOT)) {
		case "ACTIVO" -> new Criteria().andOperator(
				Criteria.where(CAMPO_ACTIVADA).is(true),
				Criteria.where(CAMPO_BLOQUEADA).is(false));
		case "BLOQUEADO" -> Criteria.where(CAMPO_BLOQUEADA).is(true);
		case "PENDIENTE" -> Criteria.where(CAMPO_ACTIVADA).is(false);
		default -> throw new IllegalArgumentException("Estado de usuario no válido: " + estado);
		};
	}

	private Criteria criterioRol(String rol) {
		String rolUpperCase = rol.trim().toUpperCase(Locale.ROOT);
		
		if (rolUpperCase.equals("NO_ADMIN")) {
			// Excluye a los administradores buscando solo clientes y vendedores
			return new Criteria().orOperator(
				Criteria.where(CAMPO_TIPO).is(TIPO_CLIENTE),
				Criteria.where(CAMPO_TIPO).is(TIPO_VENDEDOR)
			);
		}

		String alias = switch (rolUpperCase) {
		case "ADMIN" -> TIPO_ADMINISTRADOR;
		case "CLIENTE" -> TIPO_CLIENTE;
		case "VENDEDOR" -> TIPO_VENDEDOR;
		default -> throw new IllegalArgumentException("Rol de usuario no válido: " + rol);
		};
		return Criteria.where(CAMPO_TIPO).is(alias);
	}
}
