import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { environment } from '../../../environments/environment';
import { Favoritos } from './favoritos';

describe('Favoritos', () => {
  let fixture: ComponentFixture<Favoritos>;
  let http: HttpTestingController;
  const url = `${environment.apiUrl}/usuarios/favoritos`;
  const texto = (): string => {
    fixture.detectChanges();
    return fixture.nativeElement.textContent as string;
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Favoritos],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
    });
    fixture = TestBed.createComponent(Favoritos);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
  });

  it('si falla la carga muestra el error con «Reintentar» y no el estado vacío (A17, M5)', () => {
    http.expectOne(url).flush({}, { status: 500, statusText: 'Error' });

    expect(texto()).toContain('No se pudieron cargar tus favoritos.');
    expect(texto()).not.toContain('Todavía no tienes favoritos');

    const reintentar: HTMLButtonElement = fixture.nativeElement.querySelector('[role="alert"] button');
    expect(reintentar.textContent).toContain('Reintentar');
    reintentar.click();
    http.expectOne(url).flush({ ids: [], productos: [] });

    expect(texto()).toContain('Todavía no tienes favoritos');
  });

  it('lista los favoritos con su precio y un marcador si no tienen imagen', () => {
    http.expectOne(url).flush({
      ids: ['p1'],
      productos: [{ id: 'p1', nombre: 'Ratón', imagen: null, precio: 24.99, stock: 3, disponible: true, aviso: null }]
    });

    expect(texto()).toContain('1 producto guardado');
    expect(texto()).toContain('24.99 €');
    expect(fixture.nativeElement.querySelector('.favorite-card .marco-imagen svg')).not.toBeNull();
  });
});
