import type { RespuestaTokenCsrf } from '../../shared/models/respuesta-token-csrf';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, switchMap } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface RespuestaInicioSesion {
  email: string;
  role: 'ADMIN' | 'CLIENTE' | 'VENDEDOR';
  requiresTwoFactor?: boolean;
  twoFactorToken?: string | null;
  requiresTwoFactorSetup?: boolean;
  requiresEmailVerification?: boolean;
  emailVerificationEmailSent?: boolean;
}

export interface RespuestaConfiguracionTotp {
  otpauthUri: string;
  qrDataUrl: string;
  enabled: boolean;
}

export interface RespuestaAltaTotp {
  login: RespuestaInicioSesion;
  backupCodes: string[];
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

  verificarSegundoFactor(codigo: string): Observable<RespuestaInicioSesion> {
    return this.http
      .get<RespuestaTokenCsrf>(`${environment.apiUrl}/usuarios/csrf`, {
        withCredentials: true
      })
      .pipe(
        switchMap((respuestaCsrf) =>
          this.http.post<RespuestaInicioSesion>(
            `${environment.apiUrl}/usuarios/login/2fa`,
            { code: codigo },
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

  configurarTotpObligatorio(): Observable<RespuestaConfiguracionTotp> {
    return this.http
      .get<RespuestaTokenCsrf>(
        `${environment.apiUrl}/usuarios/csrf`,
        { withCredentials: true }
      )
      .pipe(
        switchMap(({ token }) =>
          this.http.post<RespuestaConfiguracionTotp>(
            `${environment.apiUrl}/usuarios/login/2fa/configurar`,
            {},
            {
              withCredentials: true,
              headers: new HttpHeaders({
                'X-XSRF-TOKEN': token
              })
            }
          )
        )
      );
  }

  confirmarTotpObligatorio(codigo: string): Observable<RespuestaAltaTotp> {
    return this.http
      .get<RespuestaTokenCsrf>(
        `${environment.apiUrl}/usuarios/csrf`,
        { withCredentials: true }
      )
      .pipe(
        switchMap(({ token }) =>
          this.http.post<RespuestaAltaTotp>(
            `${environment.apiUrl}/usuarios/login/2fa/confirmar-alta`,
            { code: codigo },
            {
              withCredentials: true,
              headers: new HttpHeaders({
                'X-XSRF-TOKEN': token
              })
            }
          )
        )
      );
  }

  verificarTercerFactor(codigo: string): Observable<RespuestaInicioSesion> {
    return this.http
      .get<RespuestaTokenCsrf>(`${environment.apiUrl}/usuarios/csrf`, {
        withCredentials: true
      })
      .pipe(
        switchMap(({ token }) =>
          this.http.post<RespuestaInicioSesion>(
            `${environment.apiUrl}/usuarios/login/3fa/verificar`,
            { code: codigo },
            {
              withCredentials: true,
              headers: new HttpHeaders({
                'X-XSRF-TOKEN': token
              })
            }
          )
        )
      );
  }

  reenviarTercerFactor(): Observable<void> {
    return this.http
      .get<RespuestaTokenCsrf>(`${environment.apiUrl}/usuarios/csrf`, {
        withCredentials: true
      })
      .pipe(
        switchMap(({ token }) =>
          this.http.post<void>(
            `${environment.apiUrl}/usuarios/login/3fa/reenviar`,
            {},
            {
              withCredentials: true,
              headers: new HttpHeaders({
                'X-XSRF-TOKEN': token
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
