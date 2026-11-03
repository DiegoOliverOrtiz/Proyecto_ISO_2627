import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { environment } from '../../../environments/environment';
import { Listas } from './listas';
import type { ListaProductos, ResumenLista } from './listas.service';

describe('Listas - HU-39', () => {
  const url = `${environment.apiUrl}/usuarios/listas`;
  const csrfUrl = `${environment.apiUrl}/usuarios/csrf`;

  const favoritos: ResumenLista = { id: 'favoritos:c1', nombre: 'Favoritos', predeterminada: true, productoIds: [] };
  const comida: ResumenLista = { id: 'l1', nombre: 'Comida', predeterminada: false, productoIds: ['p1', 'p2'] };

  const detalleFavoritos: ListaProductos = { id: 'favoritos:c1', nombre: 'Favoritos', predeterminada: true, productos: [] };
  const detalleComida: ListaProductos = {
    id: 'l1',
    nombre: 'Comida',
    predeterminada: false,
    productos: [
      { id: 'p1', nombre: 'Aceite', imagen: null, precio: 8, stock: 4, disponible: true, aviso: null },
      { id: 'p2', nombre: 'Café', imagen: null, precio: null, stock: 0, disponible: false, aviso: 'Producto no disponible' }
    ]
  };

  let fixture: ComponentFixture<Listas>;
  let http: HttpTestingController;

  function texto(): string {
    return fixture.nativeElement.textContent;
  }

  function cargarInicio(listas: ResumenLista[] = [favoritos, comida]): void {
    http.expectOne(url).flush(listas);
    http.expectOne(`${url}/favoritos%3Ac1`).flush(detalleFavoritos);
    fixture.detectChanges();
  }

  function seleccionarComida(): void {
    const botones: HTMLButtonElement[] = [...fixture.nativeElement.querySelectorAll('.lists-menu button')];
    botones[1].click();
    http.expectOne(`${url}/l1`).flush(detalleComida);
    fixture.detectChanges();
  }

  function escribir(selector: string, valor: string): void {
    const campo: HTMLInputElement = fixture.nativeElement.querySelector(selector);
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function pulsar(texto: string): void {
    const boton = [...fixture.nativeElement.querySelectorAll('button')]
      .find((elemento: HTMLButtonElement) => elemento.textContent?.trim() === texto) as HTMLButtonElement;
    boton.click();
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Listas],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
    });
    fixture = TestBed.createComponent(Listas);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    http.verify();
    TestBed.resetTestingModule();
  });

  it('muestra favoritos siempre, aunque esté vacía, y sin acciones de renombrar o eliminar', () => {
    expect(texto()).toContain('Cargando listas...');
    cargarInicio([favoritos]);

    expect(texto()).toContain('Favoritos');
    expect(texto()).toContain('Esta lista no tiene productos.');
    expect(texto()).not.toContain('Renombrar');
    expect(texto()).not.toContain('Eliminar lista');
  });

  it('muestra los productos de una lista y marca los no disponibles como no comprables', () => {
    cargarInicio();
    seleccionarComida();

    const productos = fixture.nativeElement.querySelectorAll('.product');
    expect(productos.length).toBe(2);
    expect(productos[0].textContent).toContain('Aceite');
    expect(productos[1].classList.contains('unavailable')).toBe(true);
    expect(productos[1].textContent).toContain('Producto no disponible: no se puede comprar');
  });

  it('crea una lista con el nombre recortado y CSRF', () => {
    cargarInicio();
    escribir('#nueva-lista', '  Videojuegos  ');
    pulsar('Crear lista');

    http.expectOne(csrfUrl).flush({ token: 'csrf' });
    const peticion = http.expectOne(url);
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual({ nombre: 'Videojuegos' });
    expect(peticion.request.headers.get('X-XSRF-TOKEN')).toBe('csrf');
    peticion.flush({ id: 'l3', nombre: 'Videojuegos', predeterminada: false, productos: [] });

    const nueva: ResumenLista = { id: 'l3', nombre: 'Videojuegos', predeterminada: false, productoIds: [] };
    http.expectOne(url).flush([favoritos, comida, nueva]);
    http.expectOne(`${url}/l3`).flush({ id: 'l3', nombre: 'Videojuegos', predeterminada: false, productos: [] });
    fixture.detectChanges();

    expect(texto()).toContain('Lista «Videojuegos» creada.');
    expect(fixture.componentInstance.nombreNueva.value).toBe('');
  });

  it.each([
    ['vacío', '   ', 'El nombre de la lista es obligatorio.'],
    ['largo', 'a'.repeat(51), 'El nombre no puede superar 50 caracteres.']
  ])('no envía una lista con nombre %s', (_caso, valor, mensaje) => {
    cargarInicio();
    fixture.componentInstance.nombreNueva.setValue(valor);
    pulsar('Crear lista');

    expect(texto()).toContain(mensaje);
    http.expectNone(csrfUrl);
  });

  it('muestra el conflicto del backend al repetir un nombre', () => {
    cargarInicio();
    escribir('#nueva-lista', 'comida');
    pulsar('Crear lista');

    http.expectOne(csrfUrl).flush({ token: 'csrf' });
    http.expectOne(url).flush({ error: 'Ya tienes una lista con ese nombre.' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent)
      .toContain('Ya tienes una lista con ese nombre.');
  });

  it('renombra la lista seleccionada', () => {
    cargarInicio();
    seleccionarComida();
    pulsar('Renombrar');
    escribir('#renombrar-lista', 'Alimentación');
    pulsar('Guardar');

    http.expectOne(csrfUrl).flush({ token: 'csrf' });
    const peticion = http.expectOne(`${url}/l1`);
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual({ nombre: 'Alimentación' });
    peticion.flush({ ...detalleComida, nombre: 'Alimentación' });

    http.expectOne(url).flush([favoritos, { ...comida, nombre: 'Alimentación' }]);
    http.expectOne(`${url}/l1`).flush({ ...detalleComida, nombre: 'Alimentación' });
    fixture.detectChanges();

    expect(texto()).toContain('Lista renombrada a «Alimentación».');
  });

  it('elimina una lista con productos tras confirmar y vuelve a favoritos', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    cargarInicio();
    seleccionarComida();
    pulsar('Eliminar lista');

    expect(window.confirm).toHaveBeenCalledWith(
      '¿Eliminar la lista «Comida»? Sus productos seguirán en el catálogo.'
    );
    http.expectOne(csrfUrl).flush({ token: 'csrf' });
    const peticion = http.expectOne(`${url}/l1`);
    expect(peticion.request.method).toBe('DELETE');
    peticion.flush(null, { status: 204, statusText: 'No Content' });

    cargarInicio([favoritos]);

    expect(texto()).toContain('Lista «Comida» eliminada.');
    expect(fixture.nativeElement.querySelectorAll('.lists-menu button').length).toBe(1);
  });

  it('no elimina si el usuario cancela la confirmación', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    cargarInicio();
    seleccionarComida();
    pulsar('Eliminar lista');

    http.expectNone(csrfUrl);
  });

  it('quita un producto de la lista y actualiza el contador', () => {
    cargarInicio();
    seleccionarComida();
    fixture.nativeElement.querySelector('[aria-label="Quitar Aceite de la lista"]').click();

    http.expectOne(csrfUrl).flush({ token: 'csrf' });
    const peticion = http.expectOne(`${url}/l1/productos/p1`);
    expect(peticion.request.method).toBe('DELETE');
    peticion.flush({ ...detalleComida, productos: [detalleComida.productos[1]] });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.product').length).toBe(1);
    expect(fixture.componentInstance.listas()[1].productoIds).toEqual(['p2']);
    expect(texto()).toContain('Producto quitado de la lista.');
  });

  it('informa cuando la lista no existe o es de otro usuario', () => {
    cargarInicio();
    const botones: HTMLButtonElement[] = [...fixture.nativeElement.querySelectorAll('.lists-menu button')];
    botones[1].click();
    http.expectOne(`${url}/l1`)
      .flush({ error: 'No se ha encontrado la lista solicitada.' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(texto()).toContain('No se ha encontrado la lista solicitada.');
  });

  it('no muestra detalles técnicos de errores internos', () => {
    http.expectOne(url).flush({ error: 'MongoException interna' }, { status: 500, statusText: 'Error' });
    fixture.detectChanges();

    expect(texto()).not.toContain('MongoException');
    expect(texto()).toContain('No se pudieron cargar tus listas.');
  });

  it('redirige al login si la sesión ha caducado', () => {
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    http.expectOne(url).flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(navegar).toHaveBeenCalledWith(['/login'], { queryParams: { motivo: 'sesion-expirada' } });
  });
});
