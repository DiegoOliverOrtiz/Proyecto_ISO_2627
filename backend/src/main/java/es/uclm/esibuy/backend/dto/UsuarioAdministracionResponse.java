package es.uclm.esibuy.backend.dto;

import java.time.LocalDate;

import es.uclm.esibuy.backend.model.Administrador;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.Usuario;
import es.uclm.esibuy.backend.model.Vendedor;

public record UsuarioAdministracionResponse(
        String id,
        String nombre,
        String apellidos,
        String email,
        String telefono,
        String estado,
        String rol,
        String imagenPerfil,
        String sede,
        LocalDate fechaIncorporacion,
        Boolean esPremium,
        String nombreComercial,
        String dniNif,
        LocalDate fechaNacimiento,
        String categoriaId) {

    public static UsuarioAdministracionResponse desde(Usuario usuario) {
        DatosRol datos = switch (usuario) {
            case Administrador administrador -> datosAdministrador(administrador);
            case Cliente cliente -> datosCliente(cliente);
            case Vendedor vendedor -> datosVendedor(vendedor);
            default -> throw new IllegalStateException("Tipo de usuario no soportado.");
        };

        return new UsuarioAdministracionResponse(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getApellidos(),
                usuario.getEmail(),
                usuario.getTelefono(),
                calcularEstado(usuario),
                datos.rol(),
                usuario.getImagenPerfil(),
                datos.sede(),
                datos.fechaIncorporacion(),
                datos.esPremium(),
                datos.nombreComercial(),
                datos.dniNif(),
                datos.fechaNacimiento(),
                datos.categoriaId());
    }

    private static String calcularEstado(Usuario usuario) {
        if (usuario.isBloqueada()) {
            return "BLOQUEADO";
        }
        if (usuario.isActivada()) {
            return "ACTIVO";
        }
        return "PENDIENTE";
    }

    /*
     * Campos de la respuesta que dependen del tipo de usuario
     */
    private record DatosRol(
            String rol,
            String sede,
            LocalDate fechaIncorporacion,
            Boolean esPremium,
            String nombreComercial,
            String dniNif,
            LocalDate fechaNacimiento,
            String categoriaId) {
    }

    private static DatosRol datosAdministrador(Administrador administrador) {
        return new DatosRol("ADMIN", administrador.getSede(), administrador.getFechaIncorporacion(),
                null, null, null, null, null);
    }

    private static DatosRol datosCliente(Cliente cliente) {
        return new DatosRol("CLIENTE", null, null, cliente.isEsPremium(),
                null, cliente.getDniNif(), cliente.getFechaNacimiento(), null);
    }

    private static DatosRol datosVendedor(Vendedor vendedor) {
        return new DatosRol("VENDEDOR", null, null, null,
                vendedor.getNombreComercial(), vendedor.getDniNif(), null, vendedor.getCategoriaId());
    }
}
