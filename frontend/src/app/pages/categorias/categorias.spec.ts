import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';

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
    expect(solicitudPut.request.headers.get('X-XSRF-TOKEN'))
      .toBe('token-csrf');
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

    expect(fixture.nativeElement.textContent).toContain('Deportes outdoor');
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
      { error: 'Ya existe una categoría con ese nombre.' },
      { status: 409, statusText: 'Conflict' }
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'Ya existe una categoría con ese nombre.'
    );
    expect(fixture.nativeElement.textContent).toContain('Modificar categoría');
    expect(campoNombre.value).toBe('Deportes duplicados');
    const nombreEnTarjeta = fixture.nativeElement.querySelector(
      '.categoria-card h2'
    ) as HTMLHeadingElement;
    expect(nombreEnTarjeta.textContent).toBe('Deportes');
  });
});