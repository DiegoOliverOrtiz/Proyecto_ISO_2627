package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LimiteSolicitudesRecuperacionTests {

    private static final String CORREO = "cliente@example.com";
    private static final String IP = "203.0.113.5";

    private RelojAjustable reloj;
    private LimiteSolicitudesRecuperacion limite;

    @BeforeEach
    void setUp() {
        reloj = new RelojAjustable(Instant.parse("2026-11-06T10:00:00Z"));
        limite = new LimiteSolicitudesRecuperacion(reloj);
    }

    @Test
    void permiteTresSolicitudesPorCorreoYRechazaLaCuarta() {
        assertTrue(limite.permitir(CORREO, "198.51.100.1"));
        assertTrue(limite.permitir(CORREO, "198.51.100.2"));
        assertTrue(limite.permitir(CORREO, "198.51.100.3"));

        assertFalse(limite.permitir(CORREO, "198.51.100.4"));
    }

    @Test
    void permiteDiezSolicitudesPorIpYRechazaLaUndecima() {
        for (int i = 0; i < 10; i++) {
            assertTrue(limite.permitir("usuario" + i + "@example.com", IP));
        }

        assertFalse(limite.permitir("otro@example.com", IP));
    }

    @Test
    void vuelveAPermitirCuandoPasaLaVentanaDeQuinceMinutos() {
        for (int i = 0; i < 3; i++) {
            assertTrue(limite.permitir(CORREO, IP));
        }
        assertFalse(limite.permitir(CORREO, IP));

        reloj.avanzar(Duration.ofMinutes(15).plusSeconds(1));

        assertTrue(limite.permitir(CORREO, IP));
    }

    @Test
    void lasSolicitudesRechazadasNoAlarganElBloqueo() {
        for (int i = 0; i < 3; i++) {
            assertTrue(limite.permitir(CORREO, IP));
        }
        reloj.avanzar(Duration.ofMinutes(10));
        assertFalse(limite.permitir(CORREO, IP));

        reloj.avanzar(Duration.ofMinutes(5).plusSeconds(1));

        assertTrue(limite.permitir(CORREO, IP));
    }

    @Test
    void cuentaPorSeparadoCadaCorreoYCadaIp() {
        for (int i = 0; i < 3; i++) {
            assertTrue(limite.permitir(CORREO, IP));
        }

        assertTrue(limite.permitir("otro@example.com", "198.51.100.9"));
    }

    @Test
    void trataLaIpVaciaComoUnaMismaIpDesconocida() {
        for (int i = 0; i < 10; i++) {
            assertTrue(limite.permitir("usuario" + i + "@example.com", null));
        }

        assertFalse(limite.permitir("otro@example.com", ""));
    }

    private static final class RelojAjustable extends Clock {
        private Instant ahora;

        RelojAjustable(Instant inicio) {
            this.ahora = inicio;
        }

        void avanzar(Duration duracion) {
            ahora = ahora.plus(duracion);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }
}
