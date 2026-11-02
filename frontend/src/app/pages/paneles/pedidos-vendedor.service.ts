import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface LineaPedidoVendedor {
  productoId: string;
  nombreProducto: string;
  precioCompra: number;
  cantidad: number;
}

export interface DireccionPedidoVendedor {
  [campo: string]: unknown;
}

export interface PedidoVendedor {
  id: string;
  clienteNombre: string;
  estado: 'PAGADO_COMPLETADO' | 'TRAMITADO_ENVIADO' | 'FINALIZADO';
  fechaCreacion: string;
  fechaPago: string | null;
  productos: LineaPedidoVendedor[];
  importeProductosPropios: number;
  direccionEnvio: DireccionPedidoVendedor | null;
}

@Injectable({
  providedIn: 'root'
})
export class PedidosVendedorService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/vendedor/pedidos`;

  consultarActivos(): Observable<PedidoVendedor[]> {
    return this.http.get<PedidoVendedor[]>(`${this.baseUrl}/activos`, {
      withCredentials: true
    });
  }

  consultarHistorico(): Observable<PedidoVendedor[]> {
    return this.http.get<PedidoVendedor[]>(`${this.baseUrl}/historico`, {
      withCredentials: true
    });
  }

  consultarDetalle(pedidoId: string): Observable<PedidoVendedor> {
    return this.http.get<PedidoVendedor>(
      `${this.baseUrl}/${encodeURIComponent(pedidoId)}`,
      { withCredentials: true }
    );
  }
}
