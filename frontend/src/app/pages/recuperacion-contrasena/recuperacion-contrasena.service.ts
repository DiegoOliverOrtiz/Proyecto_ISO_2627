import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, switchMap } from 'rxjs';

import { environment } from '../../../environments/environment';
import type { RespuestaTokenCsrf } from '../../shared/models/respuesta-token-csrf';

export interface SolicitarRecuperacionRequest {
  email: string;
}

export interface CompletarRecuperacionRequest {
  token: string;
  nuevaContrasena: string;
}

export interface SolicitudRecuperacionResponse {
  message: string;
}

@Injectable({ providedIn: 'root' })
export class RecuperacionContrasenaService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/usuarios/recuperar-contrasena`;

  solicitar(email: string): Observable<SolicitudRecuperacionResponse> {
    return this.conCsrf(token => this.http.post<SolicitudRecuperacionResponse>(
      this.baseUrl,
      { email } satisfies SolicitarRecuperacionRequest,
      {
        withCredentials: true,
        headers: new HttpHeaders({ 'X-XSRF-TOKEN': token })
      }
    ));
  }

  confirmar(token: string, nuevaContrasena: string): Observable<void> {
    return this.conCsrf(csrfToken => this.http.post<void>(
      `${this.baseUrl}/confirmar`,
      { token, nuevaContrasena } satisfies CompletarRecuperacionRequest,
      {
        withCredentials: true,
        headers: new HttpHeaders({ 'X-XSRF-TOKEN': csrfToken })
      }
    ));
  }

  private conCsrf<T>(peticion: (token: string) => Observable<T>): Observable<T> {
    return this.http.get<RespuestaTokenCsrf>(`${environment.apiUrl}/usuarios/csrf`, {
      withCredentials: true
    }).pipe(switchMap(({ token }) => peticion(token)));
  }
}
