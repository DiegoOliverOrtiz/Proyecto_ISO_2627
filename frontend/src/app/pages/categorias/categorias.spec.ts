import { ComponentFixture, TestBed } from '@angular/core/testing';
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
});