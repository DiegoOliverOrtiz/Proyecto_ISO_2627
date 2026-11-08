package es.uclm.esibuy.backend.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
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
    private String clienteNombre;
    private List<LineaPedido> lineas;
    private EstadoPedido estado;
    private Direccion direccionEnvio;
    private String metodoPagoId;
    private List<CambioEstadoPedido> historialEstados;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal importe;

    private Instant fechaCreacion;
    private Instant fechaPago;

    /*
     * Crea un pedido nuevo. Si no se indica el historial, empieza con la entrada «En proceso»
     * cuando el pedido se crea en ese estado.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String clienteId;
        private List<LineaPedido> lineas;
        private EstadoPedido estado;
        private BigDecimal importe;
        private Instant fechaCreacion;
        private Instant fechaPago;
        private Direccion direccionEnvio;
        private String metodoPagoId;

        private Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder clienteId(String clienteId) {
            this.clienteId = clienteId;
            return this;
        }

        public Builder lineas(List<LineaPedido> lineas) {
            this.lineas = lineas;
            return this;
        }

        public Builder estado(EstadoPedido estado) {
            this.estado = estado;
            return this;
        }

        public Builder importe(BigDecimal importe) {
            this.importe = importe;
            return this;
        }

        public Builder fechaCreacion(Instant fechaCreacion) {
            this.fechaCreacion = fechaCreacion;
            return this;
        }

        public Builder fechaPago(Instant fechaPago) {
            this.fechaPago = fechaPago;
            return this;
        }

        public Builder direccionEnvio(Direccion direccionEnvio) {
            this.direccionEnvio = direccionEnvio;
            return this;
        }

        public Builder metodoPagoId(String metodoPagoId) {
            this.metodoPagoId = metodoPagoId;
            return this;
        }

        public Pedido build() {
            return new Pedido(id, clienteId, lineas, estado, importe, fechaCreacion, fechaPago,
                    direccionEnvio, metodoPagoId, historialInicial());
        }

        private List<CambioEstadoPedido> historialInicial() {
            if (estado == EstadoPedido.EN_PROCESO && fechaCreacion != null) {
                return List.of(new CambioEstadoPedido(estado, fechaCreacion));
            }
            return List.of();
        }
    }

    @PersistenceCreator
    public Pedido(String id, String clienteId, List<LineaPedido> lineas, EstadoPedido estado,
            BigDecimal importe, Instant fechaCreacion, Instant fechaPago,
            Direccion direccionEnvio, String metodoPagoId,
            List<CambioEstadoPedido> historialEstados) {
        this.id = id;
        this.clienteId = clienteId;
        this.lineas = List.copyOf(lineas);
        this.estado = estado;
        this.importe = importe;
        this.fechaCreacion = fechaCreacion;
        this.fechaPago = fechaPago;
        this.direccionEnvio = copiarDireccion(direccionEnvio);
        this.metodoPagoId = metodoPagoId;
        this.historialEstados = historialEstados == null
                ? List.of()
                : List.copyOf(historialEstados);
    }

    public String getId() {
        return id;
    }

    public Pedido pagado(Instant fechaPago) {
    if (estado != EstadoPedido.EN_PROCESO) {
        throw new IllegalStateException("Solo se puede pagar un pedido en proceso.");
    }
    return cambiarEstado(EstadoPedido.PAGADO_COMPLETADO, fechaPago);
    }

    public Pedido cancelar(Instant fecha) {
        if (estado == null || !estado.permiteTransicionA(EstadoPedido.CANCELADO)) {
            throw new IllegalStateException("El pedido no se puede cancelar en su estado actual.");
        }
        return cambiarEstado(EstadoPedido.CANCELADO, fecha);
    }

    public Pedido cambiarEstado(EstadoPedido nuevoEstado, Instant fecha) {
    if (nuevoEstado == null || fecha == null) {
        throw new IllegalArgumentException("El estado y la fecha son obligatorios.");
    }
    if (nuevoEstado == estado) {
        return this;
    }
    if (!transicionPermitida(nuevoEstado)) {
        throw new IllegalStateException("La transición de estado del pedido no es válida.");
    }

    List<CambioEstadoPedido> nuevoHistorial = new ArrayList<>(historialEstados);
    nuevoHistorial.add(new CambioEstadoPedido(nuevoEstado, fecha));

    Instant nuevaFechaPago = nuevoEstado == EstadoPedido.PAGADO_COMPLETADO
            ? fecha
            : fechaPago;

    Pedido actualizado = new Pedido(id, clienteId, lineas, nuevoEstado, importe,
            fechaCreacion, nuevaFechaPago, direccionEnvio, metodoPagoId, nuevoHistorial);
    actualizado.setClienteNombre(clienteNombre);
    return actualizado;
    }

    private boolean transicionPermitida(EstadoPedido nuevoEstado) {
        return estado != null && estado.permiteTransicionA(nuevoEstado);
    }

    public Pedido desvinculadoCliente() {
        Pedido pedidoAnonimizado = new Pedido(id, null, lineas, estado, importe,
                fechaCreacion, fechaPago, null, null, historialEstados);
        pedidoAnonimizado.setClienteNombre("cliente eliminado");
        return pedidoAnonimizado;
    }

    public String getClienteId() {
        return clienteId;
    }

    public String getClienteNombre() {
        return clienteNombre;
    }

    public void setClienteNombre(String clienteNombre) {
        this.clienteNombre = clienteNombre;
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

    public List<CambioEstadoPedido> getHistorialEstados() {
    return List.copyOf(historialEstados);
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
