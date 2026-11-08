import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { environment } from '../../../environments/environment';
import { CarritoResponse, CarritoService } from './carrito.service';

describe('CarritoService - HU-20', () => {
  let servicio: CarritoService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(CarritoService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
  });

  it('consulta el carrito con credenciales sin enviar propietario', () => {
    servicio.consultarCarrito().subscribe();
    const peticion = http.expectOne(`${environment.apiUrl}/cliente/carrito`);
    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.withCredentials).toBe(true);
    expect(peticion.request.params.keys()).toEqual([]);
    peticion.flush({ productos: [], importe: 0 });
  });

  it('recupera la imagen desde el producto cuando la línea antigua no la incluye', () => {
    let resultado: CarritoResponse | undefined;
    servicio.consultarCarrito().subscribe(respuesta => resultado = respuesta);
    http.expectOne(`${environment.apiUrl}/cliente/carrito`).flush({
      productos: [{ productoId: 'p1', cantidad: 1, precioUnitario: 20, subtotal: 20,
        nombre: 'Producto A', stock: 4, disponible: true, aviso: null }], importe: 20
    });
    const detalle = http.expectOne(`${environment.apiUrl}/cliente/productos/p1`);
    expect(detalle.request.withCredentials).toBe(true);
    detalle.flush({ imagen: '/product-images/producto-a.jpg' });
    expect(resultado?.productos[0].imagen).toBe('/product-images/producto-a.jpg');
  });

  it.each([
    ['cantidad', 'PUT', '/productos/p%2F1'],
    ['eliminar', 'DELETE', '/productos/p%2F1'],
    ['descartar', 'DELETE', '']
  ])('envía %s con CSRF, sesión y ruta codificada', (accion, metodo, ruta) => {
    const peticion = accion === 'cantidad' ? servicio.modificarCantidad('p/1', 5)
      : accion === 'eliminar' ? servicio.eliminarProducto('p/1') : servicio.descartarCarrito();
    peticion.subscribe();
    const csrf = http.expectOne(`${environment.apiUrl}/usuarios/csrf`);
    expect(csrf.request.withCredentials).toBe(true);
    csrf.flush({ token: 'token-carrito' });
    const mutacion = http.expectOne(`${environment.apiUrl}/cliente/carrito${ruta}`);
    expect(mutacion.request.method).toBe(metodo);
    expect(mutacion.request.withCredentials).toBe(true);
    expect(mutacion.request.headers.get('X-XSRF-TOKEN')).toBe('token-carrito');
    if (accion === 'cantidad') expect(mutacion.request.body).toEqual({ cantidad: 5 });
    else expect(mutacion.request.body).toBeNull();
    mutacion.flush({ productos: [], importe: 0 });
  });

  it('no descarta si falla el token CSRF', () => {
    let codigoError: number | undefined;
    servicio.descartarCarrito().subscribe({ error: error => codigoError = error.status });
    http.expectOne(`${environment.apiUrl}/usuarios/csrf`).flush({}, { status: 403, statusText: 'Forbidden' });
    http.expectNone(`${environment.apiUrl}/cliente/carrito`);
    expect(codigoError).toBe(403);
  });

  it('envía solo producto y cantidad con sesión y token CSRF y devuelve el carrito', () => {
    const esperado: CarritoResponse = {
      productos: [{ productoId: 'p1', cantidad: 3, precioUnitario: 8, subtotal: 24,
        nombre: 'Producto A', imagen: null, stock: 10, disponible: true, aviso: null }], importe: 24
    };
    let resultado: CarritoResponse | undefined;
    servicio.anadirProducto('p1', 3).subscribe(respuesta => resultado = respuesta);
    const csrf = http.expectOne(`${environment.apiUrl}/usuarios/csrf`);
    expect(csrf.request.withCredentials).toBe(true);
    csrf.flush({ token: 'csrf-carrito' });
    const peticion = http.expectOne(`${environment.apiUrl}/cliente/carrito/productos`);
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual({ productoId: 'p1', cantidad: 3 });
    expect(peticion.request.withCredentials).toBe(true);
    expect(peticion.request.headers.get('X-XSRF-TOKEN')).toBe('csrf-carrito');
    peticion.flush(esperado);
    expect(resultado).toEqual(esperado);
  });

  it('no envía el alta si falla la obtención del token', () => {
    let error: HttpErrorResponse | undefined;
    servicio.anadirProducto('p1', 1).subscribe({ error: fallo => error = fallo });
    http.expectOne(`${environment.apiUrl}/usuarios/csrf`)
      .flush({}, { status: 403, statusText: 'Forbidden' });
    http.expectNone(`${environment.apiUrl}/cliente/carrito/productos`);
    expect(error?.status).toBe(403);
  });
});
