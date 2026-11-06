import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
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
