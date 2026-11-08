import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { Login } from './login';

describe('Login · plantilla accesible (UI-acceso)', () => {
  let fixture: ComponentFixture<Login>;
  let raiz: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
    });
    fixture = TestBed.createComponent(Login);
    fixture.detectChanges();
    raiz = fixture.nativeElement as HTMLElement;
  });

  afterEach(() => TestBed.resetTestingModule());

  it('el título de la página es el h1 «Iniciar sesión»', () => {
    expect(raiz.querySelectorAll('h1').length).toBe(1);
    expect(raiz.querySelector('h1')?.textContent?.trim()).toBe('Iniciar sesión');
  });

  it('los campos llevan autocomplete de correo y contraseña actual (B6)', () => {
    expect(raiz.querySelector('#email')?.getAttribute('autocomplete')).toBe('email');
    expect(raiz.querySelector('#password')?.getAttribute('autocomplete')).toBe('current-password');
  });

  it('el botón del ojo tiene nombre accesible y estado, sin emojis (A10)', () => {
    const ojo = raiz.querySelector<HTMLButtonElement>('.boton-ojo')!;

    expect(ojo.getAttribute('aria-label')).toBe('Mostrar contraseña');
    expect(ojo.getAttribute('aria-pressed')).toBe('false');
    expect(ojo.textContent?.trim()).toBe('');

    ojo.click();
    fixture.detectChanges();

    expect(ojo.getAttribute('aria-label')).toBe('Ocultar contraseña');
    expect(ojo.getAttribute('aria-pressed')).toBe('true');
    expect(raiz.querySelector('#password')?.getAttribute('type')).toBe('text');
  });

  it('presenta un campo accesible para el tercer factor por correo', () => {
    fixture.componentInstance.requiereVerificacionEmail.set(true);
    fixture.detectChanges();

    expect(raiz.querySelector('h1')?.textContent?.trim()).toBe('Verificar correo electrónico');
    expect(raiz.querySelector('#email-otp-code')?.getAttribute('autocomplete')).toBe('one-time-code');
    expect(raiz.querySelector('button[type="submit"]')?.textContent).toContain('Verificar código');
  });

  it('asocia cada error a su campo con aria-invalid y aria-describedby (M3)', () => {
    const correo = raiz.querySelector<HTMLInputElement>('#email')!;
    expect(correo.getAttribute('aria-invalid')).toBe('false');

    fixture.componentInstance.loginForm.markAllAsTouched();
    fixture.detectChanges();

    expect(correo.getAttribute('aria-invalid')).toBe('true');
    expect(correo.getAttribute('aria-describedby')).toBe('email-error');
    expect(raiz.querySelector('#email-error')?.textContent).toContain('El correo es obligatorio.');
    expect(raiz.querySelector('#password')?.getAttribute('aria-describedby')).toBe('password-error');
  });
});

describe('Login · aviso cuando no hay sesión (A12)', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('con motivo «sin-sesion» invita a iniciar sesión sin hablar de bloqueos', () => {
    TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({ motivo: 'sin-sesion' }) } } }
      ]
    });
    const fixture = TestBed.createComponent(Login);
    fixture.detectChanges();
    const raiz = fixture.nativeElement as HTMLElement;

    expect(raiz.querySelector('.aviso-info')?.textContent).toContain('Inicia sesión para continuar.');
    expect(raiz.querySelector('[role="alert"]')).toBeNull();
    expect(raiz.textContent).not.toContain('bloqueada');
  });
});
