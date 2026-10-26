import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Perfil } from './perfil';
import { PerfilResponse, PerfilService } from './perfil.service';

const perfilCliente: PerfilResponse = {
  tipoUsuario: 'CLIENTE', nombre: 'Ana', apellidos: 'García',
  email: 'ana@esibuy.com', telefono: '600123123', imagenPerfil: null,
  dniNif: '12*******', fechaNacimiento: null, esPremium: true,
  nombreComercial: null, categoria: null, sede: null,
  fechaIncorporacion: null, rol: null,
};

const perfilVendedor: PerfilResponse = {
  tipoUsuario: 'VENDEDOR', nombre: 'Luis', apellidos: 'Martín',
  email: 'luis@esibuy.com', telefono: null, imagenPerfil: null,
  dniNif: '45*******', fechaNacimiento: null, esPremium: null,
  nombreComercial: 'UrbanStyle', categoria: 'Tecnología', sede: null,
  fechaIncorporacion: null, rol: null, categoriaId: 'cat-1',
};

describe('Perfil', () => {
  let fixture: ComponentFixture<Perfil>;
  let componente: Perfil;
  let perfilServiceMock: {
    consultarPerfil: ReturnType<typeof vi.fn>;
    obtenerCategorias: ReturnType<typeof vi.fn>;
    modificarPerfil: ReturnType<typeof vi.fn>;
  };

  function crearComponente(respuesta: Observable<PerfilResponse>) {
    perfilServiceMock.consultarPerfil.mockReturnValue(respuesta);
    fixture = TestBed.createComponent(Perfil);
    componente = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(async () => {
    perfilServiceMock = {
      consultarPerfil: vi.fn(),
      obtenerCategorias: vi.fn(() => of([])),
      modificarPerfil: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [Perfil],
      providers: [
        provideRouter([]),
        { provide: PerfilService, useValue: perfilServiceMock },
      ],
    }).compileComponents();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('carga y muestra el perfil del cliente', () => {
    crearComponente(of(perfilCliente));

    expect(componente.perfil()).toEqual(perfilCliente);
    expect(componente.cargando()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('CLIENTE');
    expect(fixture.nativeElement.textContent).toContain('Ana García');
    expect(fixture.nativeElement.textContent).toContain('12*******');
  });

  it('muestra la categoría del vendedor cuando está asociada', () => {
    crearComponente(of(perfilVendedor));

    expect(fixture.nativeElement.textContent).toContain('Tecnología');
    expect(fixture.nativeElement.textContent).not.toContain('No hay una categoría asociada');
  });

  it('muestra un mensaje cuando el vendedor no tiene categoría', () => {
    crearComponente(of({ ...perfilVendedor, categoria: null }));

    expect(fixture.nativeElement.textContent).toContain('No hay una categoría asociada');
  });

  it('muestra el error devuelto por el backend', () => {
    crearComponente(throwError(() => new HttpErrorResponse({ status: 500 })));

    expect(componente.cargando()).toBe(false);
    expect(componente.mensajeError()).toContain('No se pudo cargar el perfil');
  });

  it('redirige al login si la sesión ha caducado', () => {
    const router = TestBed.inject(Router);
    const navegar = vi.spyOn(router, 'navigate');
    crearComponente(throwError(() => new HttpErrorResponse({ status: 401 })));

    expect(navegar).toHaveBeenCalledWith(['/login'], { queryParams: { motivo: 'sesion-expirada' } });
  });
  it('permite editar y guardar los campos autorizados de un cliente', () => {
    crearComponente(of(perfilCliente));
    componente.iniciarEdicion();
    componente.formulario.patchValue({
      nombre: 'Ana Nueva', apellidos: 'García López', telefono: '600123123',
      dniNif: '12345678A', fechaNacimiento: '2000-01-01', imagenPerfil: '',
    });
    const perfilActualizado = { ...perfilCliente, nombre: 'Ana Nueva' };
    perfilServiceMock.modificarPerfil.mockReturnValue(of(perfilActualizado));

    componente.guardarCambios();

    expect(perfilServiceMock.modificarPerfil).toHaveBeenCalledWith({
      nombre: 'Ana Nueva', apellidos: 'García López', telefono: '600123123',
      imagenPerfil: null, dniNif: '12345678A', fechaNacimiento: '2000-01-01',
    });
    expect(componente.perfil()).toEqual(perfilActualizado);
    expect(componente.mensajeExito()).toContain('actualizado');
    expect(componente.editando()).toBe(false);
  });

  it('permite al vendedor seleccionar una categoría', () => {
    perfilServiceMock.obtenerCategorias.mockReturnValue(of([
      { id: 'cat-1', nombre: 'Tecnología' }, { id: 'cat-2', nombre: 'Hogar' },
    ]));
    crearComponente(of(perfilVendedor));
    componente.iniciarEdicion();
    componente.formulario.patchValue({ nombre: 'Luis', apellidos: 'Martín', categoriaId: 'cat-2' });
    const perfilActualizado = { ...perfilVendedor, categoria: 'Hogar', categoriaId: 'cat-2' };
    perfilServiceMock.modificarPerfil.mockReturnValue(of(perfilActualizado));

    componente.guardarCambios();

    expect(perfilServiceMock.modificarPerfil).toHaveBeenCalledWith(expect.objectContaining({ categoriaId: 'cat-2' }));
    expect(componente.perfil()).toEqual(perfilActualizado);
  });

  it('rechaza DNI y teléfono con formato incorrecto antes de enviar', () => {
    crearComponente(of(perfilCliente));
    componente.iniciarEdicion();
    componente.formulario.patchValue({ nombre: 'Ana', apellidos: 'García', telefono: 'telefono-invalido', dniNif: '123' });

    componente.guardarCambios();

    expect(perfilServiceMock.modificarPerfil).not.toHaveBeenCalled();
    expect(componente.mensajeFormulario()).toContain('Revisa');
  });

  it('no incluye contraseña ni direcciones en el formulario de edición', () => {
    crearComponente(of(perfilCliente));
    componente.iniciarEdicion();

    expect(fixture.nativeElement.querySelector('[formcontrolname="password"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[formcontrolname="contrasena"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[formcontrolname="direccion"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[formcontrolname="direcciones"]')).toBeNull();
  });

  it('muestra el error del backend y mantiene el modo edición', () => {
    crearComponente(of(perfilCliente));
    componente.iniciarEdicion();
    componente.formulario.patchValue({ nombre: 'Ana', apellidos: 'García', telefono: '600123123', dniNif: '12345678A' });
    perfilServiceMock.modificarPerfil.mockReturnValue(throwError(() =>
      new HttpErrorResponse({ status: 400, error: { message: 'Datos inválidos' } })));

    componente.guardarCambios();

    expect(componente.mensajeFormulario()).toContain('Datos inválidos');
    expect(componente.guardando()).toBe(false);
    expect(componente.editando()).toBe(true);
  });
});
