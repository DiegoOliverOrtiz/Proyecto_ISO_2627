package es.uclm.esibuy.backend.controllers;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.uclm.esibuy.backend.dto.CompletarRecuperacionRequest;
import es.uclm.esibuy.backend.dto.SolicitarRecuperacionRequest;
import es.uclm.esibuy.backend.services.RecuperacionContrasenaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/usuarios")
public class RecuperacionContrasenaController {

    private final RecuperacionContrasenaService servicio;

    public RecuperacionContrasenaController(
            RecuperacionContrasenaService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/recuperar-contrasena")
    public ResponseEntity<Map<String, String>> solicitar(
            @Valid @RequestBody SolicitarRecuperacionRequest request,
            HttpServletRequest peticion) {
        servicio.solicitar(request.email(), peticion.getRemoteAddr());
        return ResponseEntity.accepted().body(Map.of(
                "message",
                "Si el correo está asociado a una cuenta, recibirás instrucciones para recuperar tu contraseña."));
    }

    @PostMapping("/recuperar-contrasena/confirmar")
    public ResponseEntity<Void> completar(
            @Valid @RequestBody CompletarRecuperacionRequest request) {
        servicio.completar(request.token(), request.nuevaContrasena());
        return ResponseEntity.noContent().build();
    }
}