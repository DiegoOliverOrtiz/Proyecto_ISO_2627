import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import {
  afterEach,
  beforeEach,
  describe,
  expect,
  it,
  vi
} from 'vitest';

import { Usuarios } from './usuarios';
import {
  ListadoUsuarios,
  UsuarioAdministracion,
  UsuariosService
} from './usuarios.service';

describe('Usuarios', () => {
  let component: Usuarios;
  let fixture: ComponentFixture<Usuarios>;
  
  const usuario: UsuarioAdministracion = {
    id: 'usuario-1',
    nombre: 'Mario',
    apellidos: 'Muñoz',
    email: 'mario@example.com',
    telefono: null,
    estado: 'ACTIVO',
    rol: 'CLIENTE',
    imagenPerfil: null,
    sede: null,
    fechaIncorporacion: null,
    esPremium: false,
    nombreComercial: null,
    dniNif: null,
    fechaNacimiento: null,
    categoriaId: null
  };

  const listado: ListadoUsuarios = {
    contenido: [usuario],
    pagina: 0,
    tamano: 20,
    totalElementos: 1,
    totalPaginas: 1,
    estadisticas: {
      totalUsuarios: 1,
      activos: 1,
      pendientes: 0,
      desactivados: 0
    }
  };

  const usuariosServiceMock = {
    consultarUsuarios: vi.fn(),
    bloquearUsuario: vi.fn(),
    desbloquearUsuario: vi.fn(),
    activarUsuario: vi.fn() // Añadido mock
  };

  beforeEach(async () => {
    usuariosServiceMock.consultarUsuarios.mockReturnValue(of(listado));
    usuariosServiceMock.bloquearUsuario.mockReturnValue(of(void 0));
    usuariosServiceMock.desbloquearUsuario.mockReturnValue(of(void 0));
    usuariosServiceMock.activarUsuario.mockReturnValue(of(void 0)); // Añadido retorno por defecto

    await TestBed.configureTestingModule({
      imports: [Usuarios],
      providers: [
        provideRouter([]),
        {
          provide: UsuariosService,
          useValue: usuariosServiceMock
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(Usuarios);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.clearAllMocks();
    TestBed.resetTestingModule();
  });

  it('carga los usuarios al iniciar', () => {
    fixture.detectChanges();

    expect(
      usuariosServiceMock.consultarUsuarios
    ).toHaveBeenCalledWith(
      '',
      '',
      '',
      0,
      20
    );

    expect(component.listado()).toEqual(listado);
    expect(component.cargando()).toBe(false);
  });

  it('no bloquea al usuario si se cancela la confirmacion', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);

    fixture.detectChanges();

    component.bloquear(usuario);

    expect(window.confirm).toHaveBeenCalled();
    expect(
      usuariosServiceMock.bloquearUsuario
    ).not.toHaveBeenCalled();

    expect(component.usuarioEnProceso()).toBeNull();
  });

  it('bloquea al usuario y recarga el listado', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    fixture.detectChanges();

    component.bloquear(usuario);

    expect(
      usuariosServiceMock.bloquearUsuario
    ).toHaveBeenCalledWith('usuario-1');

    expect(component.usuarioEnProceso()).toBeNull();

    expect(component.mensajeExito()).toBe(
      'Mario Muñoz ha sido bloqueado correctamente.'
    );

    expect(
      usuariosServiceMock.consultarUsuarios
    ).toHaveBeenCalledTimes(2);
  });

  it('desbloquea al usuario y recarga el listado', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    fixture.detectChanges();

    component.desbloquear(usuario);

    expect(
      usuariosServiceMock.desbloquearUsuario
    ).toHaveBeenCalledWith('usuario-1');

    expect(component.usuarioEnProceso()).toBeNull();

    expect(component.mensajeExito()).toBe(
      'Mario Muñoz ha sido desbloqueado correctamente.'
    );

    expect(
      usuariosServiceMock.consultarUsuarios
    ).toHaveBeenCalledTimes(2);
  });

  // NUEVO TEST: Activar usuario feliz
  it('activa al usuario y recarga el listado', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    fixture.detectChanges();

    component.activar(usuario);

    expect(
      usuariosServiceMock.activarUsuario
    ).toHaveBeenCalledWith('usuario-1');

    expect(component.usuarioEnProceso()).toBeNull();

    expect(component.mensajeExito()).toBe(
      'Mario Muñoz ha sido activado correctamente.'
    );

    expect(
      usuariosServiceMock.consultarUsuarios
    ).toHaveBeenCalledTimes(2);
  });

  // NUEVO TEST: Cancelar activación
  it('no activa al usuario si se cancela la confirmacion', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);

    fixture.detectChanges();

    component.activar(usuario);

    expect(window.confirm).toHaveBeenCalled();
    expect(
      usuariosServiceMock.activarUsuario
    ).not.toHaveBeenCalled();

    expect(component.usuarioEnProceso()).toBeNull();
  });

  it('muestra el error del backend si falla el bloqueo', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    usuariosServiceMock.bloquearUsuario.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: {
              error: 'No puedes bloquear tu propia cuenta.'
            }
          })
      )
    );

    fixture.detectChanges();

    component.bloquear(usuario);

    expect(component.usuarioEnProceso()).toBeNull();

    expect(component.mensajeError()).toBe(
      'No puedes bloquear tu propia cuenta.'
    );

    expect(
      usuariosServiceMock.consultarUsuarios
    ).toHaveBeenCalledTimes(1);
  });

  it('muestra un mensaje si el usuario ya no existe al desbloquear', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    usuariosServiceMock.desbloquearUsuario.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 404
          })
      )
    );

    fixture.detectChanges();

    component.desbloquear(usuario);

    expect(component.usuarioEnProceso()).toBeNull();

    expect(component.mensajeError()).toBe(
      'El usuario ya no existe.'
    );
  });
});