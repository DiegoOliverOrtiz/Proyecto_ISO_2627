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
  fechaIncorporacion: null, rol: null,
};

describe('Perfil', () => {
  let fixture: ComponentFixture<Perfil>;
  let componente: Perfil;
  let perfilServiceMock: { consultarPerfil: ReturnType<typeof vi.fn> };

  function crearComponente(respuesta: Observable<PerfilResponse>) {
    perfilServiceMock.consultarPerfil.mockReturnValue(respuesta);
    fixture = TestBed.createComponent(Perfil);
    componente = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(async () => {
    perfilServiceMock = { consultarPerfil: vi.fn() };
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
});
