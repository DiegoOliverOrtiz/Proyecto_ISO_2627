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
import { ConfirmacionService } from '../../shared/confirmacion/confirmacion.service';

// M8: la confirmación es un diálogo propio (asíncrono); se simula su respuesta.
const simularConfirmacion = (aceptada: boolean) =>
  vi.spyOn(TestBed.inject(ConfirmacionService), 'confirmar').mockResolvedValue(aceptada);

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

  it('no bloquea al usuario si se cancela la confirmacion', async () => {
    const confirmar = simularConfirmacion(false);

    fixture.detectChanges();

    await component.bloquear(usuario);

    expect(confirmar).toHaveBeenCalled();
    expect(
      usuariosServiceMock.bloquearUsuario
    ).not.toHaveBeenCalled();

    expect(component.usuarioEnProceso()).toBeNull();
  });

  it('bloquea al usuario y recarga el listado', async () => {
    simularConfirmacion(true);

    fixture.detectChanges();

    await component.bloquear(usuario);

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

  it('desbloquea al usuario y recarga el listado', async () => {
    simularConfirmacion(true);

    fixture.detectChanges();

    await component.desbloquear(usuario);

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
  it('activa al usuario y recarga el listado', async () => {
    simularConfirmacion(true);

    fixture.detectChanges();

    await component.activar(usuario);

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
  it('no activa al usuario si se cancela la confirmacion', async () => {
    const confirmar = simularConfirmacion(false);

    fixture.detectChanges();

    await component.activar(usuario);

    expect(confirmar).toHaveBeenCalled();
    expect(
      usuariosServiceMock.activarUsuario
    ).not.toHaveBeenCalled();

    expect(component.usuarioEnProceso()).toBeNull();
  });

  it('muestra el error del backend si falla el bloqueo', async () => {
    simularConfirmacion(true);

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

    await component.bloquear(usuario);

    expect(component.usuarioEnProceso()).toBeNull();

    expect(component.mensajeError()).toBe(
      'No puedes bloquear tu propia cuenta.'
    );

    expect(
      usuariosServiceMock.consultarUsuarios
    ).toHaveBeenCalledTimes(1);
  });

  it('muestra un mensaje si el usuario ya no existe al desbloquear', async () => {
    simularConfirmacion(true);

    usuariosServiceMock.desbloquearUsuario.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 404
          })
      )
    );

    fixture.detectChanges();

    await component.desbloquear(usuario);

    expect(component.usuarioEnProceso()).toBeNull();

    expect(component.mensajeError()).toBe(
      'El usuario ya no existe.'
    );
  });
  describe('marcado accesible (M9, M24)', () => {
    const fila = () => (fixture.nativeElement as HTMLElement).querySelector('tbody tr') as HTMLElement;

    it('el estado es un chip con texto', () => {
      fixture.detectChanges();

      const chip = fila().querySelector('.chip') as HTMLElement;
      expect(chip.textContent?.trim()).toBe('Activo');
      expect(chip.classList).toContain('chip-exito');
    });

    it('un usuario pendiente se muestra con chip de aviso y ofrece «Activar»', () => {
      usuariosServiceMock.consultarUsuarios.mockReturnValue(
        of({ ...listado, contenido: [{ ...usuario, estado: 'PENDIENTE' }] })
      );
      fixture.detectChanges();

      expect(fila().querySelector('.chip')?.classList).toContain('chip-aviso');
      const activar = fila().querySelector('button.boton-principal') as HTMLButtonElement;
      expect(activar.getAttribute('title')).toBe('Activar');
      expect(activar.getAttribute('aria-label')).toBe('Activar a Mario Muñoz');
    });

    it('las acciones son iconos con título y nombre accesible', () => {
      fixture.detectChanges();

      const acciones = Array.from(fila().querySelectorAll('.accion')) as HTMLElement[];
      expect(acciones.map((a) => a.getAttribute('title'))).toEqual(['Ver detalle', 'Modificar', 'Bloquear']);
      expect(acciones.every((a) => a.classList.contains('accion-icono'))).toBe(true);
      expect(acciones.map((a) => a.getAttribute('aria-label'))).toEqual([
        'Ver detalle de Mario Muñoz',
        'Modificar a Mario Muñoz',
        'Bloquear a Mario Muñoz'
      ]);
    });

    it('sin resultados con filtros ofrece «Limpiar filtros»', () => {
      usuariosServiceMock.consultarUsuarios.mockReturnValue(of({ ...listado, contenido: [] }));
      fixture.detectChanges();
      component.busqueda.set('zzz');
      component.buscar();
      fixture.detectChanges();

      const boton = (fixture.nativeElement as HTMLElement).querySelector('.estado button') as HTMLButtonElement;
      expect(boton.textContent?.trim()).toBe('Limpiar filtros');
    });
  });
  describe('errores de operación (M8)', () => {
    it('un error al bloquear se muestra como aviso sin ocultar el listado', async () => {
      simularConfirmacion(true);
      usuariosServiceMock.bloquearUsuario.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 404 }))
      );
      fixture.detectChanges();

      await component.bloquear(usuario);
      fixture.detectChanges();

      const vista = fixture.nativeElement as HTMLElement;
      expect(vista.querySelector('.aviso-error')?.textContent).toContain('El usuario ya no existe.');
      expect(vista.querySelectorAll('tbody tr').length).toBe(1);
    });

    it('pide la confirmación con el diálogo común y marca bloquear como acción peligrosa', async () => {
      const confirmar = simularConfirmacion(false);
      fixture.detectChanges();

      await component.bloquear(usuario);

      expect(confirmar).toHaveBeenCalledWith(
        expect.objectContaining({ titulo: 'Bloquear usuario', peligro: true, aceptar: 'Bloquear' })
      );
    });

    it('un error al cargar el listado se muestra como estado, no como aviso', () => {
      usuariosServiceMock.consultarUsuarios.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 500 }))
      );
      fixture.detectChanges();

      const vista = fixture.nativeElement as HTMLElement;
      expect(vista.querySelector('.estado[role="alert"]')).not.toBeNull();
      expect(vista.querySelector('.aviso-error')).toBeNull();
    });
  });
  it('si falla la carga ofrece «Reintentar» y vuelve a consultar (M5)', () => {
    usuariosServiceMock.consultarUsuarios.mockReturnValueOnce(
      throwError(() => new HttpErrorResponse({ status: 500 }))
    );
    fixture.detectChanges();
    const vista = fixture.nativeElement as HTMLElement;

    const reintentar = vista.querySelector('.estado[role="alert"] .boton-reintentar') as HTMLButtonElement;
    expect(reintentar.textContent?.trim()).toBe('Reintentar');
    reintentar.click();
    fixture.detectChanges();

    expect(usuariosServiceMock.consultarUsuarios).toHaveBeenCalledTimes(2);
    expect(vista.querySelectorAll('tbody tr').length).toBe(1);
  });
});