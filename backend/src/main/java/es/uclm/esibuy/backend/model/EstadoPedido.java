package es.uclm.esibuy.backend.model;

public enum EstadoPedido {
    EN_PROCESO,
    PAGADO_COMPLETADO,
    TRAMITADO_ENVIADO,
    FINALIZADO;

    // Los estados avanzan de uno en uno, en el orden declarado: no se permiten saltos ni retrocesos.
    public boolean permiteTransicionA(EstadoPedido destino) {
        return destino != null && destino.ordinal() == ordinal() + 1;
    }
}
