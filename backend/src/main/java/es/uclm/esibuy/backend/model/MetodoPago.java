package es.uclm.esibuy.backend.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "metodos_pago")
public class MetodoPago {

	@Id
	private String id;

	@Indexed
	private String clienteId;

	private String tipo;

	private String nombre;

	private String token;

	private String referenciaEnmascarada;

	private String titular;

	private String caducidad;

	private Instant fechaAlta;

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }

	public String getClienteId() { return clienteId; }
	public void setClienteId(String clienteId) { this.clienteId = clienteId; }

	public String getTipo() { return tipo; }
	public void setTipo(String tipo) { this.tipo = tipo; }

	public String getNombre() { return nombre; }
	public void setNombre(String nombre) { this.nombre = nombre; }

	public String getToken() { return token; }
	public void setToken(String token) { this.token = token; }

	public String getReferenciaEnmascarada() { return referenciaEnmascarada; }
	public void setReferenciaEnmascarada(String referenciaEnmascarada) { this.referenciaEnmascarada = referenciaEnmascarada; }

	public String getTitular() { return titular; }
	public void setTitular(String titular) { this.titular = titular; }

	public String getCaducidad() { return caducidad; }
	public void setCaducidad(String caducidad) { this.caducidad = caducidad; }

	public Instant getFechaAlta() { return fechaAlta; }
	public void setFechaAlta(Instant fechaAlta) { this.fechaAlta = fechaAlta; }
}