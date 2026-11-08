import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, switchMap } from 'rxjs';

import { environment } from '../../../environments/environment';
import type { RespuestaTokenCsrf } from '../../shared/models/respuesta-token-csrf';
import type { ProductoFavorito } from '../favoritos/favoritos.service';

export interface ResumenLista {
  id: string;
  nombre: string;
  predeterminada: boolean;
  productoIds: string[];
}

export interface ListaProductos {
  id: string;
  nombre: string;
  predeterminada: boolean;
  productos: ProductoFavorito[];
}

// HU-39: el propietario lo decide el backend a partir de la sesión.
@Injectable({ providedIn: 'root' })
export class ListasService {

  private readonly http = inject(HttpClient);

  private readonly url = `${environment.apiUrl}/usuarios/listas`;

  consultarListas(): Observable<ResumenLista[]> {
    return this.http.get<ResumenLista[]>(this.url, { withCredentials: true });
  }

  consultarLista(listaId: string): Observable<ListaProductos> {
    return this.http.get<ListaProductos>(this.rutaLista(listaId), { withCredentials: true });
  }

  crearLista(nombre: string): Observable<ListaProductos> {
    return this.mutar<ListaProductos>('POST', this.url, { nombre });
  }

  renombrarLista(listaId: string, nombre: string): Observable<ListaProductos> {
    return this.mutar<ListaProductos>('PUT', this.rutaLista(listaId), { nombre });
  }

  eliminarLista(listaId: string): Observable<void> {
    return this.mutar<void>('DELETE', this.rutaLista(listaId));
  }

  anadirProducto(listaId: string, productoId: string): Observable<ListaProductos> {
    return this.mutar<ListaProductos>('PUT', this.rutaProducto(listaId, productoId));
  }

  quitarProducto(listaId: string, productoId: string): Observable<ListaProductos> {
    return this.mutar<ListaProductos>('DELETE', this.rutaProducto(listaId, productoId));
  }

  private rutaLista(listaId: string): string {
    return `${this.url}/${encodeURIComponent(listaId)}`;
  }

  private rutaProducto(listaId: string, productoId: string): string {
    return `${this.rutaLista(listaId)}/productos/${encodeURIComponent(productoId)}`;
  }

  private mutar<T>(
    metodo: 'POST' | 'PUT' | 'DELETE',
    url: string,
    cuerpo?: { nombre: string }
  ): Observable<T> {
    return this.http.get<RespuestaTokenCsrf>(
      `${environment.apiUrl}/usuarios/csrf`,
      { withCredentials: true }
    ).pipe(
      switchMap(({ token }) =>
        this.http.request<T>(metodo, url, {
          body: cuerpo,
          withCredentials: true,
          headers: new HttpHeaders().set('X-XSRF-TOKEN', token)
        })
      )
    );
  }
}
