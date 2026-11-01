package es.uclm.esibuy.backend.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
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
    private Direccion direccionEnvio;
    private String metodoPagoId;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal importe;

    private Instant fechaCreacion;
    private Instant fechaPago;

    public Pedido(String clienteId, List<LineaPedido> lineas, EstadoPedido estado,
            BigDecimal importe, Instant fechaCreacion, Instant fechaPago,
            Direccion direccionEnvio, String metodoPagoId) {
        this(null, clienteId, lineas, estado, importe, fechaCreacion, fechaPago, direccionEnvio, metodoPagoId);
    }

    @PersistenceCreator
    public Pedido(String id, String clienteId, List<LineaPedido> lineas, EstadoPedido estado,
            BigDecimal importe, Instant fechaCreacion, Instant fechaPago,
            Direccion direccionEnvio, String metodoPagoId) {
        this.id = id;
        this.clienteId = clienteId;
        this.lineas = List.copyOf(lineas);
        this.estado = estado;
        this.importe = importe;
        this.fechaCreacion = fechaCreacion;
        this.fechaPago = fechaPago;
        this.direccionEnvio = copiarDireccion(direccionEnvio);
        this.metodoPagoId = metodoPagoId;
    }

    public String getId() {
        return id;
    }

    public Pedido pagado(Instant fechaPago) {
        if (estado != EstadoPedido.EN_PROCESO) {
            throw new IllegalStateException("Solo se puede pagar un pedido en proceso.");
        }
        return new Pedido(id, clienteId, lineas, EstadoPedido.PAGADO_COMPLETADO,
                importe, fechaCreacion, fechaPago, direccionEnvio, metodoPagoId);
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

    public Direccion getDireccionEnvio() {
        return copiarDireccion(direccionEnvio);
    }

    public String getMetodoPagoId() {
        return metodoPagoId;
    }

    private static Direccion copiarDireccion(Direccion origen) {
        if (origen == null) {
            return null;
        }
        Direccion copia = new Direccion();
        copia.setId(origen.getId());
        copia.setNombre(origen.getNombre());
        copia.setCalle(origen.getCalle());
        copia.setNumero(origen.getNumero());
        copia.setPortal(origen.getPortal());
        copia.setPiso(origen.getPiso());
        copia.setCodigoPostal(origen.getCodigoPostal());
        copia.setCiudad(origen.getCiudad());
        copia.setProvincia(origen.getProvincia());
        return copia;
    }
}
