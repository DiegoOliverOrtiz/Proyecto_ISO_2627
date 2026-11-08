import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { environment } from '../../../environments/environment';
import {
  ModificarProductoRequest,
  ProductoPropio,
  ProductoService,
  ValoracionesProducto
} from './producto.service';

describe('ProductoService - modificar producto', () => {
  let servicio: ProductoService;
  let httpMock: HttpTestingController;

  const cambios: ModificarProductoRequest = {
    nombre: 'Teclado mecánico',
    categoria: 'Periféricos',
    descripcion: 'Retroiluminado',
    precio: 59.99,
    stock: 10,
    descuento: 15,
    descuentoPremium: 20,
    soloPremium: true
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    servicio = TestBed.inject(ProductoService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    TestBed.resetTestingModule();
  });

  it('envía un PUT multipart con el token CSRF y devuelve el producto actualizado', async () => {
    const imagen = new File(['imagen'], 'teclado.png', { type: 'image/png' });
    let respuesta: ProductoPropio | undefined;

    servicio.modificarProducto('producto 1', cambios, imagen).subscribe((producto) => {
      respuesta = producto;
    });

    httpMock.expectOne(`${environment.apiUrl}/usuarios/csrf`).flush({ token: 'token-csrf' });

    const peticion = httpMock.expectOne(`${environment.apiUrl}/vendedor/productos/producto%201`);
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.withCredentials).toBe(true);
    expect(peticion.request.headers.get('X-XSRF-TOKEN')).toBe('token-csrf');

    const datos = peticion.request.body as FormData;
    const parteProducto = datos.get('producto') as Blob;
    expect(JSON.parse(await parteProducto.text())).toEqual(cambios);
    expect(datos.get('imagen')).toBe(imagen);

    const productoActualizado = { ...cambios, id: 'producto 1', esVisible: true, imagen: null };
    peticion.flush(productoActualizado);
    expect(respuesta).toEqual(productoActualizado);
  });

  it('no envía la parte de imagen si no se selecciona una nueva', () => {
    servicio.modificarProducto('producto-1', cambios, null).subscribe();

    httpMock.expectOne(`${environment.apiUrl}/usuarios/csrf`).flush({ token: 'token-csrf' });
    const peticion = httpMock.expectOne(`${environment.apiUrl}/vendedor/productos/producto-1`);

    expect((peticion.request.body as FormData).has('imagen')).toBe(false);
    peticion.flush({});
  });
});

describe('ProductoService - eliminar producto (HU-33)', () => {
  let servicio: ProductoService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    servicio = TestBed.inject(ProductoService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    TestBed.resetTestingModule();
  });

  it('pide el token CSRF y envía un DELETE con credenciales', () => {
    let completado = false;

    servicio.eliminarProducto('producto 1').subscribe({ complete: () => (completado = true) });

    httpMock.expectOne(`${environment.apiUrl}/usuarios/csrf`).flush({ token: 'token-csrf' });
    const peticion = httpMock.expectOne(`${environment.apiUrl}/vendedor/productos/producto%201`);
    expect(peticion.request.method).toBe('DELETE');
    expect(peticion.request.withCredentials).toBe(true);
    expect(peticion.request.headers.get('X-XSRF-TOKEN')).toBe('token-csrf');

    peticion.flush(null, { status: 204, statusText: 'No Content' });
    expect(completado).toBe(true);
  });

  it('propaga el 409 con el motivo devuelto por el backend', () => {
    let estado = 0;

    servicio.eliminarProducto('producto-1').subscribe({ error: (error) => (estado = error.status) });

    httpMock.expectOne(`${environment.apiUrl}/usuarios/csrf`).flush({ token: 'token-csrf' });
    httpMock
      .expectOne(`${environment.apiUrl}/vendedor/productos/producto-1`)
      .flush({ error: 'Pedidos en curso' }, { status: 409, statusText: 'Conflict' });

    expect(estado).toBe(409);
  });
});

describe('ProductoService - valoraciones de productos', () => {
  let servicio: ProductoService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    servicio = TestBed.inject(ProductoService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    TestBed.resetTestingModule();
  });

  it('consulta las valoraciones anónimas del producto', () => {
    const respuesta: ValoracionesProducto = {
      media: 4.5,
      total: 2,
      valoraciones: [
        { puntuacion: 5, comentario: 'Excelente' },
        { puntuacion: 4, comentario: 'Muy bueno' }
      ]
    };
    let resultado: typeof respuesta | undefined;

    servicio.consultarValoraciones('producto/1').subscribe((valoraciones) => {
      resultado = valoraciones;
    });

    const peticion = httpMock.expectOne(
      `${environment.apiUrl}/cliente/productos/producto%2F1/valoraciones`
    );
    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.withCredentials).toBe(true);
    peticion.flush(respuesta);

    expect(resultado).toEqual(respuesta);
  });
});
