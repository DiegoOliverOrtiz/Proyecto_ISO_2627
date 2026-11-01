package es.uclm.esibuy.backend.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import es.uclm.esibuy.backend.dao.TipoMetodoPagoDAO;
import es.uclm.esibuy.backend.model.TipoMetodoPago;

@ExtendWith(MockitoExtension.class)
class CatalogoMetodosPagoInicializadorTests {

	@Mock
	private TipoMetodoPagoDAO tipoMetodoPagoDAO;

	@Test
	void run_creaLosTresTiposSiNoExisten() {
		when(tipoMetodoPagoDAO.existsByCodigo(anyString())).thenReturn(false);

		new CatalogoMetodosPagoInicializador(tipoMetodoPagoDAO).run(null);

		verify(tipoMetodoPagoDAO, times(3)).save(any(TipoMetodoPago.class));
	}

	@Test
	void run_noDuplicaLosTiposQueYaExisten() {
		when(tipoMetodoPagoDAO.existsByCodigo(anyString())).thenReturn(true);

		new CatalogoMetodosPagoInicializador(tipoMetodoPagoDAO).run(null);

		verify(tipoMetodoPagoDAO, never()).save(any(TipoMetodoPago.class));
	}
}