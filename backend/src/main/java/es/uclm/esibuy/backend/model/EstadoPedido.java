package es.uclm.esibuy.backend.model;

public enum EstadoPedido {
    EN_PROCESO,
    PAGADO_COMPLETADO,
    TRAMITADO_ENVIADO,
    FINALIZADO,
    CANCELADO;

    // Los estados avanzan de uno en uno, en el orden declarado: no se permiten saltos ni retrocesos.
    public boolean permiteTransicionA(EstadoPedido destino) {
        if (destino == null) {
            return false;
        }
        if (destino == CANCELADO) {
            return this == EN_PROCESO || this == PAGADO_COMPLETADO;
        }
        return this != CANCELADO && destino.ordinal() == ordinal() + 1;
    }
}
