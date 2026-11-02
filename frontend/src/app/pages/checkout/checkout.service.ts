import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, Observable, switchMap, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import type { RespuestaTokenCsrf } from '../../shared/models/respuesta-token-csrf';
import type { DireccionResponse } from '../direcciones/direcciones.service';

export class ErrorPreparacionPago extends Error {
  constructor(readonly causa: HttpErrorResponse) {
    super('No se pudo preparar el pago.');
  }
}

export interface CrearPedidoRequest {
  direccionId: string;
  metodoPagoId: string;
  operacionId: string;
}

export interface LineaPedido {
  productoId: string;
  nombreProducto: string;
  precioCompra: number;
  cantidad: number;
  vendedorId: string;
  nombreVendedor: string;
}

export interface PedidoResponse {
  id: string;
  lineas: LineaPedido[];
  estado: 'EN_PROCESO' | 'PAGADO_COMPLETADO' | 'TRAMITADO_ENVIADO' | 'FINALIZADO';
  importe: number;
  fechaCreacion: string;
  fechaPago: string | null;
  direccionEnvio: DireccionResponse;
  metodoPagoId: string;
}

@Injectable({ providedIn: 'root' })
export class CheckoutService {
  private readonly http = inject(HttpClient);

  pagar(request: CrearPedidoRequest): Observable<PedidoResponse> {
    return this.http.get<RespuestaTokenCsrf>(`${environment.apiUrl}/usuarios/csrf`, { withCredentials: true }).pipe(
      catchError(fallo => throwError(() => new ErrorPreparacionPago(fallo))),
      switchMap(({ token }) => this.http.post<PedidoResponse>(`${environment.apiUrl}/cliente/pedidos`, {
        direccionId: request.direccionId, metodoPagoId: request.metodoPagoId, operacionId: request.operacionId
      }, {
        withCredentials: true, headers: new HttpHeaders({ 'X-XSRF-TOKEN': token })
      }))
    );
  }

  obtenerOperacion(email: string): CrearPedidoRequest | null {
    const guardado = sessionStorage.getItem(this.clave(email));
    if (!guardado) return null;
    const request: CrearPedidoRequest = JSON.parse(guardado);
    if (!request || typeof request.direccionId !== 'string' || !request.direccionId.trim()
      || request.direccionId.length > 128 || typeof request.metodoPagoId !== 'string' || !request.metodoPagoId.trim()
      || request.metodoPagoId.length > 128 || typeof request.operacionId !== 'string'
      || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(request.operacionId)) {
      throw new Error('No se pudo recuperar la operación pendiente.');
    }
    // Recuperar únicamente los campos del contrato, aunque se manipule el almacenamiento.
    return { direccionId: request.direccionId, metodoPagoId: request.metodoPagoId, operacionId: request.operacionId };
  }

  guardarOperacion(email: string, request: CrearPedidoRequest): void {
    sessionStorage.setItem(this.clave(email), JSON.stringify(request));
  }

  limpiarOperacion(email: string): void {
    try {
      sessionStorage.removeItem(this.clave(email));
    } catch {
      // Si el navegador impide borrarla, repetir la misma clave sigue siendo idempotente.
    }
  }

  private clave(email: string): string {
    return `esibuy.checkout.${email.trim().toLowerCase()}`;
  }
}
