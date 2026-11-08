import type { RespuestaTokenCsrf } from '../../shared/models/respuesta-token-csrf';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, switchMap } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface UsuarioAdministracion {
  id: string;
  nombre: string;
  apellidos: string;
  email: string;
  telefono: string | null;
  estado: string;
  rol: string;
  imagenPerfil: string | null;
  sede: string | null;
  fechaIncorporacion: string | null;
  esPremium: boolean | null;
  nombreComercial: string | null;
  dniNif: string | null;
  fechaNacimiento: string | null;
  categoriaId: string | null;
}

export interface ModificarUsuarioRequest {
  nombre: string;
  apellidos: string;
  telefono: string | null;
  imagenPerfil: string | null;
  dniNif?: string | null;
  fechaNacimiento?: string | null;
  sede?: string | null;
}

export interface ListadoUsuarios {
  contenido: UsuarioAdministracion[];
  pagina: number;
  tamano: number;
  totalElementos: number;
  totalPaginas: number;
  estadisticas: EstadisticasUsuarios;
}

export interface EstadisticasUsuarios {
  totalUsuarios: number;
  activos: number;
  pendientes: number;
  desactivados: number;
}

@Injectable({ providedIn: 'root' })
export class UsuariosService {

  private readonly http = inject(HttpClient);

  private readonly url =
    `${environment.apiUrl}/administrador/usuarios`;

  private readonly csrfUrl =
    `${environment.apiUrl}/usuarios/csrf`;

  consultarUsuarios(
    busqueda: string,
    estado: string,
    rol: string,
    pagina: number,
    tamano: number
  ): Observable<ListadoUsuarios> {

    let parametros = new HttpParams()
      .set('page', pagina)
      .set('size', tamano)
      .set('sort', 'apellidos,asc')
      .append('sort', 'nombre,asc');

    if (busqueda.trim()) {
      parametros = parametros.set(
        'busqueda',
        busqueda.trim()
      );
    }

    if (estado) {
      parametros = parametros.set(
        'estado',
        estado
      );
    }

    if (rol) {
      parametros = parametros.set(
        'rol',
        rol
      );
    }

    return this.http.get<ListadoUsuarios>(
      this.url,
      {
        params: parametros,
        withCredentials: true
      }
    );
  }

  consultarUsuario(
    id: string
  ): Observable<UsuarioAdministracion> {

    return this.http.get<UsuarioAdministracion>(
      `${this.url}/${encodeURIComponent(id)}`,
      {
        withCredentials: true
      }
    );
  }

  modificarUsuario(
    id: string,
    datos: ModificarUsuarioRequest
  ): Observable<UsuarioAdministracion> {

    return this.http
      .get<RespuestaTokenCsrf>(
        this.csrfUrl,
        {
          withCredentials: true
        }
      )
      .pipe(
        switchMap((respuestaCsrf) =>
          this.http.put<UsuarioAdministracion>(
            `${this.url}/${encodeURIComponent(id)}`,
            datos,
            {
              withCredentials: true,
              headers: new HttpHeaders({
                'X-XSRF-TOKEN': respuestaCsrf.token
              })
            }
          )
        )
      );
  }

  activarUsuario(id: string): Observable<void> {
    return this.http
      .get<RespuestaTokenCsrf>(
        this.csrfUrl,
        {
          withCredentials: true
        }
      )
      .pipe(
        switchMap((respuestaCsrf) =>
          this.http.patch<void>(
            `${this.url}/${encodeURIComponent(id)}/activar`,
            null,
            {
              withCredentials: true,
              headers: new HttpHeaders({
                'X-XSRF-TOKEN': respuestaCsrf.token
              })
            }
          )
        )
      );
  }

  bloquearUsuario(
    id: string
  ): Observable<void> {

    return this.cambiarBloqueo(
      id,
      'bloquear'
    );
  }

  desbloquearUsuario(
    id: string
  ): Observable<void> {

    return this.cambiarBloqueo(
      id,
      'desbloquear'
    );
  }

  private cambiarBloqueo(
    id: string,
    accion: 'bloquear' | 'desbloquear'
  ): Observable<void> {

    return this.http
      .get<RespuestaTokenCsrf>(
        this.csrfUrl,
        {
          withCredentials: true
        }
      )
      .pipe(
        switchMap((respuestaCsrf) =>
          this.http.patch<void>(
            `${this.url}/${encodeURIComponent(id)}/${accion}`,
            null,
            {
              withCredentials: true,
              headers: new HttpHeaders({
                'X-XSRF-TOKEN': respuestaCsrf.token
              })
            }
          )
        )
      );
  }
}