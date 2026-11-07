package es.uclm.esibuy.backend.services;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import es.uclm.esibuy.backend.dao.ClienteDAO;
import es.uclm.esibuy.backend.dao.PedidoDAO;
import es.uclm.esibuy.backend.dao.ValoracionDAO;
import es.uclm.esibuy.backend.dto.ValoracionPublicaResponse;
import es.uclm.esibuy.backend.dto.ValoracionesProductoResponse;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.EstadoPedido;
import es.uclm.esibuy.backend.model.Pedido;
import es.uclm.esibuy.backend.model.Valoracion;

@Service
public class ValoracionService {
    private static final int PUNTUACION_MINIMA = 1;
    private static final int PUNTUACION_MAXIMA = 5;
    private static final int LONGITUD_MAXIMA_COMENTARIO = 500;
    private static final String MENSAJE_PRODUCTO_OBLIGATORIO = "El producto es obligatorio.";

    private final ValoracionDAO valoracionDAO;
    private final PedidoDAO pedidoDAO;
    private final ClienteDAO clienteDAO;

    public ValoracionService(
            ValoracionDAO valoracionDAO,
            PedidoDAO pedidoDAO,
            ClienteDAO clienteDAO) {
        this.valoracionDAO = valoracionDAO;
        this.pedidoDAO = pedidoDAO;
        this.clienteDAO = clienteDAO;
    }

    public Valoracion valorar(
            String emailCliente,
            String pedidoId,
            String productoId,
            int puntuacion,
            String comentario) {
        validarPuntuacion(puntuacion);
        validarComentario(comentario);
        validarIdentificador(pedidoId, "El pedido es obligatorio.");
        validarIdentificador(productoId, MENSAJE_PRODUCTO_OBLIGATORIO);

        Cliente cliente = clienteDAO.findByEmail(normalizarEmail(emailCliente))
                .orElseThrow(() -> new IllegalArgumentException("El cliente no existe."));
        Pedido pedido = pedidoDAO.findByIdAndClienteId(pedidoId, cliente.getId())
                .orElseThrow(() -> new IllegalArgumentException("El pedido no pertenece al cliente."));
        validarPedidoContieneProducto(pedido, productoId);
        if (valoracionDAO.existsByClienteIdAndProductoId(cliente.getId(), productoId)) {
            throw new IllegalStateException("El cliente ya ha valorado este producto.");
        }

        return valoracionDAO.save(new Valoracion(
                cliente.getId(),
                pedidoId,
                productoId,
                puntuacion,
                comentario));
    }

    private static void validarPuntuacion(int puntuacion) {
        if (puntuacion < PUNTUACION_MINIMA || puntuacion > PUNTUACION_MAXIMA) {
            throw new IllegalArgumentException("La puntuación debe estar entre 1 y 5.");
        }
    }

    private static void validarComentario(String comentario) {
        if (comentario != null && comentario.length() > LONGITUD_MAXIMA_COMENTARIO) {
            throw new IllegalArgumentException("El comentario no puede superar los 500 caracteres.");
        }
    }

    private static void validarPedidoContieneProducto(Pedido pedido, String productoId) {
        if (pedido.getEstado() != EstadoPedido.FINALIZADO) {
            throw new IllegalStateException("Solo se pueden valorar pedidos finalizados.");
        }
        boolean productoIncluido = pedido.getLineas().stream()
                .anyMatch(linea -> Objects.equals(linea.productoId(), productoId));
        if (!productoIncluido) {
            throw new IllegalArgumentException("El producto no pertenece al pedido.");
        }
    }

    public double calcularMediaProducto(String productoId) {
        validarIdentificador(productoId, MENSAJE_PRODUCTO_OBLIGATORIO);
        return valoracionDAO.findByProductoId(productoId).stream()
                .mapToInt(Valoracion::puntuacion)
                .average()
                .orElse(0.0);
    }

    public ValoracionesProductoResponse consultarValoracionesProducto(String productoId) {
        validarIdentificador(productoId, MENSAJE_PRODUCTO_OBLIGATORIO);
        List<Valoracion> valoraciones = valoracionDAO.findByProductoId(productoId);
        double media = valoraciones.stream()
                .mapToInt(Valoracion::puntuacion)
                .average()
                .orElse(0.0);
        List<ValoracionPublicaResponse> respuestas = valoraciones.stream()
                .map(ValoracionPublicaResponse::de)
                .toList();
        return new ValoracionesProductoResponse(media, respuestas.size(), respuestas);
    }

    public boolean estaValorado(String emailCliente, String pedidoId, String productoId) {
        validarIdentificador(pedidoId, "El pedido es obligatorio.");
        validarIdentificador(productoId, MENSAJE_PRODUCTO_OBLIGATORIO);

        Cliente cliente = clienteDAO.findByEmail(normalizarEmail(emailCliente))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Pedido pedido = pedidoDAO.findByIdAndClienteId(pedidoId, cliente.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        boolean productoIncluido = pedido.getLineas().stream()
                .anyMatch(linea -> Objects.equals(linea.productoId(), productoId));
        if (!productoIncluido) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        return valoracionDAO.existsByClienteIdAndProductoId(cliente.getId(), productoId);
    }

    private static void validarIdentificador(String identificador, String mensaje) {
        if (identificador == null || identificador.isBlank()) {
            throw new IllegalArgumentException(mensaje);
        }
    }

    private static String normalizarEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("El cliente es obligatorio.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
