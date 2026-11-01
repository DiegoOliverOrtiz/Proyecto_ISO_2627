package es.uclm.esibuy.backend.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import es.uclm.esibuy.backend.dao.TipoMetodoPagoDAO;
import es.uclm.esibuy.backend.model.TipoMetodoPago;

@Component
public class CatalogoMetodosPagoInicializador implements ApplicationRunner {

	private final TipoMetodoPagoDAO tipoMetodoPagoDAO;

	public CatalogoMetodosPagoInicializador(TipoMetodoPagoDAO tipoMetodoPagoDAO) {
		this.tipoMetodoPagoDAO = tipoMetodoPagoDAO;
	}

	@Override
	public void run(ApplicationArguments args) {
		cargarSiFalta(TipoMetodoPago.CODIGO_TARJETA, "Tarjeta");
		cargarSiFalta(TipoMetodoPago.CODIGO_PAYPAL, "PayPal");
		cargarSiFalta(TipoMetodoPago.CODIGO_BIZUM, "Bizum");
	}

	private void cargarSiFalta(String codigo, String nombre) {
		if (!tipoMetodoPagoDAO.existsByCodigo(codigo)) {
			TipoMetodoPago tipo = new TipoMetodoPago();
			tipo.setCodigo(codigo);
			tipo.setNombre(nombre);
			tipo.setActivo(true);
			tipoMetodoPagoDAO.save(tipo);
		}
	}
}