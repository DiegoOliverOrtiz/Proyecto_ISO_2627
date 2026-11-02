import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, switchMap } from 'rxjs';

import { environment } from '../../../environments/environment';
import type { RespuestaTokenCsrf } from '../../shared/models/respuesta-token-csrf';

export interface ProductoFavorito {
  id: string;
  nombre: string;
  imagen: string | null;
  precio: number | null;
  stock: number | null;
  disponible: boolean;
  aviso: string | null;
}

export interface FavoritosResponse {
  ids: string[];
  productos: ProductoFavorito[];
}

@Injectable({ providedIn: 'root' })
export class FavoritosService {

  private readonly http = inject(HttpClient);

  private readonly url =
    `${environment.apiUrl}/usuarios/favoritos`;

  consultar(): Observable<FavoritosResponse> {
    return this.http.get<FavoritosResponse>(
      this.url,
      { withCredentials: true }
    );
  }

  anadir(productoId: string): Observable<FavoritosResponse> {
    return this.modificar('PUT', productoId);
  }

  quitar(productoId: string): Observable<FavoritosResponse> {
    return this.modificar('DELETE', productoId);
  }

  private modificar(
    metodo: 'PUT' | 'DELETE',
    productoId: string
  ): Observable<FavoritosResponse> {

    return this.http.get<RespuestaTokenCsrf>(
      `${environment.apiUrl}/usuarios/csrf`,
      { withCredentials: true }
    ).pipe(
      switchMap(({ token }) =>
        this.http.request<FavoritosResponse>(
          metodo,
          `${this.url}/${encodeURIComponent(productoId)}`,
          {
            withCredentials: true,
            headers: new HttpHeaders()
              .set('X-XSRF-TOKEN', token)
          }
        )
      )
    );
  }
}
