package es.uclm.esibuy.backend.model;

import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Document(collection = "usuarios")
@TypeAlias("vendedor")
public class Vendedor extends Usuario {

	@NotBlank
	private String categoria;

	@NotBlank
	@Size(max = 9)
	private String dniNif;

	@NotBlank
	@Indexed(name = "uq_vendedor_nombre_comercial", unique = true, sparse = true)
	private String nombreComercial;

	public String getCategoria() {
		return categoria;
	}

	public void setCategoria(String categoria) {
		this.categoria = categoria;
	}

	public String getDniNif() {
		return dniNif;
	}

	public void setDniNif(String dniNif) {
		this.dniNif = dniNif;
	}

	public String getNombreComercial() {
		return nombreComercial;
	}

	public void setNombreComercial(String nombreComercial) {
		this.nombreComercial = nombreComercial;
	}
}
