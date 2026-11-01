package es.uclm.esibuy.backend.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Document(collection = "usuarios")
@TypeAlias("cliente")
public class Cliente extends Usuario {

	@NotNull
	private LocalDate fechaNacimiento;

	@NotBlank
	@Size(max = 9)
	private String dniNif;

	private boolean esPremium;

	private List<Direccion> direcciones = new ArrayList<>();

	public LocalDate getFechaNacimiento() {
		return fechaNacimiento;
	}

	public void setFechaNacimiento(LocalDate fechaNacimiento) {
		this.fechaNacimiento = fechaNacimiento;
	}

	public String getDniNif() {
		return dniNif;
	}

	public void setDniNif(String dniNif) {
		this.dniNif = dniNif;
	}

	public boolean isEsPremium() {
		return esPremium;
	}

	public void setEsPremium(boolean esPremium) {
		this.esPremium = esPremium;
	}

	@JsonIgnore
	public List<Direccion> getDirecciones() {
		return direcciones == null ? List.of() : List.copyOf(direcciones);
	}

	public Optional<Direccion> buscarDireccion(String idDireccion) {
		return getDirecciones().stream()
				.filter(direccion -> idDireccion.equals(direccion.getId()))
				.findFirst();
	}

	public void agregarDireccion(Direccion direccion) {
		if (direcciones == null) {
			direcciones = new ArrayList<>();
		}
		direcciones.add(direccion);
	}

	public boolean eliminarDireccion(String idDireccion) {
		return direcciones != null
				&& direcciones.removeIf(direccion -> idDireccion.equals(direccion.getId()));
	}
}