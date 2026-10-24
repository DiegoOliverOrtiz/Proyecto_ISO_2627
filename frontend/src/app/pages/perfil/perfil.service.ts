import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';

export type TipoUsuario = 'ADMIN' | 'CLIENTE' | 'VENDEDOR';

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
  sede: string | null;
  fechaIncorporacion: string | null;
  rol: string | null;
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
}
