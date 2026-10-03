import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, switchMap } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface RespuestaInicioSesion {
  email: string;
  role: 'ADMIN' | 'CLIENTE' | 'VENDEDOR';
}

interface RespuestaTokenCsrf {
  token: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  iniciarSesion(correo: string, contrasena: string): Observable<RespuestaInicioSesion> {
    return this.http
      .get<RespuestaTokenCsrf>(`${environment.apiUrl}/usuarios/csrf`, {
        withCredentials: true
      })
      .pipe(
        switchMap((respuestaCsrf) =>
          this.http.post<RespuestaInicioSesion>(
            `${environment.apiUrl}/usuarios/login`,
            { email: correo, password: contrasena },
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

  obtenerSesionActual(): Observable<RespuestaInicioSesion> {
    return this.http.get<RespuestaInicioSesion>(
      `${environment.apiUrl}/usuarios/sesion-actual`,
      { withCredentials: true }
    );
  }

  cerrarSesion(): Observable<void> {
    return this.http
      .get<RespuestaTokenCsrf>(`${environment.apiUrl}/usuarios/csrf`, {
        withCredentials: true
      })
      .pipe(
        switchMap((respuestaCsrf) =>
          this.http.post<void>(`${environment.apiUrl}/usuarios/logout`, null, {
            withCredentials: true,
            headers: new HttpHeaders({
              'X-XSRF-TOKEN': respuestaCsrf.token
            })
          })
        )
      );
  }
}
