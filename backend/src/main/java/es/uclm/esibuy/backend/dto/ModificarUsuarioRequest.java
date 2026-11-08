package es.uclm.esibuy.backend.dto;

import java.time.LocalDate;

public record ModificarUsuarioRequest(
        String nombre,
        String apellidos,
        String telefono,
        String imagenPerfil,
        String dniNif,
        LocalDate fechaNacimiento,
        String sede,
        String categoriaId,
        Boolean esPremium) {

    public ModificarUsuarioRequest(
            String nombre,
            String apellidos,
            String telefono,
            String imagenPerfil,
            String dniNif,
            LocalDate fechaNacimiento,
            String sede,
            String categoriaId) {
        this(nombre, apellidos, telefono, imagenPerfil, dniNif,
                fechaNacimiento, sede, categoriaId, null);
    }
}
