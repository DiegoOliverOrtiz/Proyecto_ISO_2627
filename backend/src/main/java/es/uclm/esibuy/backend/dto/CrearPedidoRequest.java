package es.uclm.esibuy.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CrearPedidoRequest(
        @NotBlank @Size(max = 128) String direccionId,
        @NotBlank @Size(max = 128) String metodoPagoId,
        @NotBlank @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
                message = "La clave de operación debe ser un UUID.") String operacionId) {
}
