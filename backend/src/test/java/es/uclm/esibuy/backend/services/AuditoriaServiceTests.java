package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class AuditoriaServiceTests {

    private final AuditoriaService auditoria = new AuditoriaService();

    @Test
    void registraLaSolicitudDeRecuperacionSinDatosPersonales(CapturedOutput salida) {
        auditoria.registrarSolicitudRecuperacion(false);
        auditoria.registrarSolicitudRecuperacion(true);

        assertTrue(salida.getOut().contains(
                "evento_seguridad=recuperacion_solicitada limitada=false"));
        assertTrue(salida.getOut().contains(
                "evento_seguridad=recuperacion_solicitada limitada=true"));
        assertFalse(salida.getOut().contains("@"));
    }

    @Test
    void registraElRestablecimientoConElIdDelUsuario(CapturedOutput salida) {
        auditoria.registrarRestablecimientoContrasena("usuario-1");

        assertTrue(salida.getOut().contains(
                "evento_seguridad=contrasena_restablecida usuario_id=usuario-1 resultado=exito"));
    }

    @Test
    void registraFallosYBloqueosDeLoginSinDatosPersonales(CapturedOutput salida) {
        auditoria.registrarIntentoLoginFallido();
        auditoria.registrarBloqueoLogin(30);

        assertTrue(salida.getOut().contains(
                "evento_seguridad=login_fallido resultado=credenciales_invalidas"));
        assertTrue(salida.getOut().contains(
                "evento_seguridad=login_bloqueado retry_after_segundos=30"));
        assertFalse(salida.getOut().contains("@"));
        assertFalse(salida.getOut().contains("192.0.2."));
    }

    @Test
    void registraAltasYEliminacionesAdministrativasSinIdentificadoresPersonales(CapturedOutput salida) {
        auditoria.registrarAltaAdministrador();
        auditoria.registrarEliminacionUsuarioPorAdministrador("ADMINISTRADOR");

        assertTrue(salida.getOut().contains(
                "evento_seguridad=alta_administrador resultado=exito"));
        assertTrue(salida.getOut().contains(
                "evento_seguridad=usuario_eliminado_por_administrador tipo_usuario=ADMINISTRADOR resultado=exito"));
        assertFalse(salida.getOut().contains("@"));
    }
}
