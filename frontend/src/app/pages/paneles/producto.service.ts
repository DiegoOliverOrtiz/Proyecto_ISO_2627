import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface ProductoPropio {
  nombre: string;
  categoria: string;
  stock: number;
  descripcion: string | null;
  precio: number | null;
  descuento: number;
  descuentoPremium: number;
  esVisible: boolean;
}

@Injectable({ providedIn: 'root' })
export class ProductoService {
  private readonly http = inject(HttpClient);

  consultarProductosPropios(): Observable<ProductoPropio[]> {
    return this.http.get<ProductoPropio[]>(
      `${environment.apiUrl}/vendedor/productos`,
      { withCredentials: true }
    );
  }
}
