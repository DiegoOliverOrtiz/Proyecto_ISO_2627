package es.uclm.esibuy.backend.model;

public class Direccion {

	private String id;
	private String nombre;
	private String calle;
	private String numero;
	private String portal;
	private String piso;
	private String codigoPostal;
	private String ciudad;
	private String provincia;

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }

	public String getNombre() { return nombre; }
	public void setNombre(String nombre) { this.nombre = nombre; }

	public String getCalle() { return calle; }
	public void setCalle(String calle) { this.calle = calle; }

	public String getNumero() { return numero; }
	public void setNumero(String numero) { this.numero = numero; }

	public String getPortal() { return portal; }
	public void setPortal(String portal) { this.portal = portal; }

	public String getPiso() { return piso; }
	public void setPiso(String piso) { this.piso = piso; }

	public String getCodigoPostal() { return codigoPostal; }
	public void setCodigoPostal(String codigoPostal) { this.codigoPostal = codigoPostal; }

	public String getCiudad() { return ciudad; }
	public void setCiudad(String ciudad) { this.ciudad = ciudad; }

	public String getProvincia() { return provincia; }
	public void setProvincia(String provincia) { this.provincia = provincia; }
}