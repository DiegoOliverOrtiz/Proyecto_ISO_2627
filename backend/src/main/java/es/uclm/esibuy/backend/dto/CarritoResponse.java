package es.uclm.esibuy.backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record CarritoResponse(List<LineaCarritoResponse> productos, BigDecimal importe) {
}
