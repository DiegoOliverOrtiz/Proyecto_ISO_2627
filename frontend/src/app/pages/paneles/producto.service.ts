import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, switchMap } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface ProductoPropio {
  id: string;
  nombre: string;
  categoria: string;
  stock: number;
  descripcion: string | null;
  precio: number | null;
  descuento: number;
  descuentoPremium: number;
  esVisible: boolean;
  imagen: string | null;
  soloPremium: boolean;
}

export interface FiltrosCatalogo {
  nombre?: string;
  categoria?: string;
  precioMinimo?: number;
  precioMaximo?: number;
  disponible?: boolean;
}

export interface ProductoCatalogo {
  id: string;
  nombre: string;
  categoria: string;
  stock: number;
  precio: number | null;
  precioOriginal: number | null;
  descuento: number;
  descuentoPremium: number;
  descuentoAplicado: number;
  descripcion: string | null;
  imagen: string | null;
  vendedor: string | null;
  esVisible: boolean;
  soloPremium: boolean;
  puedeConsultarDetalle?: boolean;
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
  soloPremium: boolean;
}

// La visibilidad no se modifica desde el formulario: se gestiona con Activar/Desactivar (HU16).
export type ModificarProductoRequest = Omit<CrearProductoRequest, 'esVisible'>;

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

  consultarCatalogo(filtros: FiltrosCatalogo = {}): Observable<ProductoCatalogo[]> {
    let params = new HttpParams();
    if (filtros.nombre) params = params.set('nombre', filtros.nombre);
    if (filtros.categoria) params = params.set('categoria', filtros.categoria);
    if (filtros.precioMinimo !== undefined) {
      params = params.set('precioMinimo', filtros.precioMinimo);
    }
    if (filtros.precioMaximo !== undefined) {
      params = params.set('precioMaximo', filtros.precioMaximo);
    }
    if (filtros.disponible !== undefined) {
      params = params.set('disponible', filtros.disponible);
    }
    return this.http.get<ProductoCatalogo[]>(`${environment.apiUrl}/cliente/productos`, {
      params,
      withCredentials: true
    });
  }

  consultarDetalleProducto(id: string): Observable<ProductoCatalogo> {
    return this.http.get<ProductoCatalogo>(
      `${environment.apiUrl}/cliente/productos/${encodeURIComponent(id)}`,
      { withCredentials: true }
    );
  }

  consultarProductosPropios(): Observable<ProductoPropio[]> {
    return this.http.get<ProductoPropio[]>(
      `${environment.apiUrl}/vendedor/productos`,
      { withCredentials: true }
    );
  }

  crearProducto(request: CrearProductoRequest, imagen: File | null): Observable<CrearProductoResponse> {
    return this.conTokenCsrf((cabeceras) =>
      this.http.post<CrearProductoResponse>(
        `${environment.apiUrl}/vendedor/productos`,
        this.datosProducto(request, imagen),
        { withCredentials: true, headers: cabeceras }
      )
    );
  }

  modificarProducto(
    id: string,
    request: ModificarProductoRequest,
    imagen: File | null
  ): Observable<ProductoPropio> {
    return this.conTokenCsrf((cabeceras) =>
      this.http.put<ProductoPropio>(
        `${environment.apiUrl}/vendedor/productos/${encodeURIComponent(id)}`,
        this.datosProducto(request, imagen),
        { withCredentials: true, headers: cabeceras }
      )
    );
  }

  private datosProducto(
    request: CrearProductoRequest | ModificarProductoRequest,
    imagen: File | null
  ): FormData {
    const datosProducto = new FormData();
    datosProducto.append(
      'producto',
      new Blob([JSON.stringify(request)], { type: 'application/json' })
    );
    if (imagen) {
      datosProducto.append('imagen', imagen);
    }
    return datosProducto;
  }

  private conTokenCsrf<T>(peticion: (cabeceras: HttpHeaders) => Observable<T>): Observable<T> {
    return this.http
      .get<RespuestaTokenCsrf>(`${environment.apiUrl}/usuarios/csrf`, { withCredentials: true })
      .pipe(
        switchMap((respuestaCsrf) =>
          peticion(new HttpHeaders().set('X-XSRF-TOKEN', respuestaCsrf.token))
        )
      );
  }

  activarProducto(id: string): Observable<void> {
    return this.cambiarEstado(id, 'activar');
  }

  desactivarProducto(id: string): Observable<void> {
    return this.cambiarEstado(id, 'desactivar');
  }

  private cambiarEstado(id: string, accion: 'activar' | 'desactivar'): Observable<void> {
    return this.http
      .get<RespuestaTokenCsrf>(`${environment.apiUrl}/usuarios/csrf`, { withCredentials: true })
      .pipe(
        switchMap((respuestaCsrf) =>
          this.http.patch<void>(
            `${environment.apiUrl}/vendedor/productos/${encodeURIComponent(id)}/${accion}`,
            null,
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
