package es.uclm.esibuy.backend.services;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * Límite de solicitudes de recuperación de contraseña, en memoria y con
 * ventana deslizante: evita inundar un buzón y agotar el cupo de Brevo.
 * Se reinicia al reiniciar el servidor.
 */
@Service
public class LimiteSolicitudesRecuperacion {

    static final int MAXIMO_POR_CORREO = 3;
    static final int MAXIMO_POR_IP = 10;
    static final Duration VENTANA = Duration.ofMinutes(15);
    private static final int ENTRADAS_ANTES_DE_LIMPIAR = 10_000;

    private final Clock reloj;
    private final Map<String, Deque<Instant>> solicitudesPorCorreo = new HashMap<>();
    private final Map<String, Deque<Instant>> solicitudesPorIp = new HashMap<>();

    public LimiteSolicitudesRecuperacion() {
        this(Clock.systemUTC());
    }

    LimiteSolicitudesRecuperacion(Clock reloj) {
        this.reloj = reloj;
    }

    /**
     * Devuelve {@code true} y anota la solicitud si no supera ningún límite.
     * Las solicitudes rechazadas no cuentan, para no alargar el bloqueo.
     */
    public synchronized boolean permitir(String correoNormalizado, String ip) {
        Instant ahora = reloj.instant();
        Instant limite = ahora.minus(VENTANA);
        String claveIp = ip == null || ip.isBlank() ? "desconocida" : ip;

        limpiarSiCrece(solicitudesPorCorreo, limite);
        limpiarSiCrece(solicitudesPorIp, limite);

        Deque<Instant> delCorreo = recientes(solicitudesPorCorreo, correoNormalizado, limite);
        Deque<Instant> deLaIp = recientes(solicitudesPorIp, claveIp, limite);

        if (delCorreo.size() >= MAXIMO_POR_CORREO || deLaIp.size() >= MAXIMO_POR_IP) {
            return false;
        }

        delCorreo.addLast(ahora);
        deLaIp.addLast(ahora);
        return true;
    }

    private static Deque<Instant> recientes(
            Map<String, Deque<Instant>> solicitudes, String clave, Instant limite) {
        Deque<Instant> cola = solicitudes.computeIfAbsent(clave, c -> new ArrayDeque<>());
        while (!cola.isEmpty() && !cola.peekFirst().isAfter(limite)) {
            cola.pollFirst();
        }
        return cola;
    }

    private static void limpiarSiCrece(Map<String, Deque<Instant>> solicitudes, Instant limite) {
        if (solicitudes.size() < ENTRADAS_ANTES_DE_LIMPIAR) {
            return;
        }
        solicitudes.values().removeIf(cola -> cola.isEmpty() || !cola.peekLast().isAfter(limite));
    }
}
