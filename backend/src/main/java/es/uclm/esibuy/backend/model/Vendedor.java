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
	private String categoriaId;

	@NotBlank
	@Size(max = 9)
	private String dniNif;

	@NotBlank
	@Indexed(name = "uq_vendedor_nombre_comercial", unique = true, sparse = true)
	private String nombreComercial;

	public String getCategoriaId() {
		return categoriaId;
	}

	public void setCategoriaId(String categoriaId) {
		this.categoriaId = categoriaId;
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
