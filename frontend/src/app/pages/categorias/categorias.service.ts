import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, switchMap } from 'rxjs';

import { environment } from '../../../environments/environment';

export interface Categoria {
  id: string;
  nombre: string;
  descripcion: string;
}

export interface CrearCategoriaRequest {
  nombre: string;
  descripcion: string;
}

export interface ModificarCategoriaRequest {
  nombre: string;
  descripcion: string;
}

interface RespuestaTokenCsrf {
  token: string;
}

@Injectable({
  providedIn: 'root',
})
export class CategoriasService {
  private readonly http = inject(HttpClient);

  obtenerCategorias(): Observable<Categoria[]> {
    return this.http.get<Categoria[]>(
      `${environment.apiUrl}/categorias`,
      { withCredentials: true }
    );
  }

  obtenerCategoriaPorId(id: string): Observable<Categoria> {
    return this.http.get<Categoria>(
      `${environment.apiUrl}/categorias/${id}`,
      { withCredentials: true }
    );
  }

  crearCategoria(
    datos: CrearCategoriaRequest
  ): Observable<Categoria> {
    return this.http
      .get<RespuestaTokenCsrf>(
        `${environment.apiUrl}/usuarios/csrf`,
        { withCredentials: true }
      )
      .pipe(
        switchMap((respuestaCsrf) =>
          this.http.post<Categoria>(
            `${environment.apiUrl}/categorias`,
            datos,
            {
              withCredentials: true,
              headers: new HttpHeaders()
                .set('X-XSRF-TOKEN', respuestaCsrf.token)
            }
          )
        )
      );
  }

  modificarCategoria(
    id: string,
    datos: ModificarCategoriaRequest
  ): Observable<Categoria> {
    return this.http
      .get<RespuestaTokenCsrf>(
        `${environment.apiUrl}/usuarios/csrf`,
        { withCredentials: true }
      )
      .pipe(
        switchMap((respuestaCsrf) =>
          this.http.put<Categoria>(
            `${environment.apiUrl}/categorias/${encodeURIComponent(id)}`,
            datos,
            {
              withCredentials: true,
              headers: new HttpHeaders()
                .set('X-XSRF-TOKEN', respuestaCsrf.token)
            }
          )
        )
      );
  }

  eliminarCategoria(id: string): Observable<void> {
    return this.http
      .get<RespuestaTokenCsrf>(
        `${environment.apiUrl}/usuarios/csrf`,
        { withCredentials: true }
      )
      .pipe(
        switchMap((respuestaCsrf) =>
          this.http.delete<void>(
            `${environment.apiUrl}/categorias/${encodeURIComponent(id)}`,
            {
              withCredentials: true,
              headers: new HttpHeaders()
                .set('X-XSRF-TOKEN', respuestaCsrf.token)
            }
          )
        )
      );
  }
}