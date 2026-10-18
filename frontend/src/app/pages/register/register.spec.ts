import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { Register } from './register';
import { RegistrationService } from './registration.service';

describe('Register', () => {
  let component: Register;
  let fixture: ComponentFixture<Register>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Register],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([])
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Register);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);

    await fixture.whenStable();
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('shows the customer form by default and switches to the seller form', () => {
    expect(
      fixture.nativeElement.querySelector('[formControlName="fechaNacimiento"]')
    ).toBeTruthy();

    expect(
      fixture.nativeElement.querySelector('[formControlName="tipoCliente"]')
    ).toBeTruthy();

    fixture.nativeElement
      .querySelector('.role-switch button:last-child')
      .click();

    httpMock.expectOne(
      'http://localhost:8080/categorias/registro'
    ).flush([]);

    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[formControlName="nombreComercial"]')
    ).toBeTruthy();

    expect(
      fixture.nativeElement.querySelector('[formControlName="fechaNacimiento"]')
    ).toBeNull();

    expect(
      fixture.nativeElement
        .querySelector('.avatar-wrap img')
        .getAttribute('src')
    ).toBe('/avatars/vendedor.svg');
  });

  it('does not submit invalid data and reports mismatched passwords', () => {
    component.clienteForm.patchValue({
      contrasena: 'password123',
      repetirContrasena: 'different123'
    });

    component.register();
    fixture.detectChanges();

    expect(component.clienteForm.invalid).toBe(true);
    expect(component.passwordMismatch(component.clienteForm)).toBe(true);

    httpMock.expectNone('http://localhost:8080/cliente/register');
  });

  it('validates all password requirements for customer and seller forms', () => {
    const passwords = [
      'password1!',
      'PASSWORD1!',
      'Password!',
      'Password1',
      'Password1 ',
    ];

    for (const password of passwords) {
      component.clienteForm.controls.contrasena.setValue(password);
      component.vendedorForm.controls.contrasena.setValue(password);

      expect(
        component.clienteForm.controls.contrasena.hasError('passwordRequirements')
      ).toBe(true);
      expect(
        component.vendedorForm.controls.contrasena.hasError('passwordRequirements')
      ).toBe(true);
    }

    component.clienteForm.controls.contrasena.setValue('Password1!');
    component.vendedorForm.controls.contrasena.setValue('Password1!');

    expect(component.clienteForm.controls.contrasena.valid).toBe(true);
    expect(component.vendedorForm.controls.contrasena.valid).toBe(true);
  });

  it('shows password requirements on focus and marks satisfied rules with a check', () => {
    expect(
      fixture.nativeElement.querySelector('.password-requirements')
    ).toBeNull();

    const passwordInput = fixture.nativeElement.querySelector(
      '[formControlName="contrasena"]'
    ) as HTMLInputElement;

    passwordInput.dispatchEvent(new Event('focus'));
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('.password-requirements')
    ).toBeTruthy();
    expect(
      fixture.nativeElement.querySelectorAll('.password-requirements li.met')
    ).toHaveLength(0);

    passwordInput.dispatchEvent(new Event('blur'));
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('.password-requirements')
    ).toBeNull();

    passwordInput.dispatchEvent(new Event('focus'));
    fixture.detectChanges();
    component.clienteForm.controls.contrasena.setValue('Password1!');
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelectorAll('.password-requirements li.met')
    ).toHaveLength(5);
    expect(
      fixture.nativeElement.querySelector('.requirement-status').textContent
    ).toBe('✓');

    passwordInput.dispatchEvent(new Event('blur'));
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('.password-requirements')
    ).toBeTruthy();
  });

  it('prevents customers under 18 from submitting', () => {
    component.clienteForm.patchValue({
      nombre: 'Ada',
      apellidos: 'Lovelace',
      fechaNacimiento: '2020-01-01',
      dniNif: '12345678A',
      email: 'ada@example.com',
      contrasena: 'Strong-pass-123!',
      repetirContrasena: 'Strong-pass-123!',
    });

    component.register();

    expect(
      component.clienteForm.controls.fechaNacimiento.hasError('underage')
    ).toBe(true);

    expect(
      component.controlError(component.clienteForm, 'fechaNacimiento')
    ).toContain('18 años');

    httpMock.expectNone('http://localhost:8080/cliente/register');
  });

  it('maps a premium customer request and confirms pending activation', () => {
    component.clienteForm.setValue({
      nombre: 'Ada',
      apellidos: 'Lovelace',
      fechaNacimiento: '1990-12-10',
      dniNif: '12345678A',
      email: 'ada@example.com',
      telefono: '',
      imagenPerfil: '',
      contrasena: 'Strong-pass-123!',
      repetirContrasena: 'Strong-pass-123!',
      tipoCliente: 'premium',
    });

    component.register();

    const request = httpMock.expectOne(
      'http://localhost:8080/cliente/register'
    );

    expect(request.request.method).toBe('POST');

    expect(request.request.body).toEqual({
      nombre: 'Ada',
      apellidos: 'Lovelace',
      fechaNacimiento: '1990-12-10',
      dniNif: '12345678A',
      email: 'ada@example.com',
      contrasena: 'Strong-pass-123!',
      esPremium: true,
    });

    request.flush({
      id: 'customer-1',
      tipoUsuario: 'CLIENTE'
    });

    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('output').textContent
    ).toContain('revisará tus datos');
  });

  it('shows the safe backend message when registration conflicts', () => {
    component.clienteForm.setValue({
      nombre: 'Ada',
      apellidos: 'Lovelace',
      fechaNacimiento: '1990-12-10',
      dniNif: '12345678A',
      email: 'ada@example.com',
      telefono: '',
      imagenPerfil: '',
      contrasena: 'Strong-pass-123!',
      repetirContrasena: 'Strong-pass-123!',
      tipoCliente: 'normal',
    });

    component.register();

    httpMock.expectOne(
      'http://localhost:8080/cliente/register'
    ).flush(
      {
        error: 'Ya existe un usuario con los datos únicos indicados.'
      },
      {
        status: 409,
        statusText: 'Conflict'
      }
    );

    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('output').textContent
    ).toContain('Ya existe un usuario');
  });

  it('shows all field errors returned together by the backend', () => {
    component.clienteForm.setValue({
      nombre: 'Ada',
      apellidos: 'Lovelace',
      fechaNacimiento: '1990-12-10',
      dniNif: '12345678A',
      email: 'ada@example.com',
      telefono: '',
      imagenPerfil: '',
      contrasena: 'Strong-pass-123!',
      repetirContrasena: 'Strong-pass-123!',
      tipoCliente: 'normal',
    });

    component.register();

    httpMock.expectOne(
      'http://localhost:8080/cliente/register'
    ).flush(
      {
        error: 'Revisa los campos del formulario.',
        errores: {
          fechaNacimiento: ['El cliente debe tener al menos 18 años.'],
          email: ['Introduce un correo electrónico válido.'],
        },
      },
      {
        status: 400,
        statusText: 'Bad Request'
      }
    );

    fixture.detectChanges();

    const errorItems = Array.from(
      fixture.nativeElement.querySelectorAll('.feedback li'),
      (item: Element) => item.textContent,
    );

    expect(errorItems).toEqual([
      'Fecha de nacimiento: El cliente debe tener al menos 18 años.',
      'Correo electrónico: Introduce un correo electrónico válido.',
    ]);
  });

  // HU11: comprobar que el registro carga las categorías del backend.
  it('loads categories when selecting the seller role', async () => {
    fixture.nativeElement
      .querySelector('.role-switch button:last-child')
      .click();

    const request = httpMock.expectOne(
      'http://localhost:8080/categorias/registro'
    );

    expect(request.request.method).toBe('GET');

    request.flush([
      {
        id: '6abe34098deff53da8a2c052',
        nombre: 'Deportes',
      },
    ]);

    await fixture.whenStable();
    fixture.detectChanges();

    expect(component.sellerCategories).toEqual([
      {
        id: '6abe34098deff53da8a2c052',
        nombre: 'Deportes',
      },
    ]);

    expect(fixture.nativeElement.textContent).toContain('Deportes');

    expect(
      component.vendedorForm.controls.categoriaId
    ).toBeTruthy();
  });

  it('posts seller registration to the seller endpoint', () => {
    const registrationService = TestBed.inject(RegistrationService);

    const requestBody = {
      nombre: 'Grace',
      apellidos: 'Hopper',
      dniNif: '12345678B',
      email: 'grace@example.com',
      contrasena: 'Strong-pass-123!',
      categoriaId: '6abe34098deff53da8a2c052',
      nombreComercial: 'Grace Shop',
    };

    registrationService.registerVendedor(requestBody).subscribe();

    const request = httpMock.expectOne(
      'http://localhost:8080/vendedor/register'
    );

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(requestBody);

    request.flush({
      id: 'seller-1',
      tipoUsuario: 'VENDEDOR'
    });
  });
});