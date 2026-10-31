package es.uclm.esibuy.backend.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Document(collection = "pedidos")
@TypeAlias("pedido")
public class Pedido {
    @Id
    private String id;

    private String clienteId;
    private List<LineaPedido> lineas;
    private EstadoPedido estado;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal importe;

    private Instant fechaCreacion;
    private Instant fechaPago;

    public Pedido(String clienteId, List<LineaPedido> lineas, EstadoPedido estado,
            BigDecimal importe, Instant fechaCreacion, Instant fechaPago) {
        this.clienteId = clienteId;
        this.lineas = List.copyOf(lineas);
        this.estado = estado;
        this.importe = importe;
        this.fechaCreacion = fechaCreacion;
        this.fechaPago = fechaPago;
    }

    public String getId() {
        return id;
    }

    public String getClienteId() {
        return clienteId;
    }

    public List<LineaPedido> getLineas() {
        return List.copyOf(lineas);
    }

    public EstadoPedido getEstado() {
        return estado;
    }

    public BigDecimal getImporte() {
        return importe;
    }

    public Instant getFechaCreacion() {
        return fechaCreacion;
    }

    public Instant getFechaPago() {
        return fechaPago;
    }
}
