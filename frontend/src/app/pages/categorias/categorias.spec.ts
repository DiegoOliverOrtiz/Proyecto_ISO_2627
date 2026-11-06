import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { vi } from 'vitest';

import { Categorias } from './categorias';
import { environment } from '../../../environments/environment';

describe('Categorias', () => {
  let component: Categorias;
  let fixture: ComponentFixture<Categorias>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Categorias],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([])
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Categorias);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    httpMock.verify();
  });

  it('should create', () => {
    fixture.detectChanges();

    const request = httpMock.expectOne(
      `${environment.apiUrl}/categorias`
    );

    request.flush([]);

    expect(component).toBeTruthy();
  });

  it('muestra las categorias devueltas por el backend', () => {
    fixture.detectChanges();

    const request = httpMock.expectOne(
      `${environment.apiUrl}/categorias`
    );

    expect(request.request.method).toBe('GET');

    request.flush([
      {
        id: 'categoria-1',
        nombre: 'Deportes',
        descripcion: 'Productos y equipamiento deportivo',
      },
      {
        id: 'categoria-2',
        nombre: 'Electrónica',
        descripcion: 'Productos electrónicos',
      },
    ]);

    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Deportes');
    expect(fixture.nativeElement.textContent).toContain(
      'Productos y equipamiento deportivo'
    );

    expect(fixture.nativeElement.textContent).toContain('Electrónica');
    expect(fixture.nativeElement.textContent).toContain(
      'Productos electrónicos'
    );
  });

  it('muestra un mensaje cuando no existen categorias', () => {
    fixture.detectChanges();

    const request = httpMock.expectOne(
      `${environment.apiUrl}/categorias`
    );

    request.flush([]);

    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'No hay categorías registradas actualmente.'
    );
  });

  it('muestra un mensaje de error si falla la consulta', () => {
    fixture.detectChanges();

    const request = httpMock.expectOne(
      `${environment.apiUrl}/categorias`
    );

    request.flush(
      {
        error: 'Error al consultar categorías',
      },
      {
        status: 500,
        statusText: 'Internal Server Error',
      }
    );

    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'No se pudieron cargar las categorías.'
    );
  });

  it('envia la sesion al consultar categorias', () => {
    fixture.detectChanges();

    const request = httpMock.expectOne(
      `${environment.apiUrl}/categorias`
    );

    expect(request.request.withCredentials).toBe(true);

    request.flush([]);
  });

  it('modifica una categoria y actualiza el listado sin recargarlo', () => {
    fixture.detectChanges();

    const listado = httpMock.expectOne(
      `${environment.apiUrl}/categorias`
    );

    listado.flush([
      {
        id: 'categoria-1',
        nombre: 'Deportes',
        descripcion: 'Productos deportivos',
      },
    ]);

    fixture.detectChanges();

    const botonEditar = fixture.nativeElement.querySelector(
      '[aria-label="Editar categoría Deportes"]'
    ) as HTMLButtonElement;

    botonEditar.click();
    fixture.detectChanges();

    const campoNombre = fixture.nativeElement.querySelector(
      '[formControlName="nombre"]'
    ) as HTMLInputElement;

    const campoDescripcion = fixture.nativeElement.querySelector(
      '[formControlName="descripcion"]'
    ) as HTMLTextAreaElement;

    expect(campoNombre.value).toBe('Deportes');
    expect(campoDescripcion.value).toBe('Productos deportivos');

    campoNombre.value = '  Deportes outdoor  ';
    campoNombre.dispatchEvent(new Event('input'));

    campoDescripcion.value = '  Ropa y material para exterior  ';
    campoDescripcion.dispatchEvent(new Event('input'));

    fixture.detectChanges();

    const botonGuardar = fixture.nativeElement.querySelector(
      'button[type="submit"]'
    ) as HTMLButtonElement;

    botonGuardar.click();

    const solicitudCsrf = httpMock.expectOne(
      `${environment.apiUrl}/usuarios/csrf`
    );

    expect(solicitudCsrf.request.method).toBe('GET');
    expect(solicitudCsrf.request.withCredentials).toBe(true);

    solicitudCsrf.flush({ token: 'token-csrf' });

    const solicitudPut = httpMock.expectOne(
      `${environment.apiUrl}/categorias/categoria-1`
    );

    expect(solicitudPut.request.method).toBe('PUT');
    expect(solicitudPut.request.withCredentials).toBe(true);
    expect(
      solicitudPut.request.headers.get('X-XSRF-TOKEN')
    ).toBe('token-csrf');

    expect(solicitudPut.request.body).toEqual({
      nombre: 'Deportes outdoor',
      descripcion: 'Ropa y material para exterior',
    });

    solicitudPut.flush({
      id: 'categoria-1',
      nombre: 'Deportes outdoor',
      descripcion: 'Ropa y material para exterior',
    });

    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'Deportes outdoor'
    );

    expect(fixture.nativeElement.textContent).toContain(
      'Ropa y material para exterior'
    );

    expect(fixture.nativeElement.textContent).toContain(
      'Categoría modificada correctamente.'
    );

    const nombreEnTarjeta = fixture.nativeElement.querySelector(
      '.categoria-card h2'
    ) as HTMLHeadingElement;

    expect(nombreEnTarjeta.textContent).toBe('Deportes outdoor');
  });

  it('muestra el error del backend y conserva el formulario si falla la edición', () => {
    fixture.detectChanges();

    const listado = httpMock.expectOne(
      `${environment.apiUrl}/categorias`
    );

    listado.flush([
      {
        id: 'categoria-1',
        nombre: 'Deportes',
        descripcion: 'Productos deportivos',
      },
    ]);

    fixture.detectChanges();

    const botonEditar = fixture.nativeElement.querySelector(
      '[aria-label="Editar categoría Deportes"]'
    ) as HTMLButtonElement;

    botonEditar.click();
    fixture.detectChanges();

    const campoNombre = fixture.nativeElement.querySelector(
      '[formControlName="nombre"]'
    ) as HTMLInputElement;

    campoNombre.value = 'Deportes duplicados';
    campoNombre.dispatchEvent(new Event('input'));

    fixture.detectChanges();

    const botonGuardar = fixture.nativeElement.querySelector(
      'button[type="submit"]'
    ) as HTMLButtonElement;

    botonGuardar.click();

    const solicitudCsrf = httpMock.expectOne(
      `${environment.apiUrl}/usuarios/csrf`
    );

    solicitudCsrf.flush({ token: 'token-csrf' });

    const solicitudPut = httpMock.expectOne(
      `${environment.apiUrl}/categorias/categoria-1`
    );

    solicitudPut.flush(
      {
        error: 'Ya existe una categoría con ese nombre.'
      },
      {
        status: 409,
        statusText: 'Conflict'
      }
    );

    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'Ya existe una categoría con ese nombre.'
    );

    expect(fixture.nativeElement.textContent).toContain(
      'Modificar categoría'
    );

    expect(campoNombre.value).toBe('Deportes duplicados');

    const nombreEnTarjeta = fixture.nativeElement.querySelector(
      '.categoria-card h2'
    ) as HTMLHeadingElement;

    expect(nombreEnTarjeta.textContent).toBe('Deportes');
  });

  it('elimina una categoria y la quita del listado', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    fixture.detectChanges();

    const listado = httpMock.expectOne(
      `${environment.apiUrl}/categorias`
    );

    listado.flush([
      {
        id: 'categoria-1',
        nombre: 'Deportes',
        descripcion: 'Productos deportivos',
      },
    ]);

    fixture.detectChanges();

    const botonEliminar = fixture.nativeElement.querySelector(
      '[aria-label="Eliminar categoría Deportes"]'
    ) as HTMLButtonElement;

    botonEliminar.click();

    const solicitudCsrf = httpMock.expectOne(
      `${environment.apiUrl}/usuarios/csrf`
    );

    expect(solicitudCsrf.request.method).toBe('GET');

    solicitudCsrf.flush({
      token: 'token-csrf'
    });

    const solicitudDelete = httpMock.expectOne(
      `${environment.apiUrl}/categorias/categoria-1`
    );

    expect(solicitudDelete.request.method).toBe('DELETE');
    expect(solicitudDelete.request.withCredentials).toBe(true);
    expect(
      solicitudDelete.request.headers.get('X-XSRF-TOKEN')
    ).toBe('token-csrf');

    solicitudDelete.flush(null);

    fixture.detectChanges();

    expect(component.categorias().length).toBe(0);

    expect(fixture.nativeElement.textContent).toContain(
      'Categoría eliminada correctamente.'
    );
  });

  it('no elimina la categoria si se cancela la confirmacion', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);

    fixture.detectChanges();

    const listado = httpMock.expectOne(
      `${environment.apiUrl}/categorias`
    );

    listado.flush([
      {
        id: 'categoria-1',
        nombre: 'Deportes',
        descripcion: 'Productos deportivos',
      },
    ]);

    fixture.detectChanges();

    const botonEliminar = fixture.nativeElement.querySelector(
      '[aria-label="Eliminar categoría Deportes"]'
    ) as HTMLButtonElement;

    botonEliminar.click();

    expect(window.confirm).toHaveBeenCalled();

    fixture.detectChanges();

    expect(component.categorias().length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('Deportes');
  });

  it('muestra el error del backend si la categoria tiene productos asociados', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    fixture.detectChanges();

    const listado = httpMock.expectOne(
      `${environment.apiUrl}/categorias`
    );

    listado.flush([
      {
        id: 'categoria-1',
        nombre: 'Deportes',
        descripcion: 'Productos deportivos',
      },
    ]);

    fixture.detectChanges();

    const botonEliminar = fixture.nativeElement.querySelector(
      '[aria-label="Eliminar categoría Deportes"]'
    ) as HTMLButtonElement;

    botonEliminar.click();

    const solicitudCsrf = httpMock.expectOne(
      `${environment.apiUrl}/usuarios/csrf`
    );

    solicitudCsrf.flush({
      token: 'token-csrf'
    });

    const solicitudDelete = httpMock.expectOne(
      `${environment.apiUrl}/categorias/categoria-1`
    );

    expect(solicitudDelete.request.method).toBe('DELETE');

    solicitudDelete.flush(
      {
        error: 'No se puede eliminar una categoría con productos asociados.',
      },
      {
        status: 409,
        statusText: 'Conflict',
      }
    );

    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'No se puede eliminar una categoría con productos asociados.'
    );

    expect(component.categorias().length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('Deportes');
  });
  describe('presentación (M17, A20)', () => {
    function cargar(categorias: unknown[]): HTMLElement {
      fixture.detectChanges();
      httpMock.expectOne(`${environment.apiUrl}/categorias`).flush(categorias);
      fixture.detectChanges();
      return fixture.nativeElement as HTMLElement;
    }

    it('sin resultados con filtros muestra el mensaje completo y «Limpiar filtros» junto a él (M17)', () => {
      const vista = cargar([]);
      component.filtros.patchValue({ nombre: 'zzz' });
      component.aplicarFiltros();
      httpMock.expectOne(`${environment.apiUrl}/categorias?nombre=zzz`).flush([]);
      fixture.detectChanges();

      const estado = vista.querySelector('.estado') as HTMLElement;
      expect(estado.textContent).toContain('No se encontraron categorías con los filtros indicados.');
      expect(estado.querySelector('button')?.textContent?.trim()).toBe('Limpiar filtros');
    });

    it('sin categorías invita a crear la primera', () => {
      const vista = cargar([]);

      expect(vista.querySelector('.estado')?.textContent).toContain('Nueva categoría');
    });

    it('muestra cada categoría como tarjeta con «Editar» y «Eliminar» de contorno rojo', () => {
      const vista = cargar([{ id: 'c1', nombre: 'Hogar', descripcion: 'Cosas de casa' }]);

      const tarjeta = vista.querySelector('.categoria-card') as HTMLElement;
      expect(tarjeta.querySelector('h2')?.textContent?.trim()).toBe('Hogar');
      expect(tarjeta.querySelector('.boton-peligro')?.getAttribute('aria-label')).toBe('Eliminar categoría Hogar');
      expect(vista.querySelectorAll('h1').length).toBe(1);
    });

    it('al abrir el formulario, el foco entra en su título y respeta prefers-reduced-motion', async () => {
      const vista = cargar([]);
      document.body.appendChild(vista);
      const desplazar = vi.fn();
      (HTMLElement.prototype as { scrollIntoView?: unknown }).scrollIntoView = desplazar;
      vi.stubGlobal('matchMedia', () => ({ matches: true }));

      component.abrirFormulario();
      fixture.detectChanges();
      await new Promise<void>((resolver) => setTimeout(resolver));

      expect(document.activeElement?.id).toBe('titulo-formulario');
      expect(desplazar).toHaveBeenCalledWith(expect.objectContaining({ behavior: 'auto', block: 'start' }));
      delete (HTMLElement.prototype as { scrollIntoView?: unknown }).scrollIntoView;
      vi.unstubAllGlobals();
      vista.remove();
    });
  });
});