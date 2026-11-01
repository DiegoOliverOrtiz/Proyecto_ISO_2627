import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, switchMap } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface TipoPago {
  codigo: string;
  nombre: string;
}

export interface MetodoPagoRequest {
  tipo: string;
  nombre: string;
  numeroTarjeta?: string | null;
  titular?: string | null;
  caducidad?: string | null;
  cvv?: string | null;
  correoPaypal?: string | null;
  telefonoBizum?: string | null;
}

export interface MetodoPagoResponse {
  id: string;
  tipo: string;
  nombre: string;
  datosEnmascarados: string;
  titular?: string | null;
  caducidad?: string | null;
}

interface RespuestaTokenCsrf {
  token: string;
}

@Injectable({ providedIn: 'root' })
export class PagosService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/cliente/metodos-pago`;
  private readonly csrfUrl = `${environment.apiUrl}/usuarios/csrf`;

  consultarTipos(): Observable<TipoPago[]> {
    return this.http.get<TipoPago[]>(`${this.baseUrl}/tipos`, { withCredentials: true });
  }

  // Todavía no está implementado el listado en backend, pero se prepararía así para futuras HUs (o devolvemos vacío)
  consultarMetodos(): Observable<MetodoPagoResponse[]> {
    return this.http.get<MetodoPagoResponse[]>(this.baseUrl, { withCredentials: true });
  }

  registrarMetodo(request: MetodoPagoRequest): Observable<MetodoPagoResponse> {
    return this.http.get<RespuestaTokenCsrf>(this.csrfUrl, { withCredentials: true }).pipe(
      switchMap(csrf => this.http.post<MetodoPagoResponse>(this.baseUrl, request, {
        withCredentials: true,
        headers: new HttpHeaders({ 'X-XSRF-TOKEN': csrf.token })
      }))
    );
  }
  
  // HU-36 Eliminar (por si lo necesitas ya)
  eliminarMetodo(id: string): Observable<void> {
    return this.http.get<RespuestaTokenCsrf>(this.csrfUrl, { withCredentials: true }).pipe(
      switchMap(csrf => this.http.delete<void>(`${this.baseUrl}/${id}`, {
        withCredentials: true,
        headers: new HttpHeaders({ 'X-XSRF-TOKEN': csrf.token })
      }))
    );
  }
}