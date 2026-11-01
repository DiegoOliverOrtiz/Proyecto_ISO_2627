package es.uclm.esibuy.backend.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

import es.uclm.esibuy.backend.model.TipoMetodoPago;

public interface TipoMetodoPagoDAO extends Repository<TipoMetodoPago, String> {

	List<TipoMetodoPago> findByActivoTrueOrderByNombreAsc();

	Optional<TipoMetodoPago> findByCodigoAndActivoTrue(String codigo);

	boolean existsByCodigo(String codigo);

	<S extends TipoMetodoPago> S save(S tipo);
}