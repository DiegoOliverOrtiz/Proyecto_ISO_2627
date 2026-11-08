package es.uclm.esibuy.backend.model;

import java.math.BigDecimal;

import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

public record LineaPedido(String productoId, String nombreProducto,
        @Field(targetType = FieldType.DECIMAL128) BigDecimal precioCompra,
        int cantidad, String vendedorId, String nombreVendedor) {
}
