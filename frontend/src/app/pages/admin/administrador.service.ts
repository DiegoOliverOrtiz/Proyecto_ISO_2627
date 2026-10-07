import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
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

@Injectable({ providedIn: 'root' })
export class AdministradorService {
  private readonly http = inject(HttpClient);

  crearAdministrador(request: RegistroAdministradorRequest): Observable<RegistroUsuarioResponse> {
    return this.http.post<RegistroUsuarioResponse>(
      `${environment.apiUrl}/administrador/register`,
      request,
      { withCredentials: true }
    );
  }
}