package es.uclm.esibuy.backend.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditoriaService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuditoriaService.class);

    public void registrarBajaCuenta(String tipoUsuario) {
        LOGGER.info("evento_seguridad=cuenta_eliminada tipo_usuario={} resultado=exito", tipoUsuario);
    }

    public void registrarIntentoLoginFallido() {
        LOGGER.warn("evento_seguridad=login_fallido resultado=credenciales_invalidas");
    }

    public void registrarBloqueoLogin(int retryAfterSeconds) {
        LOGGER.warn("evento_seguridad=login_bloqueado retry_after_segundos={}", retryAfterSeconds);
    }

    public void registrarAltaAdministrador() {
        LOGGER.info("evento_seguridad=alta_administrador resultado=exito");
    }

    public void registrarEliminacionUsuarioPorAdministrador(String tipoUsuario) {
        LOGGER.info("evento_seguridad=usuario_eliminado_por_administrador tipo_usuario={} resultado=exito",
                tipoUsuario);
    }

    // Sin el correo: el evento no debe revelar a quién se refiere ni si la cuenta existe.
    public void registrarSolicitudRecuperacion(boolean limitada) {
        LOGGER.info("evento_seguridad=recuperacion_solicitada limitada={}", limitada);
    }

    public void registrarRestablecimientoContrasena(String usuarioId) {
        LOGGER.info("evento_seguridad=contrasena_restablecida usuario_id={} resultado=exito", usuarioId);
    }
}
