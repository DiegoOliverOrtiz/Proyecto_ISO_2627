package es.uclm.esibuy.backend.dao;

import java.util.List;

import org.springframework.data.repository.Repository;

import es.uclm.esibuy.backend.model.MetodoPago;

public interface MetodoPagoDAO extends Repository<MetodoPago, String> {

	List<MetodoPago> findByClienteId(String clienteId);

	void deleteByClienteId(String clienteId);

	<S extends MetodoPago> S save(S metodoPago);
}
