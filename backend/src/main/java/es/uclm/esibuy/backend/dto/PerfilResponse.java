package es.uclm.esibuy.backend.dto;

import java.time.LocalDate;

public record PerfilResponse(
        String tipoUsuario,
        String nombre,
        String apellidos,
        String email,
        String telefono,
        String imagenPerfil,
        String dniNif,
        LocalDate fechaNacimiento,
        Boolean esPremium,
        String nombreComercial,
        String categoria,
        String sede,
        LocalDate fechaIncorporacion,
        String rol) {
}
