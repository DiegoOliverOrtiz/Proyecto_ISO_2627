import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AdministradorService } from '../admin/administrador.service';
import { UsuarioDetalle } from './usuario-detalle';
import { UsuarioAdministracion, UsuariosService } from './usuarios.service';

describe('UsuarioDetalle', () => {
  let componente: UsuarioDetalle;
  let fixture: ComponentFixture<UsuarioDetalle>;

  const usuario: UsuarioAdministracion = {
    id: 'cliente-1',
    nombre: 'Ana',
    apellidos: 'García',
    email: 'ana@example.com',
    telefono: '******123',
    estado: 'ACTIVO',
    rol: 'CLIENTE',
    imagenPerfil: null,
    sede: null,
    fechaIncorporacion: null,
    esPremium: false,
    nombreComercial: null,
    dniNif: '12*******',
    fechaNacimiento: '1990-01-01',
    categoriaId: null
  };

  const usuariosServiceMock = {
    consultarUsuario: vi.fn(),
    modificarUsuario: vi.fn()
  };

  beforeEach(async () => {
    usuariosServiceMock.consultarUsuario.mockReturnValue(of(usuario));
    usuariosServiceMock.modificarUsuario.mockReturnValue(of(usuario));

    await TestBed.configureTestingModule({
      imports: [UsuarioDetalle],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ id: usuario.id }),
              queryParamMap: convertToParamMap({})
            }
          }
        },
        { provide: UsuariosService, useValue: usuariosServiceMock },
        { provide: AdministradorService, useValue: { eliminarAdministrador: vi.fn() } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(UsuarioDetalle);
    componente = fixture.componentInstance;
    vi.spyOn(window, 'alert').mockImplementation(() => undefined);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.clearAllMocks();
    TestBed.resetTestingModule();
  });

  it('no precarga datos enmascarados y conserva o borra el teléfono según la selección', () => {
    componente.iniciarEdicion(usuario);

    expect(componente.formulario.controls.telefono.value).toBe('');
    expect(componente.formulario.controls.dniNif.value).toBe('');

    componente.formulario.patchValue({ nombre: 'Ana', apellidos: 'García' });
    componente.guardarCambios();
    expect(usuariosServiceMock.modificarUsuario).toHaveBeenLastCalledWith(
      usuario.id,
      expect.objectContaining({ telefono: null, dniNif: null })
    );

    componente.iniciarEdicion(usuario);
    componente.formulario.patchValue({
      nombre: 'Ana',
      apellidos: 'García',
      eliminarTelefono: true
    });
    componente.guardarCambios();
    expect(usuariosServiceMock.modificarUsuario).toHaveBeenLastCalledWith(
      usuario.id,
      expect.objectContaining({ telefono: '' })
    );
  });
});
