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
    modificarProducto: vi.fn(),
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

  describe('modificar producto (HU18)', () => {
    const productoConDatos: ProductoPropio = {
      ...productoActivo,
      descripcion: 'Teclado compacto',
      descuento: 5,
      descuentoPremium: 10,
      imagen: '/product-images/teclado.png'
    };

    function botonesModificar(): HTMLButtonElement[] {
      return Array.from(fixture.nativeElement.querySelectorAll('.acciones-producto button.boton-secundario'));
    }

    beforeEach(() => {
      productoServiceMock.modificarProducto.mockReturnValue(of(productoConDatos));
      productoServiceMock.urlImagen.mockImplementation((imagen: string) => `http://api${imagen}`);
    });

    it('muestra la acción Modificar en cada producto del listado', () => {
      expect(botonesModificar().map((boton) => boton.textContent?.trim())).toEqual([
        'Modificar',
        'Modificar'
      ]);
    });

    it('abre el formulario con los valores actuales del producto y sin el selector de visibilidad', () => {
      component.editarProducto(productoConDatos);
      fixture.detectChanges();

      expect(component.mostrandoFormulario()).toBe(true);
      expect(component.formularioProducto.getRawValue()).toEqual({
        nombre: 'Teclado',
        categoria: 'Informática',
        descripcion: 'Teclado compacto',
        esVisible: true,
        precio: 49.99,
        stock: 4,
        descuento: 5,
        descuentoPremium: 10
      });
      const formulario: HTMLElement = fixture.nativeElement.querySelector('form');
      expect(formulario.getAttribute('aria-label')).toBe('Modificar producto');
      expect(formulario.textContent).not.toContain('Visibilidad del producto');
      expect(formulario.textContent).toContain('Guardar cambios');
      expect(formulario.querySelector('img')?.getAttribute('src')).toBe(
        'http://api/product-images/teclado.png'
      );
    });

    it('envía solo los campos modificables, informa del resultado y recarga el catálogo', () => {
      component.editarProducto(productoConDatos);
      component.formularioProducto.patchValue({ precio: 39.99, stock: 0 });

      component.guardarProducto();
      fixture.detectChanges();

      expect(productoServiceMock.modificarProducto).toHaveBeenCalledWith(
        'producto-1',
        {
          nombre: 'Teclado',
          categoria: 'Informática',
          descripcion: 'Teclado compacto',
          precio: 39.99,
          stock: 0,
          descuento: 5,
          descuentoPremium: 10
        },
        null
      );
      expect(productoServiceMock.crearProducto).not.toHaveBeenCalled();
      expect(component.mensajeExito()).toBe('Producto modificado correctamente.');
      expect(component.mostrandoFormulario()).toBe(false);
      expect(component.productoEnEdicion()).toBeNull();
      expect(productoServiceMock.consultarProductosPropios).toHaveBeenCalledTimes(2);
    });

    it('no envía la petición si el precio no es mayor que 0', () => {
      component.editarProducto(productoConDatos);
      component.formularioProducto.patchValue({ precio: 0 });

      component.guardarProducto();

      expect(productoServiceMock.modificarProducto).not.toHaveBeenCalled();
      expect(component.mensajeError()).toBe(
        'Completa todos los campos obligatorios y corrige los valores indicados.'
      );
    });

    it('no envía la petición si el stock es negativo o el descuento supera 100', () => {
      component.editarProducto(productoConDatos);

      component.formularioProducto.patchValue({ stock: -1 });
      component.guardarProducto();
      component.formularioProducto.patchValue({ stock: 1, descuento: 101 });
      component.guardarProducto();

      expect(productoServiceMock.modificarProducto).not.toHaveBeenCalled();
    });

    it('muestra el motivo devuelto por el backend si la modificación falla', () => {
      productoServiceMock.modificarProducto.mockReturnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 404,
              error: { error: 'No se ha encontrado el producto solicitado.' }
            })
        )
      );
      component.editarProducto(productoConDatos);

      component.guardarProducto();

      expect(component.mensajeError()).toBe('No se ha encontrado el producto solicitado.');
      expect(component.mensajeExito()).toBe('');
      expect(component.mostrandoFormulario()).toBe(true);
      expect(component.enviandoProducto()).toBe(false);
    });

    it('al cancelar sale del modo edición y el formulario vuelve a ser de creación', () => {
      component.editarProducto(productoConDatos);
      component.alternarFormulario();
      component.alternarFormulario();
      fixture.detectChanges();

      expect(component.productoEnEdicion()).toBeNull();
      expect(component.formularioProducto.getRawValue().nombre).toBe('');
      expect(fixture.nativeElement.querySelector('form').textContent).toContain('Crear producto');
    });
  });
});
