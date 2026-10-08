import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, switchMap } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface ProductoPropio {
  nombre: string;
  categoria: string;
  stock: number;
  descripcion: string | null;
  precio: number | null;
  descuento: number;
  descuentoPremium: number;
  esVisible: boolean;
  imagen: string | null;
}

export interface CrearProductoRequest {
  nombre: string;
  categoria: string;
  descripcion: string | null;
  esVisible: boolean;
  precio: number;
  stock: number;
  descuento: number;
  descuentoPremium: number;
}

export interface CrearProductoResponse {
  nombre: string;
  categoria: string;
  precio: number;
  stock: number;
  descuento: number;
  descuentoPremium: number;
  imagen: string | null;
}

interface RespuestaTokenCsrf {
  token: string;
}

@Injectable({ providedIn: 'root' })
export class ProductoService {
  private readonly http = inject(HttpClient);

  consultarProductosPropios(): Observable<ProductoPropio[]> {
    return this.http.get<ProductoPropio[]>(
      `${environment.apiUrl}/vendedor/productos`,
      { withCredentials: true }
    );
  }

  crearProducto(request: CrearProductoRequest, imagen: File | null): Observable<CrearProductoResponse> {
    const datosProducto = new FormData();
    datosProducto.append(
      'producto',
      new Blob([JSON.stringify(request)], { type: 'application/json' })
    );
    if (imagen) {
      datosProducto.append('imagen', imagen);
    }

    return this.http
      .get<RespuestaTokenCsrf>(`${environment.apiUrl}/usuarios/csrf`, { withCredentials: true })
      .pipe(
        switchMap((respuestaCsrf) =>
          this.http.post<CrearProductoResponse>(
            `${environment.apiUrl}/vendedor/productos`,
            datosProducto,
            {
              withCredentials: true,
              headers: new HttpHeaders().set('X-XSRF-TOKEN', respuestaCsrf.token),
            }
          )
        )
      );
  }

  urlImagen(imagen: string): string {
    return `${environment.apiUrl}${imagen}`;
  }
}
