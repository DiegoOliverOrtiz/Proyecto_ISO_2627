package es.uclm.esibuy.backend.dto;

import java.time.LocalDate;

public record ModificarUsuarioRequest(
        String nombre,
        String apellidos,
        String telefono,
        String imagenPerfil,
        String dniNif,
        LocalDate fechaNacimiento,
        String sede) {
}