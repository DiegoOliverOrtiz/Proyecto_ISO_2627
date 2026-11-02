import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, catchError, forkJoin, map, of, switchMap } from 'rxjs';

import { environment } from '../../../environments/environment';
import type { RespuestaTokenCsrf } from '../../shared/models/respuesta-token-csrf';

export interface LineaCarritoResponse {
  productoId: string;
  cantidad: number;
  precioUnitario: number | null;
  subtotal: number | null;
  nombre: string | null;
  imagen: string | null;
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
    return this.http.get<CarritoResponse>(`${environment.apiUrl}/cliente/carrito`, { withCredentials: true })
      .pipe(switchMap(carrito => this.enriquecerImagenes(carrito)));
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
        })),
      switchMap(carrito => this.enriquecerImagenes(carrito))
    );
  }

  private enriquecerImagenes(carrito: CarritoResponse): Observable<CarritoResponse> {
    const lineasSinImagen = carrito.productos.filter(linea => linea.imagen === undefined);
    if (lineasSinImagen.length === 0) return of(carrito);

    return forkJoin(lineasSinImagen.map(linea => this.http.get<{ imagen?: string | null }>(
      `${environment.apiUrl}/cliente/productos/${encodeURIComponent(linea.productoId)}`,
      { withCredentials: true }
    ).pipe(
      map(producto => [linea.productoId, producto.imagen ?? null] as const),
      catchError(() => of([linea.productoId, null] as const))
    ))).pipe(
      map(imagenes => {
        const porProducto = new Map(imagenes);
        return {
          ...carrito,
          productos: carrito.productos.map(linea => ({
            ...linea,
            imagen: linea.imagen ?? porProducto.get(linea.productoId) ?? null
          }))
        };
      })
    );
  }

  anadirProducto(productoId: string, cantidad: number): Observable<CarritoResponse> {
    return this.mutarCarrito('POST', '/productos', { productoId, cantidad });
  }
}
