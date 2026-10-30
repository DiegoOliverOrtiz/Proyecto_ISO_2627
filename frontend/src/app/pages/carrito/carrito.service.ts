import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, switchMap } from 'rxjs';

import { environment } from '../../../environments/environment';
import type { RespuestaTokenCsrf } from '../../shared/models/respuesta-token-csrf';

export interface LineaCarritoResponse {
  productoId: string;
  cantidad: number;
  precioUnitario: number | null;
  subtotal: number | null;
  nombre: string | null;
  stock: number | null;
  disponible: boolean;
  aviso: string | null;
}

export interface CarritoResponse {
  productos: LineaCarritoResponse[];
  importe: number;
}

@Injectable({ providedIn: 'root' })
export class CarritoService {
  private readonly http = inject(HttpClient);

  consultarCarrito(): Observable<CarritoResponse> {
    return this.http.get<CarritoResponse>(`${environment.apiUrl}/cliente/carrito`, { withCredentials: true });
  }

  modificarCantidad(productoId: string, cantidad: number): Observable<CarritoResponse> {
    return this.mutarCarrito('PUT', `/productos/${encodeURIComponent(productoId)}`, { cantidad });
  }

  eliminarProducto(productoId: string): Observable<CarritoResponse> {
    return this.mutarCarrito('DELETE', `/productos/${encodeURIComponent(productoId)}`);
  }

  descartarCarrito(): Observable<CarritoResponse> {
    return this.mutarCarrito('DELETE', '');
  }

  private mutarCarrito(metodo: string, ruta: string, body?: object): Observable<CarritoResponse> {
    return this.http.get<RespuestaTokenCsrf>(`${environment.apiUrl}/usuarios/csrf`, {
      withCredentials: true
    }).pipe(
      switchMap(({ token }) => this.http.request<CarritoResponse>(metodo,
        `${environment.apiUrl}/cliente/carrito${ruta}`, {
          body, withCredentials: true, headers: new HttpHeaders().set('X-XSRF-TOKEN', token)
        }))
    );
  }

  anadirProducto(productoId: string, cantidad: number): Observable<CarritoResponse> {
    return this.mutarCarrito('POST', '/productos', { productoId, cantidad });
  }
}
