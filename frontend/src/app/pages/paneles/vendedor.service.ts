import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface PerfilVendedor {
  nombreComercial: string;
}

@Injectable({ providedIn: 'root' })
export class VendedorService {
  private readonly http = inject(HttpClient);

  obtenerPerfil(): Observable<PerfilVendedor> {
    return this.http.get<PerfilVendedor>(
      `${environment.apiUrl}/vendedor/perfil`,
      { withCredentials: true }
    );
  }
}