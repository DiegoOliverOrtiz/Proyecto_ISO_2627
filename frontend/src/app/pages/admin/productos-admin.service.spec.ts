import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { environment } from '../../../environments/environment';
import { ProductoAdministracion, ProductosAdminService } from './productos-admin.service';

describe('ProductosAdminService (HU-34)', () => {
  let servicio: ProductosAdminService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    servicio = TestBed.inject(ProductosAdminService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    TestBed.resetTestingModule();
  });

  it('consulta con GET y credenciales los productos de todos los vendedores', () => {
    const productos: ProductoAdministracion[] = [
      {
        id: 'p1',
        nombre: 'Teclado',
        categoria: 'Informática',
        stock: 3,
        descripcion: null,
        precio: 49.99,
        descuento: 0,
        descuentoPremium: 0,
        esVisible: false,
        imagen: null,
        soloPremium: false,
        vendedor: 'Tienda A'
      }
    ];
    let respuesta: ProductoAdministracion[] | undefined;

    servicio.consultarProductos().subscribe((datos) => (respuesta = datos));

    const peticion = httpMock.expectOne(`${environment.apiUrl}/administrador/productos`);
    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.withCredentials).toBe(true);
    peticion.flush(productos);

    expect(respuesta).toEqual(productos);
  });

  it('no ofrece métodos de escritura', () => {
    const metodos = Object.getOwnPropertyNames(Object.getPrototypeOf(servicio)).filter(
      (nombre) => nombre !== 'constructor'
    );

    expect(metodos).toEqual(['consultarProductos']);
  });
});
