
import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface ClienteRegistrationRequest {
  nombre: string;
  apellidos: string;
  fechaNacimiento: string;
  dniNif: string;
  email: string;
  contrasena: string;
  telefono?: string;
  imagenPerfil?: string;
  esPremium: boolean;
}

export interface VendedorRegistrationRequest {
  nombre: string;
  apellidos: string;
  dniNif: string;
  email: string;
  contrasena: string;
  categoriaId: string;
  nombreComercial: string;
  telefono?: string;
  imagenPerfil?: string;
}

export interface CategoriaRegistro {
  id: string;
  nombre: string;
}

export interface RegistrationResponse {
  id: string;
  tipoUsuario: string;
}

@Injectable({ providedIn: 'root' })
export class RegistrationService {
  private readonly http = inject(HttpClient);

  obtenerCategoriasRegistro(): Observable<CategoriaRegistro[]> {
    return this.http.get<CategoriaRegistro[]>(
      `${environment.apiUrl}/categorias/registro`
    );
  }

  registerCliente(
    request: ClienteRegistrationRequest
  ): Observable<RegistrationResponse> {
    return this.http.post<RegistrationResponse>(
      `${environment.apiUrl}/cliente/register`,
      request
    );
  }

  registerVendedor(
    request: VendedorRegistrationRequest
  ): Observable<RegistrationResponse> {
    return this.http.post<RegistrationResponse>(
      `${environment.apiUrl}/vendedor/register`,
      request
    );
  }
}
