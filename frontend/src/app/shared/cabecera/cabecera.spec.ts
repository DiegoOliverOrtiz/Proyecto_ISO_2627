import { HttpErrorResponse } from '@angular/common/http';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthService, RespuestaInicioSesion } from '../../pages/login/auth.service';
import { Cabecera, ENLACES_POR_ROL } from './cabecera';

@Component({ template: '' })
class PaginaVacia {}

describe('Cabecera (A18, A19)', () => {
  let fixture: ComponentFixture<Cabecera>;
  const authMock = {
    obtenerSesionActual: vi.fn<() => Observable<RespuestaInicioSesion>>(),
    cerrarSesion: vi.fn<() => Observable<void>>()
  };

  const crear = (sesion: Observable<RespuestaInicioSesion>): HTMLElement => {
    authMock.obtenerSesionActual.mockReturnValue(sesion);
    TestBed.configureTestingModule({
      imports: [Cabecera],
      providers: [
        provideRouter([{ path: '**', component: PaginaVacia }]),
        { provide: AuthService, useValue: authMock }
      ]
    });
    fixture = TestBed.createComponent(Cabecera);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };

  const sesionDe = (role: RespuestaInicioSesion['role']) => of({ email: 'u@example.com', role });

  const textosEnlaces = (raiz: HTMLElement) =>
    Array.from(raiz.querySelectorAll('nav a')).map((a) => a.textContent?.trim());

  afterEach(() => {
    vi.clearAllMocks();
    TestBed.resetTestingModule();
  });

  it.each([
    ['CLIENTE', '/cliente', ['Catálogo', 'Favoritos', 'Mis listas', 'Mis pedidos', 'Carrito', 'Mi perfil']],
    ['VENDEDOR', '/vendedor', ['Mis productos', 'Pedidos', 'Mi perfil']],
    ['ADMIN', '/admin', ['Panel', 'Usuarios', 'Categorías', 'Productos', 'Administradores', 'Mi perfil']]
  ] as const)('con rol %s muestra sus enlaces y la marca lleva a %s', (rol, inicio, textos) => {
    const raiz = crear(sesionDe(rol));

    expect(textosEnlaces(raiz)).toEqual(textos);
    expect(raiz.querySelector('.marca')?.getAttribute('href')).toBe(inicio);
    expect(raiz.querySelector('.marca')?.getAttribute('aria-label')).toBe('ESIBuy, inicio');
  });

  it('vendedor y administrador no ven «Mis listas» (pendiente del PO)', () => {
    expect(ENLACES_POR_ROL.VENDEDOR.map((e) => e.ruta)).not.toContain('/listas');
    expect(ENLACES_POR_ROL.ADMIN.map((e) => e.ruta)).not.toContain('/listas');
  });

  it('sin sesión solo muestra la marca, que lleva al inicio', () => {
    const raiz = crear(throwError(() => new HttpErrorResponse({ status: 401 })));

    expect(raiz.querySelector('nav')).toBeNull();
    expect(raiz.querySelector('.marca')?.getAttribute('href')).toBe('/');
  });

  it('el botón de menú indica si el menú está desplegado', () => {
    const raiz = crear(sesionDe('CLIENTE'));
    const boton = raiz.querySelector<HTMLButtonElement>('.boton-menu')!;

    expect(boton.getAttribute('aria-expanded')).toBe('false');
    boton.click();
    fixture.detectChanges();
    expect(boton.getAttribute('aria-expanded')).toBe('true');
    expect(raiz.querySelector('nav')?.classList).toContain('abierta');

    raiz.querySelector<HTMLAnchorElement>('nav a')!.click();
    fixture.detectChanges();
    expect(boton.getAttribute('aria-expanded')).toBe('false');
  });

  it('marca como página actual el enlace de la ruta activa', async () => {
    const raiz = crear(sesionDe('CLIENTE'));

    await TestBed.inject(Router).navigateByUrl('/cliente/favoritos');
    fixture.detectChanges();

    const activos = Array.from(raiz.querySelectorAll('nav a[aria-current="page"]'));
    expect(activos.map((a) => a.textContent?.trim())).toEqual(['Favoritos']);
  });

  it('al cerrar sesión lleva al login', () => {
    authMock.cerrarSesion.mockReturnValue(of(undefined));
    const raiz = crear(sesionDe('VENDEDOR'));
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl');

    raiz.querySelector<HTMLButtonElement>('.cerrar-sesion')!.click();

    expect(authMock.cerrarSesion).toHaveBeenCalled();
    expect(navegar).toHaveBeenCalledWith('/login');
  });

  it('si falla el cierre de sesión muestra el error y no navega', () => {
    authMock.cerrarSesion.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const raiz = crear(sesionDe('ADMIN'));
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl');

    raiz.querySelector<HTMLButtonElement>('.cerrar-sesion')!.click();
    fixture.detectChanges();

    expect(navegar).not.toHaveBeenCalled();
    expect(raiz.querySelector('[role="alert"]')?.textContent).toContain('No se pudo cerrar la sesión');
  });
});
