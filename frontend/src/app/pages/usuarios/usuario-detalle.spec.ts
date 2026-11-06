import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AdministradorService } from '../admin/administrador.service';
import { UsuarioDetalle } from './usuario-detalle';
import { UsuarioAdministracion, UsuariosService } from './usuarios.service';

describe('UsuarioDetalle (UI-admin)', () => {
  let fixture: ComponentFixture<UsuarioDetalle>;

  const base: UsuarioAdministracion = {
    id: 'u1', nombre: 'Mario', apellidos: 'Muñoz', email: 'mario@example.com', telefono: '600111222',
    estado: 'ACTIVO', rol: 'CLIENTE', imagenPerfil: null, sede: null, fechaIncorporacion: null, esPremium: true,
    nombreComercial: null, dniNif: '12345678Z', fechaNacimiento: '2003-11-20', categoriaId: null
  };

  const servicio = { consultarUsuario: vi.fn(), modificarUsuario: vi.fn() };

  function crear(usuario: UsuarioAdministracion | null, editar = false): HTMLElement {
    if (usuario) {
      servicio.consultarUsuario.mockReturnValue(of(usuario));
    } else {
      servicio.consultarUsuario.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    }
    TestBed.configureTestingModule({
      imports: [UsuarioDetalle],
      providers: [
        provideRouter([]),
        { provide: UsuariosService, useValue: servicio },
        { provide: AdministradorService, useValue: {} },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ id: 'u1' }),
              queryParamMap: convertToParamMap(editar ? { editar: 'true' } : {})
            }
          }
        }
      ]
    });
    fixture = TestBed.createComponent(UsuarioDetalle);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  afterEach(() => {
    vi.clearAllMocks();
    TestBed.resetTestingModule();
  });

  it('muestra el estado de la cuenta una sola vez y como chip con texto', () => {
    const vista = crear({ ...base, estado: 'BLOQUEADO' });

    const chip = vista.querySelector('.status-row .chip') as HTMLElement;
    expect(chip.textContent?.trim()).toBe('Bloqueado');
    expect(chip.classList).toContain('chip-peligro');
    expect(Array.from(vista.querySelectorAll('dt')).map((dt) => dt.textContent?.trim())).not.toContain('Estado');
  });

  it('muestra las fechas como dd/MM/aaaa', () => {
    const vista = crear(base);

    expect(vista.querySelector('dl')?.textContent).toContain('20/11/2003');
    expect(vista.querySelector('dl')?.textContent).not.toContain('2003-11-20');
  });

  it('un único h1 con el nombre y «Modificar usuario» como acción principal', () => {
    const vista = crear(base);

    expect(vista.querySelectorAll('h1').length).toBe(1);
    expect(vista.querySelector('h1')?.textContent).toContain('Mario');
    expect(vista.querySelector('.header-actions .boton-principal')?.textContent?.trim()).toBe('Modificar usuario');
    expect(vista.querySelector('.topbar')).toBeNull();
  });

  it('vuelve al listado de usuarios o al de administradores según el rol', () => {
    const vistaCliente = crear(base);
    expect(vistaCliente.querySelector('a.volver')?.getAttribute('href')).toBe('/admin/usuarios');
    TestBed.resetTestingModule();

    const vistaAdmin = crear({ ...base, rol: 'ADMIN', sede: 'Central', fechaIncorporacion: '2026-01-01' });
    expect(vistaAdmin.querySelector('a.volver')?.getAttribute('href')).toBe('/admin/administradores');
    expect(vistaAdmin.querySelector('dl')?.textContent).toContain('01/01/2026');
  });

  it('con ?editar=true abre el formulario con el email protegido y acciones en botones comunes', () => {
    const vista = crear(base, true);

    expect(vista.querySelector('form.edit-form')).not.toBeNull();
    expect((vista.querySelector('#email') as HTMLInputElement).disabled).toBe(true);
    expect(vista.querySelector('.form-actions .boton-secundario')?.textContent?.trim()).toBe('Cancelar');
    expect(vista.querySelector('.form-actions .boton-principal')?.textContent?.trim()).toBe('Guardar cambios');
  });

  it('si falla la carga muestra el error como estado accesible', () => {
    const vista = crear(null);

    expect(vista.querySelector('.estado[role="alert"]')).not.toBeNull();
  });
});
