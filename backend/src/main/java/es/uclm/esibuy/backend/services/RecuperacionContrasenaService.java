package es.uclm.esibuy.backend.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.HexFormat;

import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import es.uclm.esibuy.backend.dao.TokenRecuperacionDAO;
import es.uclm.esibuy.backend.dao.UsuarioDAO;
import es.uclm.esibuy.backend.exceptions.InvalidRegistrationException;
import es.uclm.esibuy.backend.exceptions.TokenRecuperacionInvalidoException;
import es.uclm.esibuy.backend.model.TokenRecuperacion;
import es.uclm.esibuy.backend.model.Usuario;

@Service
public class RecuperacionContrasenaService {

    private static final long MINUTOS_VALIDEZ_TOKEN = 30;

    private final UsuarioDAO usuarioDAO;
    private final TokenRecuperacionDAO tokenDAO;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final SesionService sesionService;
    private final BrevoEmailService brevoEmailService;
    private final LimiteSolicitudesRecuperacion limiteSolicitudes;
    private final AuditoriaService auditoriaService;
    private final TransactionTemplate transaccion;
    private final String urlFrontend;
    private final SecureRandom secureRandom = new SecureRandom();

    public RecuperacionContrasenaService(
            UsuarioDAO usuarioDAO,
            TokenRecuperacionDAO tokenDAO,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            SesionService sesionService,
            BrevoEmailService brevoEmailService,
            LimiteSolicitudesRecuperacion limiteSolicitudes,
            AuditoriaService auditoriaService,
            MongoTransactionManager transactionManager,
            @org.springframework.beans.factory.annotation.Value(
                    "${app.frontend-url:https://localhost:4200}") String urlFrontend) {
        this.usuarioDAO = usuarioDAO;
        this.tokenDAO = tokenDAO;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.sesionService = sesionService;
        this.brevoEmailService = brevoEmailService;
        this.limiteSolicitudes = limiteSolicitudes;
        this.auditoriaService = auditoriaService;
        this.transaccion = new TransactionTemplate(transactionManager);
        this.urlFrontend = urlFrontend.replaceAll("/+$", "");
    }

    public void solicitar(String email, String ip) {
        String emailNormalizado = email.trim().toLowerCase(Locale.ROOT);

        // Por encima del límite no se crea token ni se envía correo, pero la
        // respuesta al cliente es la misma para no revelar nada.
        if (!limiteSolicitudes.permitir(emailNormalizado, ip)) {
            auditoriaService.registrarSolicitudRecuperacion(true);
            return;
        }
        // Se registra antes de buscar el usuario, exista o no.
        auditoriaService.registrarSolicitudRecuperacion(false);

        Optional<Usuario> usuarioEncontrado = usuarioDAO.findByEmail(emailNormalizado);

        if (usuarioEncontrado.isEmpty()) {
            return;
        }

        Usuario usuario = usuarioEncontrado.get();
        byte[] bytesToken = new byte[32];
        secureRandom.nextBytes(bytesToken);

        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(bytesToken);
        Instant caducidad = Instant.now().plusSeconds(MINUTOS_VALIDEZ_TOKEN * 60);

        tokenDAO.deleteByUsuarioId(usuario.getId());
        tokenDAO.save(new TokenRecuperacion(
                calcularHash(token), usuario.getId(), caducidad));

        String enlace = urlFrontend + "/recuperar-contrasena?token=" + token;
        // Sin esperar a Brevo: el tiempo de respuesta no revela si el correo existe.
        brevoEmailService.enviarRecuperacionEnSegundoPlano(usuario.getEmail(), enlace);
    }

    public void completar(String token, String nuevaContrasena) {
        String hashToken = calcularHash(token);
        TokenRecuperacion tokenGuardado = tokenDAO.findByHashToken(hashToken)
                .orElseThrow(TokenRecuperacionInvalidoException::new);
        Instant ahora = Instant.now();

        if (tokenGuardado.isConsumido()
                || !tokenGuardado.getCaducidad().isAfter(ahora)) {
            throw new TokenRecuperacionInvalidoException();
        }

        Usuario usuario = usuarioDAO.findById(tokenGuardado.getUsuarioId())
                .orElseThrow(TokenRecuperacionInvalidoException::new);

        passwordPolicy.validate(nuevaContrasena);

        List<String> hashesRecientes =
                new ArrayList<>(usuario.getHistorialContrasenasHash());
        String hashActual = usuario.getContrasenaHash();
        if (hashActual != null && !hashesRecientes.contains(hashActual)) {
            hashesRecientes.add(hashActual);
        }

        if (passwordPolicy.matchesRecentPassword(
                nuevaContrasena, hashesRecientes, passwordEncoder)) {
            throw new InvalidRegistrationException(
                    "nuevaContrasena",
                    "No puedes reutilizar una de tus contraseñas recientes.");
        }

        transaccion.executeWithoutResult(status -> {
            if (tokenDAO.consumirSiValido(tokenGuardado.getId(), ahora) != 1) {
                throw new TokenRecuperacionInvalidoException();
            }

            usuario.registrarContrasenaHash(passwordEncoder.encode(nuevaContrasena));
            usuarioDAO.save(usuario);
        });

        sesionService.cerrarSesiones(usuario.getEmail());
        auditoriaService.registrarRestablecimientoContrasena(usuario.getId());
    }

    private String calcularHash(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "No está disponible SHA-256 para validar el token.", exception);
        }
    }
}