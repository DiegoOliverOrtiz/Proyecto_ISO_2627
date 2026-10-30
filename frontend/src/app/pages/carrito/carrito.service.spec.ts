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

  it('envía solo producto y cantidad con sesión y token CSRF y devuelve el carrito', () => {
    const esperado: CarritoResponse = {
      productos: [{ productoId: 'p1', cantidad: 3, precioUnitario: 8, subtotal: 24 }], importe: 24
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
