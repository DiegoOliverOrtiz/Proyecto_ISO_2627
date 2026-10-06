package es.uclm.esibuy.backend.dto;

public record ConsultarProductosResponse(String nombre,String categoria, int stock, String descripcion, Double precio, int descuento, int descuentoPremium, boolean esVisible) {
}
