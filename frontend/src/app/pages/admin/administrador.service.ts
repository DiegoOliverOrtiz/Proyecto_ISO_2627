import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, switchMap } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface RegistroAdministradorRequest {
  nombre: string;
  apellidos: string;
  email: string;
  contrasena: string;
  sede?: string;
  fechaIncorporacion?: string;
  imagenPerfil?: string;
}

export interface RegistroUsuarioResponse {
  id: string;
  tipoUsuario: string;
}

interface RespuestaTokenCsrf {
  token: string;
}

@Injectable({ providedIn: 'root' })
export class AdministradorService {
  private readonly http = inject(HttpClient);

  crearAdministrador(request: RegistroAdministradorRequest): Observable<RegistroUsuarioResponse> {
    // Obtenemos primero el token CSRF, igual que en el AuthService, para evitar el error 403 Forbidden
    return this.http
      .get<RespuestaTokenCsrf>(`${environment.apiUrl}/usuarios/csrf`, {
        withCredentials: true
      })
      .pipe(
        switchMap((respuestaCsrf) =>
          this.http.post<RegistroUsuarioResponse>(
            `${environment.apiUrl}/administrador/register`,
            request,
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