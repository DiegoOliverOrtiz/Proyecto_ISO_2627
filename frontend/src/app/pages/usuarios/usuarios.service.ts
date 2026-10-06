import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface UsuarioAdministracion {
  id: string;
  nombre: string;
  apellidos: string;
  email: string;
  telefono: string | null;
  estado: string;
  rol: string;
  imagenPerfil: string | null;
  sede: string | null;
  fechaIncorporacion: string | null;
  esPremium: boolean | null;
  nombreComercial: string | null;
}

export interface ListadoUsuarios {
  contenido: UsuarioAdministracion[];
  pagina: number;
  tamano: number;
  totalElementos: number;
  totalPaginas: number;
  estadisticas: EstadisticasUsuarios;
}

export interface EstadisticasUsuarios {
  totalUsuarios: number;
  activos: number;
  pendientes: number;
  desactivados: number;
}

@Injectable({ providedIn: 'root' })
export class UsuariosService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/administrador/usuarios`;

  consultarUsuarios(
    busqueda: string,
    estado: string,
    rol: string,
    pagina: number,
    tamano: number
  ): Observable<ListadoUsuarios> {
    let parametros = new HttpParams()
      .set('page', pagina)
      .set('size', tamano)
      .set('sort', 'apellidos,asc')
      .append('sort', 'nombre,asc');

    if (busqueda.trim()) parametros = parametros.set('busqueda', busqueda.trim());
    if (estado) parametros = parametros.set('estado', estado);
    if (rol) parametros = parametros.set('rol', rol);

    return this.http.get<ListadoUsuarios>(this.url, {
      params: parametros,
      withCredentials: true
    });
  }

  consultarUsuario(id: string): Observable<UsuarioAdministracion> {
    return this.http.get<UsuarioAdministracion>(`${this.url}/${encodeURIComponent(id)}`, {
      withCredentials: true
    });
  }
}