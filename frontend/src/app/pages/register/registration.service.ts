import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

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
  categoria: string;
  nombreComercial: string;
  telefono?: string;
  imagenPerfil?: string;
}

export interface RegistrationResponse {
  id: string;
  tipoUsuario: string;
}

const apiBaseUrl = globalThis.location.hostname === 'localhost' || globalThis.location.hostname === '127.0.0.1'
  ? 'http://localhost:8080'
  : globalThis.location.origin;

@Injectable({ providedIn: 'root' })
export class RegistrationService {
  private readonly http = inject(HttpClient);

  registerCliente(request: ClienteRegistrationRequest): Observable<RegistrationResponse> {
    return this.http.post<RegistrationResponse>(`${apiBaseUrl}/cliente/register`, request);
  }

  registerVendedor(request: VendedorRegistrationRequest): Observable<RegistrationResponse> {
    return this.http.post<RegistrationResponse>(`${apiBaseUrl}/vendedor/register`, request);
  }
}