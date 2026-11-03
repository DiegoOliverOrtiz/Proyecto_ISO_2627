import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Observable } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { environment } from '../../../environments/environment';
import { ListasService } from './listas.service';

describe('ListasService - HU-39', () => {
  const url = `${environment.apiUrl}/usuarios/listas`;
  let servicio: ListasService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(ListasService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
  });

  it('consulta las listas con credenciales y sin enviar propietario', () => {
    servicio.consultarListas().subscribe();
    const peticion = http.expectOne(url);
    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.withCredentials).toBe(true);
    expect(peticion.request.params.keys()).toEqual([]);
    peticion.flush([]);
  });

  it('consulta una lista con el identificador codificado', () => {
    servicio.consultarLista('favoritos:c/1').subscribe();
    const peticion = http.expectOne(`${url}/favoritos%3Ac%2F1`);
    expect(peticion.request.withCredentials).toBe(true);
    peticion.flush({ id: 'favoritos:c/1', nombre: 'Favoritos', predeterminada: true, productos: [] });
  });

  it.each([
    ['crear', 'POST', '', { nombre: 'Comida' }],
    ['renombrar', 'PUT', '/l1', { nombre: 'Comida' }],
    ['eliminar', 'DELETE', '/l1', null],
    ['anadir', 'PUT', '/l1/productos/p%2F1', null],
    ['quitar', 'DELETE', '/l1/productos/p%2F1', null]
  ])('envía %s con token CSRF y sesión', (accion, metodo, ruta, cuerpo) => {
    const operaciones: Record<string, () => Observable<unknown>> = {
      crear: () => servicio.crearLista('Comida'),
      renombrar: () => servicio.renombrarLista('l1', 'Comida'),
      eliminar: () => servicio.eliminarLista('l1'),
      anadir: () => servicio.anadirProducto('l1', 'p/1'),
      quitar: () => servicio.quitarProducto('l1', 'p/1')
    };

    operaciones[accion]().subscribe();

    const csrf = http.expectOne(`${environment.apiUrl}/usuarios/csrf`);
    expect(csrf.request.withCredentials).toBe(true);
    csrf.flush({ token: 'token-listas' });

    const mutacion = http.expectOne(`${url}${ruta}`);
    expect(mutacion.request.method).toBe(metodo);
    expect(mutacion.request.withCredentials).toBe(true);
    expect(mutacion.request.headers.get('X-XSRF-TOKEN')).toBe('token-listas');
    expect(mutacion.request.body ?? null).toEqual(cuerpo);
    mutacion.flush(null);
  });

  it('no envía la mutación si falla el token CSRF', () => {
    let error: HttpErrorResponse | undefined;
    servicio.crearLista('Comida').subscribe({ error: (fallo) => (error = fallo) });
    http.expectOne(`${environment.apiUrl}/usuarios/csrf`)
      .flush({}, { status: 403, statusText: 'Forbidden' });
    http.expectNone(url);
    expect(error?.status).toBe(403);
  });
});
