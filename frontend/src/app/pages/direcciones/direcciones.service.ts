import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, switchMap } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface DireccionRequest {
  nombre: string;
  calle: string;
  numero: string;
  portal?: string | null;
  piso?: string | null;
  codigoPostal: string;
  ciudad: string;
  provincia: string;
}

export interface DireccionResponse extends DireccionRequest {
  id: string;
}

interface RespuestaTokenCsrf {
  token: string;
}

@Injectable({ providedIn: 'root' })
export class DireccionesService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/cliente/direcciones`;
  private readonly csrfUrl = `${environment.apiUrl}/usuarios/csrf`;

  consultarDirecciones(): Observable<DireccionResponse[]> {
    return this.http.get<DireccionResponse[]>(this.baseUrl, { withCredentials: true });
  }

  agregarDireccion(request: DireccionRequest): Observable<DireccionResponse> {
    return this.http.get<RespuestaTokenCsrf>(this.csrfUrl, { withCredentials: true }).pipe(
      switchMap(csrf => this.http.post<DireccionResponse>(this.baseUrl, request, {
        withCredentials: true,
        headers: new HttpHeaders({ 'X-XSRF-TOKEN': csrf.token })
      }))
    );
  }

  modificarDireccion(id: string, request: DireccionRequest): Observable<DireccionResponse> {
    return this.http.get<RespuestaTokenCsrf>(this.csrfUrl, { withCredentials: true }).pipe(
      switchMap(csrf => this.http.put<DireccionResponse>(`${this.baseUrl}/${id}`, request, {
        withCredentials: true,
        headers: new HttpHeaders({ 'X-XSRF-TOKEN': csrf.token })
      }))
    );
  }

  eliminarDireccion(id: string): Observable<void> {
    return this.http.get<RespuestaTokenCsrf>(this.csrfUrl, { withCredentials: true }).pipe(
      switchMap(csrf => this.http.delete<void>(`${this.baseUrl}/${id}`, {
        withCredentials: true,
        headers: new HttpHeaders({ 'X-XSRF-TOKEN': csrf.token })
      }))
    );
  }
}