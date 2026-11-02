import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { environment } from '../../../environments/environment';
import { CheckoutService, CrearPedidoRequest } from './checkout.service';

describe('CheckoutService - HU-26', () => {
  let servicio: CheckoutService;
  let http: HttpTestingController;
  const request: CrearPedidoRequest = {
    direccionId: 'd1', metodoPagoId: 'm1', operacionId: '0746e76d-70d6-49a8-b972-f08b359474e1'
  };

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(CheckoutService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => {
    http.verify();
    sessionStorage.clear();
    TestBed.resetTestingModule();
  });

  it('envía solo selecciones y clave con sesión y CSRF', () => {
    servicio.pagar(request).subscribe();
    const csrf = http.expectOne(`${environment.apiUrl}/usuarios/csrf`);
    expect(csrf.request.withCredentials).toBe(true);
    csrf.flush({ token: 'csrf-compra' });
    const pago = http.expectOne(`${environment.apiUrl}/cliente/pedidos`);
    expect(pago.request.method).toBe('POST');
    expect(pago.request.body).toEqual(request);
    expect(pago.request.withCredentials).toBe(true);
    expect(pago.request.headers.get('X-XSRF-TOKEN')).toBe('csrf-compra');
    pago.flush({ id: 'pedido' });
  });

  it('no envía el pedido si falla la obtención de CSRF', () => {
    servicio.pagar(request).subscribe({ error: () => {} });
    http.expectOne(`${environment.apiUrl}/usuarios/csrf`).flush({}, { status: 403, statusText: 'Forbidden' });
    http.expectNone(`${environment.apiUrl}/cliente/pedidos`);
  });

  it('descarta campos adicionales aunque el objeto se manipule en ejecución', () => {
    servicio.pagar({ ...request, total: 1, precio: 1, clienteId: 'otro' } as CrearPedidoRequest).subscribe();
    http.expectOne(`${environment.apiUrl}/usuarios/csrf`).flush({ token: 'csrf' });
    const pago = http.expectOne(`${environment.apiUrl}/cliente/pedidos`);
    expect(pago.request.body).toEqual(request);
    pago.flush({ id: 'pedido' });
  });

  it('conserva operaciones por usuario y permite recuperarlas', () => {
    servicio.guardarOperacion('A@example.com', request);
    expect(TestBed.inject(CheckoutService).obtenerOperacion('a@example.com')).toEqual(request);
    expect(servicio.obtenerOperacion('b@example.com')).toBeNull();
    servicio.limpiarOperacion('a@example.com');
    expect(servicio.obtenerOperacion('a@example.com')).toBeNull();
  });

  it('no recupera campos añadidos ni sustituye una operación corrupta por una compra nueva', () => {
    sessionStorage.setItem('esibuy.checkout.a@example.com', JSON.stringify({ ...request, total: 1, clienteId: 'b' }));
    expect(servicio.obtenerOperacion('a@example.com')).toEqual(request);
    sessionStorage.setItem('esibuy.checkout.a@example.com', '{');
    expect(() => servicio.obtenerOperacion('a@example.com')).toThrow();
  });
});
