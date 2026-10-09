import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthService } from '../login/auth.service';
import { CategoriasService } from '../categorias/categorias.service';
import { ProductoPropio, ProductoService } from './producto.service';
import { PanelVendedor } from './vendedor';
import { VendedorService } from './vendedor.service';

describe('PanelVendedor - activar/desactivar producto', () => {
  let component: PanelVendedor;
  let fixture: ComponentFixture<PanelVendedor>;

  const productoActivo: ProductoPropio = {
    id: 'producto-1',
    nombre: 'Teclado',
    categoria: 'Informática',
    stock: 4,
    descripcion: null,
    precio: 49.99,
    descuento: 0,
    descuentoPremium: 0,
    esVisible: true,
    imagen: null
  };

  const productoDesactivado: ProductoPropio = {
    ...productoActivo,
    id: 'producto-2',
    nombre: 'Ratón',
    esVisible: false
  };

  const productoServiceMock = {
    consultarProductosPropios: vi.fn(),
    activarProducto: vi.fn(),
    desactivarProducto: vi.fn(),
    crearProducto: vi.fn(),
    urlImagen: vi.fn()
  };

  beforeEach(async () => {
    productoServiceMock.consultarProductosPropios.mockReturnValue(
      of([productoActivo, productoDesactivado])
    );
    productoServiceMock.activarProducto.mockReturnValue(of(void 0));
    productoServiceMock.desactivarProducto.mockReturnValue(of(void 0));

    await TestBed.configureTestingModule({
      imports: [PanelVendedor],
      providers: [
        provideRouter([]),
        { provide: ProductoService, useValue: productoServiceMock },
        { provide: CategoriasService, useValue: { obtenerCategorias: () => of([]) } },
        {
          provide: VendedorService,
          useValue: { obtenerPerfil: () => of({ nombreComercial: 'Tienda de prueba' }) }
        },
        { provide: AuthService, useValue: { cerrarSesion: () => of(void 0) } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(PanelVendedor);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.clearAllMocks();
    TestBed.resetTestingModule();
  });

  function botonesEstado(): HTMLButtonElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('button.boton-estado'));
  }

  it('muestra el estado y la acción correspondiente de cada producto', () => {
    const texto = fixture.nativeElement.textContent as string;
    const botones = botonesEstado();

    expect(texto).toContain('Estado: Activo');
    expect(texto).toContain('Estado: Desactivado');
    expect(botones.map((boton) => boton.textContent?.trim())).toEqual(['Desactivar', 'Activar']);
  });

  it('desactiva un producto activo, informa del resultado y recarga el listado', () => {
    botonesEstado()[0].click();
    fixture.detectChanges();

    expect(productoServiceMock.desactivarProducto).toHaveBeenCalledWith('producto-1');
    expect(productoServiceMock.activarProducto).not.toHaveBeenCalled();
    expect(component.mensajeExito()).toBe('Producto desactivado correctamente.');
    expect(productoServiceMock.consultarProductosPropios).toHaveBeenCalledTimes(2);
  });

  it('activa un producto desactivado e informa del resultado', () => {
    botonesEstado()[1].click();
    fixture.detectChanges();

    expect(productoServiceMock.activarProducto).toHaveBeenCalledWith('producto-2');
    expect(component.mensajeExito()).toBe('Producto activado correctamente.');
  });

  it('muestra el mensaje del backend cuando el estado ya estaba aplicado', () => {
    productoServiceMock.desactivarProducto.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { error: 'El producto ya está desactivado.' }
          })
      )
    );

    component.cambiarEstadoProducto(productoActivo);
    fixture.detectChanges();

    expect(component.mensajeError()).toBe('El producto ya está desactivado.');
    expect(component.mensajeExito()).toBe('');
    expect(component.productoCambiandoEstado()).toBeNull();
  });

  it('muestra un mensaje de conexión si el servicio no responde', () => {
    productoServiceMock.activarProducto.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 0 }))
    );

    component.cambiarEstadoProducto(productoDesactivado);

    expect(component.mensajeError()).toBe(
      'No se pudo conectar con el servicio. Inténtalo de nuevo.'
    );
  });
});
