import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { environment } from '../../../environments/environment';
import { Carrito } from './carrito';
import { CarritoResponse, LineaCarritoResponse } from './carrito.service';

describe('Carrito - HU-21', () => {
  let fixture: ComponentFixture<Carrito>;
  let http: HttpTestingController;
  const url = `${environment.apiUrl}/cliente/carrito`;
  const linea = (cambios: Partial<LineaCarritoResponse> = {}): LineaCarritoResponse => ({
    productoId: 'p1', nombre: 'Producto A', cantidad: 2, precioUnitario: 8, subtotal: 16,
    stock: 10, disponible: true, aviso: null, ...cambios
  });
  const cargar = (productos = [linea()], importe = 16): void => {
    http.expectOne(url).flush({ productos, importe });
    fixture.detectChanges();
  };
  const responderMutacion = (ruta: string, respuesta: CarritoResponse): void => {
    http.expectOne(`${environment.apiUrl}/usuarios/csrf`).flush({ token: 'csrf' });
    http.expectOne(`${url}${ruta}`).flush(respuesta);
    fixture.detectChanges();
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Carrito], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
    });
    fixture = TestBed.createComponent(Carrito);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('muestra carga, productos, cantidades, importes y navegación', () => {
    expect(fixture.nativeElement.textContent).toContain('Cargando carrito...');
    cargar();
    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('Producto A');
    expect(texto).toContain('Cantidad en el carrito: 2');
    expect(texto).toContain('8.00');
    expect(texto).toContain('Total: 16.00');
    expect(fixture.nativeElement.querySelector('a').getAttribute('href')).toBe('/cliente');
  });

  it('muestra carrito vacío sin lista ni botón de descarte', () => {
    cargar([], 0);
    expect(fixture.nativeElement.textContent).toContain('Tu carrito está vacío');
    expect(fixture.nativeElement.querySelector('.cart-list')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Descartar carrito');
  });

  it('modifica desde el input y actualiza cantidades y total del backend', async () => {
    cargar();
    await fixture.whenStable();
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input');
    input.value = '5';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    input.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
    expect(fixture.componentInstance.operacion()).toBe('p1');
    expect(fixture.nativeElement.querySelector('.quantity-selector button').disabled).toBe(true);
    fixture.componentInstance.guardarCantidad(linea());
    http.expectOne(`${environment.apiUrl}/usuarios/csrf`).flush({ token: 'csrf' });
    const peticion = http.expectOne(`${url}/productos/p1`);
    expect(peticion.request.body).toEqual({ cantidad: 5 });
    peticion.flush({ productos: [linea({ cantidad: 5, subtotal: 40 })], importe: 40 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Total: 40.00');
    expect(fixture.componentInstance.cantidades()['p1']).toBe(5);
  });

  it.each([0, -1, 1.5, null, 11, Number.MAX_SAFE_INTEGER + 1])('rechaza cantidad inválida %s sin API', cantidad => {
    cargar();
    fixture.componentInstance.cambiarCantidad(linea(), cantidad);
    fixture.componentInstance.guardarCantidad(linea());
    expect(fixture.componentInstance.error()).not.toBe('');
    http.expectNone(`${environment.apiUrl}/usuarios/csrf`);
  });

  it('elimina una línea usando la respuesta recalculada', () => {
    cargar([linea(), linea({ productoId: 'p2', nombre: 'Producto B' })], 32);
    fixture.nativeElement.querySelector('.cart-line-actions .logout-button').click();
    responderMutacion('/productos/p1', { productos: [linea({ productoId: 'p2', nombre: 'Producto B' })], importe: 16 });
    expect(fixture.nativeElement.textContent).not.toContain('Producto A');
    expect(fixture.nativeElement.textContent).toContain('Producto B');
    expect(fixture.nativeElement.textContent).toContain('Total: 16.00');
  });

  it('no descarta cuando se cancela la confirmación', () => {
    cargar();
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    fixture.nativeElement.querySelector('.cart-summary button').click();
    expect(window.confirm).toHaveBeenCalled();
    http.expectNone(`${environment.apiUrl}/usuarios/csrf`);
    expect(fixture.componentInstance.carrito()?.productos).toHaveLength(1);
  });

  it('confirma el descarte y muestra estado vacío', () => {
    cargar();
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    fixture.nativeElement.querySelector('.cart-summary button').click();
    fixture.componentInstance.descartarCarrito();
    expect(window.confirm).toHaveBeenCalledTimes(1);
    responderMutacion('', { productos: [], importe: 0 });
    expect(fixture.nativeElement.textContent).toContain('Tu carrito está vacío');
    expect(fixture.nativeElement.textContent).toContain('Carrito descartado.');
  });

  it('conserva líneas no disponibles y permite eliminarlas', async () => {
    cargar([linea({ disponible: false, stock: null, precioUnitario: null, subtotal: null,
      aviso: 'El producto ha sido eliminado.' })], 0);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('El producto ha sido eliminado.');
    expect(fixture.nativeElement.textContent).toContain('El total incluye únicamente las líneas disponibles.');
    expect(fixture.nativeElement.querySelector('input').disabled).toBe(true);
    expect(fixture.nativeElement.querySelectorAll('.quantity-selector button')[1].disabled).toBe(true);
    expect(fixture.nativeElement.querySelector('.cart-line-actions .logout-button').disabled).toBe(false);
  });

  it('permite corregir cantidad cuando se reduce el stock', () => {
    const producto = linea({ disponible: false, cantidad: 5, stock: 3,
      aviso: 'No hay stock suficiente para la cantidad solicitada.', subtotal: null, precioUnitario: null });
    cargar([producto], 0);
    expect(fixture.nativeElement.querySelector('input').disabled).toBe(false);
    fixture.componentInstance.ajustarCantidad(producto, -1);
    expect(fixture.componentInstance.cantidades()['p1']).toBe(3);
    responderMutacion('/productos/p1', { productos: [linea({ cantidad: 3, stock: 3, subtotal: 24 })], importe: 24 });
    expect(fixture.nativeElement.textContent).toContain('Total: 24.00');
  });

  it('el botón más guarda directamente sin botón de confirmación', () => {
    cargar();
    fixture.nativeElement.querySelectorAll('.quantity-selector button')[1].click();
    http.expectOne(`${environment.apiUrl}/usuarios/csrf`).flush({ token: 'csrf' });
    const peticion = http.expectOne(`${url}/productos/p1`);
    expect(peticion.request.body).toEqual({ cantidad: 3 });
    peticion.flush({ productos: [linea({ cantidad: 3, subtotal: 24 })], importe: 24 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Total: 24.00');
    expect(fixture.nativeElement.textContent).not.toContain('Guardar cantidad');
  });

  it('guarda con Enter y no repite la petición al salir del campo tras el éxito', async () => {
    cargar();
    await fixture.whenStable();
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input');
    input.value = '4';
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    http.expectOne(`${environment.apiUrl}/usuarios/csrf`).flush({ token: 'csrf' });
    const peticion = http.expectOne(`${url}/productos/p1`);
    expect(peticion.request.body).toEqual({ cantidad: 4 });
    peticion.flush({ productos: [linea({ cantidad: 4, subtotal: 32 })], importe: 32 });
    fixture.detectChanges();
    await fixture.whenStable();
    input.dispatchEvent(new Event('blur'));
    http.expectNone(`${environment.apiUrl}/usuarios/csrf`);
    expect(fixture.nativeElement.textContent).toContain('Total: 32.00');
  });

  it('el botón menos guarda y queda bloqueado al llegar a una unidad', () => {
    cargar();
    fixture.nativeElement.querySelector('.quantity-selector button').click();
    http.expectOne(`${environment.apiUrl}/usuarios/csrf`).flush({ token: 'csrf' });
    const peticion = http.expectOne(`${url}/productos/p1`);
    expect(peticion.request.body).toEqual({ cantidad: 1 });
    peticion.flush({ productos: [linea({ cantidad: 1, subtotal: 8 })], importe: 8 });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.quantity-selector button').disabled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Total: 8.00');
  });

  it('eliminar la última línea muestra el estado vacío sin recargar manualmente', () => {
    cargar();
    fixture.nativeElement.querySelector('.cart-line-actions .logout-button').click();
    responderMutacion('/productos/p1', { productos: [], importe: 0 });
    expect(fixture.nativeElement.textContent).toContain('Tu carrito está vacío');
    expect(fixture.nativeElement.querySelector('.cart-list')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Actualizar carrito');
    http.expectNone(url);
  });

  it('muestra error de carga y permite reintentar', () => {
    http.expectOne(url).flush({}, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No se pudo cargar el carrito.');
    fixture.nativeElement.querySelector('main button').click();
    cargar();
    expect(fixture.componentInstance.error()).toBe('');
  });

  it('un 404 al consultar no se interpreta como un producto eliminado', () => {
    http.expectOne(url).flush({}, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    expect(fixture.componentInstance.error()).toBe('No se pudo cargar el carrito. Inténtalo de nuevo.');
    expect(fixture.nativeElement.textContent).not.toContain('El producto ya no está');
  });

  it.each([400, 404, 409, 500, 0])('conserva carrito ante error %s de modificación y oculta detalles internos', status => {
    cargar();
    fixture.componentInstance.cambiarCantidad(linea(), 3);
    fixture.componentInstance.guardarCantidad(linea());
    http.expectOne(`${environment.apiUrl}/usuarios/csrf`).flush({ token: 'csrf' });
    http.expectOne(`${url}/productos/p1`).flush({ error: 'detalle interno MongoDB' }, { status, statusText: 'Error' });
    fixture.detectChanges();
    expect(fixture.componentInstance.carrito()?.importe).toBe(16);
    expect(fixture.componentInstance.operacion()).toBeNull();
    expect(fixture.componentInstance.error()).not.toContain('MongoDB');
    expect(fixture.componentInstance.error()).not.toBe('');
  });

  it('muestra el error de stock devuelto por el backend', () => {
    cargar();
    fixture.componentInstance.cambiarCantidad(linea(), 3);
    fixture.componentInstance.guardarCantidad(linea());
    http.expectOne(`${environment.apiUrl}/usuarios/csrf`).flush({ token: 'csrf' });
    http.expectOne(`${url}/productos/p1`).flush({ error: 'No hay stock suficiente para la cantidad solicitada.' },
      { status: 400, statusText: 'Bad Request' });
    expect(fixture.componentInstance.error()).toContain('No hay stock suficiente');
  });

  it.each(['eliminar', 'descartar'])('muestra errores de %s sin perder las líneas', accion => {
    cargar();
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    if (accion === 'eliminar') fixture.componentInstance.eliminarProducto(linea());
    else fixture.componentInstance.descartarCarrito();
    http.expectOne(`${environment.apiUrl}/usuarios/csrf`).flush({ token: 'csrf' });
    http.expectOne(`${url}${accion === 'eliminar' ? '/productos/p1' : ''}`)
      .flush({}, { status: 500, statusText: 'Error' });
    expect(fixture.componentInstance.error()).toContain(accion === 'eliminar' ? 'eliminar' : 'descartar');
    expect(fixture.componentInstance.carrito()?.productos).toHaveLength(1);
  });

  it('redirige al login ante 401', () => {
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    http.expectOne(url).flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(navegar).toHaveBeenCalledWith(['/login'], { queryParams: { motivo: 'sesion-expirada' } });
  });

  it.each([200, 401, 0])('ante 403 comprueba sesión y no asume que ha caducado (%s)', status => {
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    http.expectOne(url).flush({}, { status: 403, statusText: 'Forbidden' });
    http.expectOne(`${environment.apiUrl}/usuarios/sesion-actual`).flush({}, { status, statusText: 'Session' });
    if (status === 401) expect(navegar).toHaveBeenCalled();
    else expect(navegar).not.toHaveBeenCalled();
  });
});
