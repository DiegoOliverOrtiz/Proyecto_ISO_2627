import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';

export interface Categoria {
  id: string;
  nombre: string;
  descripcion: string;
}

@Injectable({
  providedIn: 'root',
})
export class CategoriasService {
  private readonly http = inject(HttpClient);

  obtenerCategorias(): Observable<Categoria[]> {
    return this.http.get<Categoria[]>(
      `${environment.apiUrl}/categorias`,
      { withCredentials: true }
    );
  }

  obtenerCategoriaPorId(id: string): Observable<Categoria> {
    return this.http.get<Categoria>(
      `${environment.apiUrl}/categorias/${id}`,
      { withCredentials: true }
    );
  }
}