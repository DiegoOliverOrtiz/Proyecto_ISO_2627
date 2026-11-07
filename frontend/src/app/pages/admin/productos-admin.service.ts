import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import type { ProductoPropio } from '../paneles/producto.service';

// HU-34: lo mismo que ve el vendedor de sus productos, más el vendedor asociado.
export interface ProductoAdministracion extends ProductoPropio {
  vendedor: string | null;
}

// Solo lectura: este servicio no tiene ningún método de escritura.
@Injectable({ providedIn: 'root' })
export class ProductosAdminService {
  private readonly http = inject(HttpClient);
  private readonly urlProductos = `${environment.apiUrl}/administrador/productos`;

  consultarProductos(): Observable<ProductoAdministracion[]> {
    return this.http.get<ProductoAdministracion[]>(this.urlProductos, { withCredentials: true });
  }
}
