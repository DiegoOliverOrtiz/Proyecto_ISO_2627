import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { describe, expect, it } from 'vitest';

import { App } from './app';
import { AuthService } from './pages/login/auth.service';

@Component({ template: '' })
class PaginaVacia {}

describe('App', () => {
  const crear = () => {
    TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([
          { path: 'publica', component: PaginaVacia, data: { cabecera: false } },
          { path: 'privada', component: PaginaVacia }
        ]),
        {
          provide: AuthService,
          useValue: { obtenerSesionActual: () => of({ email: 'c@example.com', role: 'CLIENTE' }) }
        }
      ]
    });
    return TestBed.createComponent(App);
  };

  it('should create the app', () => {
    expect(crear().componentInstance).toBeTruthy();
  });

  it('muestra la cabecera común en las rutas con sesión y la oculta en las públicas', async () => {
    const fixture = crear();
    const enrutador = TestBed.inject(Router);

    await enrutador.navigateByUrl('/privada');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-cabecera')).not.toBeNull();

    await enrutador.navigateByUrl('/publica');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-cabecera')).toBeNull();
  });
});
