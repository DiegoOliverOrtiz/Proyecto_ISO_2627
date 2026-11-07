import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, switchMap } from 'rxjs';

import { environment } from '../../../environments/environment';
import type { RespuestaTokenCsrf } from '../../shared/models/respuesta-token-csrf';

export type EstadoPedido =
  | 'EN_PROCESO'
  | 'PAGADO_COMPLETADO'
  | 'TRAMITADO_ENVIADO'
  | 'FINALIZADO'
  | 'CANCELADO';

export interface LineaPedido {
  productoId: string;
  nombreProducto: string;
  precioCompra: number;
  cantidad: number;
  vendedorId: string;
  nombreVendedor: string;
}

export interface DireccionPedido {
  nombre: string;
  calle: string;
  numero: string;
  portal: string | null;
  piso: string | null;
  codigoPostal: string;
  ciudad: string;
  provincia: string;
}

export interface CambioEstadoPedido {
  estado: EstadoPedido;
  fecha: string;
}

export interface PedidoCliente {
  id: string;
  lineas: LineaPedido[];
  estado: EstadoPedido;
  importe: number;
  fechaCreacion: string;
  fechaPago: string | null;
  direccionEnvio: DireccionPedido | null;
  historialEstados: CambioEstadoPedido[];
}

export interface EstadoValoracion {
  valorada: boolean;
}

export interface CrearValoracion {
  puntuacion: number;
  comentario: string;
}

export interface ValoracionCliente extends CrearValoracion {
  id: string;
  pedidoId: string;
  productoId: string;
}

@Injectable({ providedIn: 'root' })
export class PedidosService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/cliente/pedidos`;

  consultarActivos(): Observable<PedidoCliente[]> {
    return this.http.get<PedidoCliente[]>(`${this.baseUrl}/activos`, {
      withCredentials: true
    });
  }

  consultarHistorico(): Observable<PedidoCliente[]> {
    return this.http.get<PedidoCliente[]>(`${this.baseUrl}/historico`, {
      withCredentials: true
    });
  }

  consultarDetalle(pedidoId: string): Observable<PedidoCliente> {
    return this.http.get<PedidoCliente>(
      `${this.baseUrl}/${encodeURIComponent(pedidoId)}`,
      { withCredentials: true }
    );
  }

  cancelarPedido(pedidoId: string): Observable<PedidoCliente> {
    return this.http
      .get<RespuestaTokenCsrf>(`${environment.apiUrl}/usuarios/csrf`, {
        withCredentials: true
      })
      .pipe(
        switchMap(({ token }) =>
          this.http.post<PedidoCliente>(
            `${this.baseUrl}/${encodeURIComponent(pedidoId)}/cancelar`,
            null,
            {
              withCredentials: true,
              headers: new HttpHeaders({ 'X-XSRF-TOKEN': token })
            }
          )
        )
      );
  }

  confirmarRecepcion(pedidoId: string): Observable<PedidoCliente> {
    return this.http
      .get<RespuestaTokenCsrf>(`${environment.apiUrl}/usuarios/csrf`, {
        withCredentials: true
      })
      .pipe(
        switchMap(({ token }) =>
          this.http.post<PedidoCliente>(
            `${this.baseUrl}/${encodeURIComponent(pedidoId)}/confirmar-recepcion`,
            null,
            {
              withCredentials: true,
              headers: new HttpHeaders({ 'X-XSRF-TOKEN': token })
            }
          )
        )
      );
  }

  consultarEstadoValoracion(
    pedidoId: string,
    productoId: string
  ): Observable<EstadoValoracion> {
    return this.http.get<EstadoValoracion>(
      `${this.baseUrl}/${encodeURIComponent(pedidoId)}`
        + `/productos/${encodeURIComponent(productoId)}`
        + '/valoraciones/estado',
      { withCredentials: true }
    );
  }

  valorarProducto(
    pedidoId: string,
    productoId: string,
    valoracion: CrearValoracion
  ): Observable<ValoracionCliente> {
    return this.http
      .get<RespuestaTokenCsrf>(`${environment.apiUrl}/usuarios/csrf`, {
        withCredentials: true
      })
      .pipe(
        switchMap(({ token }) =>
          this.http.post<ValoracionCliente>(
            `${this.baseUrl}/${encodeURIComponent(pedidoId)}`
              + `/productos/${encodeURIComponent(productoId)}`
              + '/valoraciones',
            valoracion,
            {
              withCredentials: true,
              headers: new HttpHeaders({ 'X-XSRF-TOKEN': token })
            }
          )
        )
      );
  }
}
