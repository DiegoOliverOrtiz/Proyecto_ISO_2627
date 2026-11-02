import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { Observable } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { environment } from '../../../environments/environment';
import { Checkout } from './checkout';
import type { LineaCarritoResponse } from '../carrito/carrito.service';
import { CheckoutService, CrearPedidoRequest } from './checkout.service';
import { rolGuard } from '../login/rol.guard';

describe('Checkout - HU-26', () => {
  let fixture: ComponentFixture<Checkout>;
  let http: HttpTestingController;
  const api = environment.apiUrl;
  const request: CrearPedidoRequest = {
    direccionId: 'd1', metodoPagoId: 'm1', operacionId: '0746e76d-70d6-49a8-b972-f08b359474e1'
  };
  const direccion = { id: 'd1', nombre: 'Casa', calle: 'Calle A', numero: '1', ciudad: 'Ciudad Real', codigoPostal: '13001', provincia: 'Ciudad Real' };
  const metodo = { id: 'm1', nombre: 'Mi tarjeta', tipo: 'TARJETA', datosEnmascarados: '**** **** **** 4242' };
  const linea: LineaCarritoResponse = { productoId: 'p1', nombre: 'Producto A', imagen: null, cantidad: 2, precioUnitario: 90, subtotal: 180, stock: 10, disponible: true, aviso: null };

  function crear(): void {
    fixture = TestBed.createComponent(Checkout);
    fixture.detectChanges();
  }
  function cargar(productos = [linea], direcciones = [direccion], metodos = [metodo]): void {
    http.expectOne(`${api}/usuarios/sesion-actual`).flush({ email: 'a@example.com', role: 'CLIENTE' });
    http.expectOne(`${api}/cliente/carrito`).flush({ productos, importe: productos.length ? 180 : 0 });
    http.expectOne(`${api}/cliente/direcciones`).flush(direcciones);
    const pagos = http.expectOne(`${api}/cliente/metodos-pago`);
    expect(pagos.request.withCredentials).toBe(true);
    pagos.flush(metodos);
    fixture.detectChanges();
  }
  function seleccionar(): void {
    fixture.componentInstance.direccionId.set('d1');
    fixture.componentInstance.metodoPagoId.set('m1');
    fixture.detectChanges();
  }
  function obtenerPago() {
    http.expectOne(`${api}/usuarios/csrf`).flush({ token: 'csrf' });
    return http.expectOne(`${api}/cliente/pedidos`);
  }

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ imports: [Checkout],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => {
    http.verify();
    fixture?.destroy();
    vi.restoreAllMocks();
    sessionStorage.clear();
    TestBed.resetTestingModule();
  });

  it('muestra carga y resumen con selectores propios y referencia enmascarada', () => {
    crear();
    expect(fixture.nativeElement.textContent).toContain('Cargando compra...');
    cargar();
    expect(fixture.nativeElement.textContent).toContain('Producto A');
    expect(fixture.nativeElement.textContent).toContain('180.00 €');
    expect(fixture.nativeElement.textContent).toContain('**** **** **** 4242');
    expect(fixture.componentInstance.puedePagar()).toBe(false);
    seleccionar();
    expect(fixture.componentInstance.puedePagar()).toBe(true);
  });

  it('envía un pago, bloquea pulsaciones repetidas y muestra el resultado del servidor', () => {
    crear(); cargar(); seleccionar();
    fixture.componentInstance.pagar();
    fixture.componentInstance.pagar();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Procesando pago...');
    const pago = obtenerPago();
    expect(Object.keys(pago.request.body).sort()).toEqual(['direccionId', 'metodoPagoId', 'operacionId']);
    expect(TestBed.inject(CheckoutService).obtenerOperacion('a@example.com')).toEqual(pago.request.body);
    pago.flush({ id: 'pedido-1', estado: 'PAGADO_COMPLETADO', importe: 150, direccionEnvio: direccion,
      lineas: [{ productoId: 'p1', nombreProducto: 'Producto A', precioCompra: 75, cantidad: 2 }] });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Compra completada');
    expect(fixture.nativeElement.textContent).toContain('Importe pagado:');
    expect(fixture.nativeElement.textContent).toContain('150.00');
    expect(fixture.nativeElement.textContent).toContain('pedido-1');
    expect(TestBed.inject(CheckoutService).obtenerOperacion('a@example.com')).toBeNull();
    fixture.componentInstance.pagar();
    http.expectNone(`${api}/usuarios/csrf`);
  });

  it('conserva una operación pendiente si falla CSRF con 400 antes del reintento', () => {
    TestBed.inject(CheckoutService).guardarOperacion('a@example.com', request);
    crear(); cargar([]);
    fixture.componentInstance.pagar();
    http.expectOne(`${api}/usuarios/csrf`).flush({}, { status: 400, statusText: 'Bad Request' });
    expect(fixture.componentInstance.pendiente()).toEqual(request);
    expect(TestBed.inject(CheckoutService).obtenerOperacion('a@example.com')).toEqual(request);
    expect(fixture.componentInstance.procesando()).toBe(false);
    http.expectNone(`${api}/cliente/pedidos`);
    http.expectNone(`${api}/usuarios/sesion-actual`);
  });

  it('rechaza carrito vacío y no permite pagar', () => {
    crear(); cargar([]);
    seleccionar(); fixture.componentInstance.pagar();
    expect(fixture.nativeElement.textContent).toContain('Tu carrito está vacío');
    http.expectNone(`${api}/usuarios/csrf`);
  });

  it('bloquea productos no disponibles aunque el importe sea positivo', () => {
    crear(); cargar([{ ...linea, disponible: false, aviso: 'No hay stock suficiente.' }]);
    seleccionar(); fixture.componentInstance.pagar();
    expect(fixture.componentInstance.puedePagar()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('No hay stock suficiente.');
    http.expectNone(`${api}/usuarios/csrf`);
  });

  it('ofrece gestionar direcciones y métodos si faltan', () => {
    crear(); cargar([linea], [], []);
    seleccionar(); fixture.componentInstance.pagar();
    expect(fixture.nativeElement.textContent).toContain('No tienes direcciones registradas.');
    expect(fixture.nativeElement.textContent).toContain('No tienes métodos de pago registrados.');
    expect(fixture.nativeElement.querySelector('a[href="/perfil/pagos"]')).not.toBeNull();
    http.expectNone(`${api}/usuarios/csrf`);
  });

  it.each([0, 409, 503, 500])('conserva la selección y la clave al fallar con %s', status => {
    crear(); cargar(); seleccionar(); fixture.componentInstance.pagar();
    const primero = obtenerPago();
    const enviado = primero.request.body;
    primero.flush({}, { status, statusText: 'Error' });
    fixture.detectChanges();
    fixture.componentInstance.direccionId.set('otra');
    fixture.componentInstance.metodoPagoId.set('otro');
    fixture.componentInstance.pagar();
    const segundo = obtenerPago();
    expect(segundo.request.body).toEqual(enviado);
    segundo.flush({}, { status: 503, statusText: 'Unavailable' });
    expect(fixture.componentInstance.pendiente()).toEqual(enviado);
  });

  it('recupera la operación tras recargar aunque el carrito ya esté vacío', () => {
    TestBed.inject(CheckoutService).guardarOperacion('a@example.com', request);
    crear(); cargar([]);
    expect(fixture.nativeElement.textContent).toContain('Comprueba tu compra pendiente');
    fixture.componentInstance.pagar();
    const pago = obtenerPago();
    expect(pago.request.body).toEqual(request);
    pago.flush({ id: 'ya-pagado', estado: 'PAGADO_COMPLETADO', importe: 180, lineas: [], direccionEnvio: direccion });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('ya-pagado');
  });

  it.each(['No hay stock suficiente para la cantidad solicitada.', 'El producto ya no está disponible.',
    'La dirección no pertenece al cliente o no existe.', 'El método de pago no pertenece al cliente o no existe.'])
  ('muestra rechazo y vuelve a cargar datos: %s', mensaje => {
    crear(); cargar(); seleccionar(); fixture.componentInstance.pagar();
    obtenerPago().flush({ error: mensaje }, { status: 400, statusText: 'Bad Request' });
    cargar();
    expect(fixture.nativeElement.textContent).toContain(mensaje);
    expect(fixture.componentInstance.pendiente()).toBeNull();
  });

  it('no paga si no puede conservar el intento en el navegador', () => {
    crear(); cargar(); seleccionar();
    vi.spyOn(TestBed.inject(CheckoutService), 'guardarOperacion').mockImplementation(() => { throw new Error('Storage'); });
    fixture.componentInstance.pagar();
    expect(fixture.componentInstance.error()).toContain('No se pudo preparar');
    http.expectNone(`${api}/usuarios/csrf`);
  });

  it('no utiliza la operación pendiente de otro usuario', () => {
    TestBed.inject(CheckoutService).guardarOperacion('b@example.com', request);
    crear(); cargar([]);
    expect(fixture.componentInstance.pendiente()).toBeNull();
    expect(fixture.componentInstance.puedePagar()).toBe(false);
  });

  it.each(['CLIENTE', 'VENDEDOR', 'ADMIN'])('el guard de compra controla el acceso de %s', role => {
    let resultado: boolean | UrlTree | undefined;
    const decision = TestBed.runInInjectionContext(() => rolGuard(
      { data: { rol: 'CLIENTE' } } as unknown as ActivatedRouteSnapshot, {} as RouterStateSnapshot
    )) as Observable<boolean | UrlTree>;
    decision.subscribe(valor => resultado = valor);
    http.expectOne(`${api}/usuarios/sesion-actual`).flush({ email: 'a@example.com', role });
    if (role === 'CLIENTE') expect(resultado).toBe(true);
    else expect(TestBed.inject(Router).serializeUrl(resultado as UrlTree)).toBe(role === 'ADMIN' ? '/admin' : '/vendedor');
  });

  it('permite seleccionar y pagar desde los controles del formulario', async () => {
    crear(); cargar();
    await fixture.whenStable();
    const direccionSelect: HTMLSelectElement = fixture.nativeElement.querySelector('#direccion-envio');
    const metodoSelect: HTMLSelectElement = fixture.nativeElement.querySelector('#metodo-pago');
    direccionSelect.value = 'd1';
    direccionSelect.dispatchEvent(new Event('change'));
    metodoSelect.value = 'm1';
    metodoSelect.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.pay-button').disabled).toBe(false);
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    const pago = obtenerPago();
    expect(pago.request.body.direccionId).toBe('d1');
    expect(pago.request.body.metodoPagoId).toBe('m1');
    pago.flush({ id: 'pedido', estado: 'PAGADO_COMPLETADO', lineas: [], importe: 180, direccionEnvio: direccion });
  });

  it('permite recargar cuando falla la consulta de métodos', () => {
    crear();
    http.expectOne(`${api}/usuarios/sesion-actual`).flush({ email: 'a@example.com', role: 'CLIENTE' });
    http.expectOne(`${api}/cliente/carrito`).flush({ productos: [linea], importe: 180 });
    http.expectOne(`${api}/cliente/direcciones`).flush([direccion]);
    http.expectOne(`${api}/cliente/metodos-pago`).flush({}, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(fixture.componentInstance.error()).toContain('No se pudieron cargar');
    expect(fixture.componentInstance.puedePagar()).toBe(false);
    fixture.componentInstance.cargar();
    cargar();
    expect(fixture.componentInstance.carrito()?.productos.length).toBe(1);
  });

  it('conserva el intento al caducar la sesión y dirige al login', () => {
    crear(); cargar(); seleccionar();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.componentInstance.pagar();
    const pago = obtenerPago();
    const original = pago.request.body;
    pago.flush({}, { status: 403, statusText: 'Forbidden' });
    http.expectOne(`${api}/usuarios/sesion-actual`).flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(navegar).toHaveBeenCalledWith(['/login'], { queryParams: { motivo: 'sesion-expirada' } });
    expect(TestBed.inject(CheckoutService).obtenerOperacion('a@example.com')).toEqual(original);
  });

  it('no sustituye una operación guardada corrupta por otra compra', () => {
    sessionStorage.setItem('esibuy.checkout.a@example.com', '{');
    crear();
    http.expectOne(`${api}/usuarios/sesion-actual`).flush({ email: 'a@example.com', role: 'CLIENTE' });
    expect(fixture.componentInstance.puedePagar()).toBe(false);
    expect(fixture.componentInstance.error()).toContain('recuperar la compra pendiente');
    http.expectNone(`${api}/cliente/carrito`);
    http.expectNone(`${api}/usuarios/csrf`);
  });

  it('conserva el intento si se abandona la pantalla durante el pago', () => {
    crear(); cargar(); seleccionar(); fixture.componentInstance.pagar();
    const pago = obtenerPago();
    const original = pago.request.body;
    fixture.destroy();
    expect(pago.cancelled).toBe(true);
    expect(TestBed.inject(CheckoutService).obtenerOperacion('a@example.com')).toEqual(original);
    crear(); cargar([]);
    expect(fixture.componentInstance.pendiente()).toEqual(original);
  });
});
