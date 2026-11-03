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
        String categoriaId,
        String sede,
        LocalDate fechaIncorporacion,
        String rol,
        boolean totpActivado) {

    public PerfilResponse(
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
        this(tipoUsuario, nombre, apellidos, email, telefono, imagenPerfil,
                dniNif, fechaNacimiento, esPremium, nombreComercial, categoria,
                null, sede, fechaIncorporacion, rol, false);
    }

    public PerfilResponse(
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
            String categoriaId,
            String sede,
            LocalDate fechaIncorporacion,
            String rol) {
        this(tipoUsuario, nombre, apellidos, email, telefono, imagenPerfil,
                dniNif, fechaNacimiento, esPremium, nombreComercial, categoria,
                categoriaId, sede, fechaIncorporacion, rol, false);
    }
}
