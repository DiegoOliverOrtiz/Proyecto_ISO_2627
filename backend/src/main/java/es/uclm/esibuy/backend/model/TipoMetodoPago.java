package es.uclm.esibuy.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "tipos_metodo_pago")
public class TipoMetodoPago {

	public static final String CODIGO_TARJETA = "TARJETA";
	public static final String CODIGO_PAYPAL = "PAYPAL";
	public static final String CODIGO_BIZUM = "BIZUM";

	@Id
	private String id;

	@Indexed(name = "uq_tipo_metodo_pago_codigo", unique = true)
	private String codigo;

	private String nombre;

	private boolean activo = true;

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }

	public String getCodigo() { return codigo; }
	public void setCodigo(String codigo) { this.codigo = codigo; }

	public String getNombre() { return nombre; }
	public void setNombre(String nombre) { this.nombre = nombre; }

	public boolean isActivo() { return activo; }
	public void setActivo(boolean activo) { this.activo = activo; }
}