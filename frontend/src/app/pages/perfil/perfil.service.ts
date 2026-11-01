import type { RespuestaTokenCsrf } from '../../shared/models/respuesta-token-csrf';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, switchMap } from 'rxjs';

import { environment } from '../../../environments/environment';

export type TipoUsuario = 'ADMIN' | 'CLIENTE' | 'VENDEDOR';

export interface CategoriaPerfil {
  id: string;
  nombre: string;
}

export interface PerfilResponse {
  tipoUsuario: TipoUsuario;
  nombre: string;
  apellidos: string;
  email: string;
  telefono: string | null;
  imagenPerfil: string | null;
  dniNif: string | null;
  fechaNacimiento: string | null;
  esPremium: boolean | null;
  nombreComercial: string | null;
  categoria: string | null;
  categoriaId?: string | null;
  sede: string | null;
  fechaIncorporacion: string | null;
  rol: string | null;
  totpActivado: boolean;
}

export interface TwoFactorSetupResponse {
  otpauthUri: string | null;
  qrDataUrl: string | null;
  enabled: boolean;
}

export interface TwoFactorConfirmationResponse {
  enabled: boolean;
  backupCodes: string[];
}

export interface ModificarPerfilRequest {
  nombre: string;
  apellidos: string;
  telefono: string | null;
  imagenPerfil: string | null;
  dniNif?: string | null;
  fechaNacimiento?: string | null;
  sede?: string | null;
  categoriaId?: string | null;
}

@Injectable({ providedIn: 'root' })
export class PerfilService {
  private readonly http = inject(HttpClient);

  consultarPerfil(): Observable<PerfilResponse> {
    return this.http.get<PerfilResponse>(
      `${environment.apiUrl}/usuarios/perfil`,
      { withCredentials: true }
    );
  }

  obtenerCategorias(): Observable<CategoriaPerfil[]> {
    return this.http.get<CategoriaPerfil[]>(
      `${environment.apiUrl}/categorias/registro`
    );
  }

  modificarPerfil(
    datos: ModificarPerfilRequest
  ): Observable<PerfilResponse> {
    return this.http
      .get<RespuestaTokenCsrf>(
        `${environment.apiUrl}/usuarios/csrf`,
        { withCredentials: true }
      )
      .pipe(
        switchMap((respuestaCsrf) =>
          this.http.put<PerfilResponse>(
            `${environment.apiUrl}/usuarios/perfil`,
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

  cambiarTipoCliente(esPremium: boolean): Observable<PerfilResponse> {
    return this.http
      .get<RespuestaTokenCsrf>(
        `${environment.apiUrl}/usuarios/csrf`,
        { withCredentials: true }
      )
      .pipe(
        switchMap((respuestaCsrf) =>
          this.http.put<PerfilResponse>(
            `${environment.apiUrl}/cliente/tipo`,
            { esPremium },
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

  configurarSegundoFactor(): Observable<TwoFactorSetupResponse> {
    return this.peticionConCsrf<TwoFactorSetupResponse>('POST', '/usuarios/2fa/configurar');
  }

  confirmarSegundoFactor(code: string): Observable<TwoFactorConfirmationResponse> {
    return this.peticionConCsrf<TwoFactorConfirmationResponse>('POST', '/usuarios/2fa/confirmar', { code });
  }

  desactivarSegundoFactor(code: string): Observable<void> {
    return this.peticionConCsrf<void>('DELETE', '/usuarios/2fa', { code });
  }

  private peticionConCsrf<T>(method: 'POST' | 'DELETE', ruta: string, body?: unknown): Observable<T> {
    return this.http.get<RespuestaTokenCsrf>(`${environment.apiUrl}/usuarios/csrf`, { withCredentials: true }).pipe(
      switchMap((respuestaCsrf) => this.http.request<T>(method, `${environment.apiUrl}${ruta}`, {
        body,
        withCredentials: true,
        headers: new HttpHeaders({ 'X-XSRF-TOKEN': respuestaCsrf.token })
      }))
    );
  }
}
