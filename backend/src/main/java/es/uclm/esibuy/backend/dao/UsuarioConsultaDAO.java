package es.uclm.esibuy.backend.dao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

import es.uclm.esibuy.backend.model.Usuario;
import es.uclm.esibuy.backend.dto.EstadisticasUsuariosResponse;

public interface UsuarioConsultaDAO {
	Page<Usuario> buscar(String texto, String estado, String rol, Pageable pageable);
	Optional<Usuario> buscarPorId(String id);
	EstadisticasUsuariosResponse obtenerEstadisticas();
}
