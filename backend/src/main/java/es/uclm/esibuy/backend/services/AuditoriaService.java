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
}
