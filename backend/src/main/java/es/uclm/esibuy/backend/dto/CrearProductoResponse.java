package es.uclm.esibuy.backend.dto;

public record CrearProductoResponse(String nombre, String categoria, double precio, int stock, int descuento, int descuentoPremium ) {

}
