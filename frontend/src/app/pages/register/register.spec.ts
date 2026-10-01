import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
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
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
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
    expect(fixture.nativeElement.querySelector('[formControlName="fechaNacimiento"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[formControlName="tipoCliente"]')).toBeTruthy();

    fixture.nativeElement.querySelector('.role-switch button:last-child').click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[formControlName="nombreComercial"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[formControlName="fechaNacimiento"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('.avatar-wrap img').getAttribute('src')).toBe('/avatars/vendedor.svg');
  });

  it('does not submit invalid data and reports mismatched passwords', () => {
    component.clienteForm.patchValue({ contrasena: 'password123', repetirContrasena: 'different123' });

    component.register();
    fixture.detectChanges();

    expect(component.clienteForm.invalid).toBe(true);
    expect(component.passwordMismatch(component.clienteForm)).toBe(true);
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
      contrasena: 'strong-pass-123',
      repetirContrasena: 'strong-pass-123',
      tipoCliente: 'premium',
    });

    component.register();
    const request = httpMock.expectOne('http://localhost:8080/cliente/register');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      nombre: 'Ada',
      apellidos: 'Lovelace',
      fechaNacimiento: '1990-12-10',
      dniNif: '12345678A',
      email: 'ada@example.com',
      contrasena: 'strong-pass-123',
      esPremium: true,
    });

    request.flush({ id: 'customer-1', tipoUsuario: 'CLIENTE' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('output').textContent).toContain('revisará tus datos');
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
      contrasena: 'strong-pass-123',
      repetirContrasena: 'strong-pass-123',
      tipoCliente: 'normal',
    });

    component.register();
    httpMock.expectOne('http://localhost:8080/cliente/register').flush(
      { error: 'Ya existe un usuario con los datos únicos indicados.' },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('output').textContent).toContain('Ya existe un usuario');
  });

  it('posts seller registration to the seller endpoint', () => {
    const registrationService = TestBed.inject(RegistrationService);
    const requestBody = {
      nombre: 'Grace',
      apellidos: 'Hopper',
      dniNif: '12345678B',
      email: 'grace@example.com',
      contrasena: 'strong-pass-123',
      categoria: 'Tecnología',
      nombreComercial: 'Grace Shop',
    };

    registrationService.registerVendedor(requestBody).subscribe();
    const request = httpMock.expectOne('http://localhost:8080/vendedor/register');

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(requestBody);
    request.flush({ id: 'seller-1', tipoUsuario: 'VENDEDOR' });
  });
});
