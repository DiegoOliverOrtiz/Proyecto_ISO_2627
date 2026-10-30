import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, switchMap } from 'rxjs';

import { environment } from '../../../environments/environment';
import type { RespuestaTokenCsrf } from '../../shared/models/respuesta-token-csrf';

export interface LineaCarritoResponse {
  productoId: string;
  cantidad: number;
  precioUnitario: number;
  subtotal: number;
}

export interface CarritoResponse {
  productos: LineaCarritoResponse[];
  importe: number;
}

@Injectable({ providedIn: 'root' })
export class CarritoService {
  private readonly http = inject(HttpClient);

  anadirProducto(productoId: string, cantidad: number): Observable<CarritoResponse> {
    return this.http.get<RespuestaTokenCsrf>(`${environment.apiUrl}/usuarios/csrf`, {
      withCredentials: true
    }).pipe(
      switchMap(({ token }) => this.http.post<CarritoResponse>(
        `${environment.apiUrl}/cliente/carrito/productos`,
        { productoId, cantidad },
        { withCredentials: true, headers: new HttpHeaders().set('X-XSRF-TOKEN', token) }
      ))
    );
  }
}
