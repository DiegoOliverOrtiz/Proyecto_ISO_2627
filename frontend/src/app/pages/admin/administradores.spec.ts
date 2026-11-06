import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthService } from '../login/auth.service';
import { ListadoUsuarios, UsuarioAdministracion, UsuariosService } from '../usuarios/usuarios.service';
import { AdministradorService } from './administrador.service';
import { Administradores } from './administradores';

describe('Administradores (UI-admin)', () => {
  let fixture: ComponentFixture<Administradores>;

  const admin = (id: string, nombre: string, email: string, estado: string): UsuarioAdministracion => ({
    id, nombre, apellidos: 'Admin', email, telefono: null, estado, rol: 'ADMIN', imagenPerfil: null, sede: null,
    fechaIncorporacion: null, esPremium: null, nombreComercial: null, dniNif: null, fechaNacimiento: null, categoriaId: null
  } as UsuarioAdministracion);

  const listado: ListadoUsuarios = {
    contenido: [
      admin('a1', 'Ada', 'ada@esibuy.com', 'ACTIVO'),
      admin('a2', 'Bruno', 'bruno@esibuy.com', 'BLOQUEADO'),
      admin('a3', 'Carla', 'carla@esibuy.com', 'ACTIVO')
    ],
    pagina: 0, tamano: 20, totalElementos: 3, totalPaginas: 1,
    estadisticas: { totalUsuarios: 3, activos: 2, pendientes: 0, desactivados: 1 }
  };

  const usuariosService = { consultarUsuarios: vi.fn() };

  beforeEach(async () => {
    usuariosService.consultarUsuarios.mockReturnValue(of(listado));
    await TestBed.configureTestingModule({
      imports: [Administradores],
      providers: [
        provideRouter([]),
        { provide: UsuariosService, useValue: usuariosService },
        { provide: AdministradorService, useValue: {} },
        { provide: AuthService, useValue: { obtenerSesionActual: () => of({ email: 'ada@esibuy.com', role: 'ADMIN' }) } }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(Administradores);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.clearAllMocks();
    TestBed.resetTestingModule();
  });

  const filas = () => Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('tbody tr'));
  const botones = (fila: Element) => Array.from(fila.querySelectorAll('.user-actions .accion')).map((b) => b.textContent?.trim());

  it('muestra el estado como texto en un chip, no solo como un icono', () => {
    const chips = filas().map((f) => f.querySelector('.chip')?.textContent?.trim());

    expect(chips[0]).toBe('Activo');
    expect(chips[1]).toBe('Bloqueado');
    expect(filas()[1].querySelector('.chip')?.classList).toContain('chip-peligro');
  });

  it('las acciones son botones con texto y 44 px, con el nombre accesible de la persona', () => {
    const bloquear = filas()[2].querySelector('button.boton-peligro') as HTMLButtonElement;

    expect(bloquear.textContent?.trim()).toBe('Bloquear');
    expect(bloquear.getAttribute('aria-label')).toBe('Bloquear a Carla Admin');
    expect(bloquear.classList).toContain('accion');
  });

  it('en la fila propia aparece «Tú» y no se ofrece bloquear', () => {
    const propia = filas()[0];

    expect(propia.querySelector('.current-admin-label')?.textContent?.trim()).toBe('Tú');
    expect(botones(propia)).toEqual(['Ver detalle', 'Eliminar']);
  });

  it('un administrador bloqueado ofrece «Desbloquear»', () => {
    expect(botones(filas()[1])).toEqual(['Ver detalle', 'Desbloquear', 'Eliminar']);
  });

  it('«Añadir administrador» es el botón principal de la cabecera y hay un único h1', () => {
    const vista = fixture.nativeElement as HTMLElement;

    const anadir = vista.querySelector('.pagina-cabecera a.boton-principal') as HTMLAnchorElement;
    expect(anadir.textContent?.trim()).toBe('Añadir administrador');
    expect(anadir.getAttribute('href')).toBe('/admin/administradores/nuevo');
    expect(vista.querySelectorAll('h1').length).toBe(1);
  });
});
