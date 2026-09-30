package es.uclm.esibuy.backend.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Document(collection = "usuarios")
public abstract class Usuario {

	@Id
	private String id;

	@NotBlank
	private String nombre;

	@NotBlank
	private String apellidos;

	@NotBlank
	@Email
	@Indexed(name = "uq_usuario_email", unique = true)
	private String email;

	@NotBlank
	private String contrasenaHash;

	private List<String> historialContrasenasHash = new ArrayList<>();

	private String telefono;

	private String imagenPerfil;

	private boolean bloqueada;

	private boolean activada;

	protected Usuario() {
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	public String getApellidos() {
		return apellidos;
	}

	public void setApellidos(String apellidos) {
		this.apellidos = apellidos;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
	}

	@JsonIgnore
	public String getContrasenaHash() {
		return contrasenaHash;
	}

	public void setContrasenaHash(String contrasenaHash) {
		this.contrasenaHash = contrasenaHash;
	}

	@JsonIgnore
	public List<String> getHistorialContrasenasHash() {
		return historialContrasenasHash == null ? List.of() : List.copyOf(historialContrasenasHash);
	}

	public void registrarContrasenaHash(String contrasenaHash) {
		this.contrasenaHash = contrasenaHash;
		if (historialContrasenasHash == null) {
			historialContrasenasHash = new ArrayList<>();
		}
		historialContrasenasHash.add(0, contrasenaHash);
		if (historialContrasenasHash.size() > 5) {
			historialContrasenasHash = new ArrayList<>(historialContrasenasHash.subList(0, 5));
		}
	}

	public String getTelefono() {
		return telefono;
	}

	public void setTelefono(String telefono) {
		this.telefono = telefono;
	}

	public String getImagenPerfil() {
		return imagenPerfil;
	}

	public void setImagenPerfil(String imagenPerfil) {
		this.imagenPerfil = imagenPerfil;
	}

	public boolean isBloqueada() {
		return bloqueada;
	}

	public void setBloqueada(boolean bloqueada) {
		this.bloqueada = bloqueada;
	}

	public boolean isActivada() {
		return activada;
	}

	public void setActivada(boolean activada) {
		this.activada = activada;
	}
}