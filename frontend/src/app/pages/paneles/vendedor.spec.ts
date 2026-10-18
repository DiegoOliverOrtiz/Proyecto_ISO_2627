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

  describe('búsqueda y filtros del catálogo (HU15)', () => {
    const teclado: ProductoPropio = { ...productoActivo, nombre: 'Teclado Mecánico' };
    const raton: ProductoPropio = {
      ...productoActivo,
      id: 'producto-2',
      nombre: 'Ratón inalámbrico',
      esVisible: false
    };
    const silla: ProductoPropio = {
      ...productoActivo,
      id: 'producto-3',
      nombre: 'Silla Gaming',
      categoria: 'Hogar'
    };
    const literal: ProductoPropio = {
      ...productoActivo,
      id: 'producto-4',
      nombre: 'Cable (USB) .* $where',
      categoria: 'Hogar'
    };
    const catalogo = [teclado, raton, silla, literal];

    function nombresMostrados(): string[] {
      return Array.from(
        fixture.nativeElement.querySelectorAll('ul[aria-label="Mis productos"] li h2')
      ).map((titulo) => (titulo as HTMLElement).textContent?.trim() ?? '');
    }

    function buscar(texto: string): void {
      const entrada = fixture.nativeElement.querySelector('#busqueda-producto') as HTMLInputElement;
      entrada.value = texto;
      entrada.dispatchEvent(new Event('input'));
      fixture.detectChanges();
    }

    function elegir(idSelect: string, valor: string): void {
      const select = fixture.nativeElement.querySelector(idSelect) as HTMLSelectElement;
      select.value = valor;
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();
    }

    beforeEach(() => {
      productoServiceMock.consultarProductosPropios.mockReturnValue(of(catalogo));
      component.cargarProductos();
      fixture.detectChanges();
    });

    it('muestra todo el catálogo cuando no hay búsqueda ni filtros', () => {
      expect(nombresMostrados()).toHaveLength(4);
    });

    it('busca por nombre parcial', () => {
      buscar('tecl');

      expect(nombresMostrados()).toEqual(['Teclado Mecánico']);
    });

    it('la búsqueda no distingue mayúsculas de minúsculas', () => {
      buscar('SILLA');
      expect(nombresMostrados()).toEqual(['Silla Gaming']);

      buscar('silla');
      expect(nombresMostrados()).toEqual(['Silla Gaming']);
    });

    it('ignora los espacios al principio y al final del texto buscado', () => {
      buscar('  ratón  ');

      expect(nombresMostrados()).toEqual(['Ratón inalámbrico']);
    });

    it('lista las categorías presentes en el catálogo, sin repetir', () => {
      expect(component.categoriasDisponibles()).toEqual(['Hogar', 'Informática']);
    });

    it('filtra por categoría', () => {
      elegir('#filtro-categoria', 'Hogar');

      expect(nombresMostrados()).toEqual(['Silla Gaming', 'Cable (USB) .* $where']);
    });

    it('filtra por estado activo y desactivado', () => {
      elegir('#filtro-estado', 'desactivados');
      expect(nombresMostrados()).toEqual(['Ratón inalámbrico']);

      elegir('#filtro-estado', 'activos');
      expect(nombresMostrados()).toEqual([
        'Teclado Mecánico',
        'Silla Gaming',
        'Cable (USB) .* $where'
      ]);
    });

    it('combina búsqueda, categoría y estado', () => {
      elegir('#filtro-categoria', 'Informática');
      elegir('#filtro-estado', 'activos');
      buscar('ra');
      expect(nombresMostrados()).toEqual([]);

      buscar('tec');
      expect(nombresMostrados()).toEqual(['Teclado Mecánico']);
    });

    it('informa claramente cuando no hay coincidencias', () => {
      buscar('inexistente');

      const texto = fixture.nativeElement.textContent as string;
      expect(nombresMostrados()).toEqual([]);
      expect(texto).toContain(
        'No se encontraron productos que coincidan con la búsqueda o los filtros.'
      );
      expect(texto).not.toContain('Aún no tienes productos registrados.');
    });

    it('limpiar los filtros restaura el listado completo', () => {
      buscar('tecl');
      elegir('#filtro-estado', 'activos');
      expect(component.hayFiltrosActivos()).toBe(true);

      component.limpiarFiltros();
      fixture.detectChanges();

      expect(component.hayFiltrosActivos()).toBe(false);
      expect(nombresMostrados()).toHaveLength(4);
    });

    it('borrar el texto de búsqueda restaura el listado completo', () => {
      buscar('tecl');
      buscar('');

      expect(nombresMostrados()).toHaveLength(4);
    });

    it.each(['.*', '(', '[', '\\', '$where', '{"$ne": ""}', '^', '$'])(
      'trata %s como texto literal: no devuelve el catálogo entero ni falla',
      (texto) => {
        expect(() => buscar(texto)).not.toThrow();

        const esperados = catalogo
          .map((producto) => producto.nombre)
          .filter((nombre) => nombre.toLowerCase().includes(texto.toLowerCase()));
        expect(nombresMostrados()).toEqual(esperados);
        expect(nombresMostrados().length).toBeLessThan(catalogo.length);
      }
    );

    it('encuentra un nombre que contiene caracteres especiales de forma literal', () => {
      buscar('(usb) .* $where');

      expect(nombresMostrados()).toEqual(['Cable (USB) .* $where']);
    });

    it('no envía el texto de búsqueda al backend', () => {
      productoServiceMock.consultarProductosPropios.mockClear();

      buscar('{"$ne": ""}');

      expect(productoServiceMock.consultarProductosPropios).not.toHaveBeenCalled();
    });

    it('mantiene las acciones Modificar y Activar/Desactivar asociadas a cada producto filtrado', () => {
      buscar('ratón');

      const acciones = Array.from(
        fixture.nativeElement.querySelectorAll('ul[aria-label="Mis productos"] .acciones-producto button')
      ).map((boton) => (boton as HTMLButtonElement).getAttribute('aria-label'));
      expect(acciones).toEqual(['Modificar Ratón inalámbrico', 'Activar Ratón inalámbrico']);
    });

    it('activar un producto desde un listado filtrado actúa sobre ese producto y conserva el filtro', () => {
      buscar('ratón');

      (fixture.nativeElement.querySelector('button.boton-estado') as HTMLButtonElement).click();
      fixture.detectChanges();

      expect(productoServiceMock.activarProducto).toHaveBeenCalledWith('producto-2');
      expect(component.terminoBusqueda()).toBe('ratón');
      expect(nombresMostrados()).toEqual(['Ratón inalámbrico']);
    });

    it('modificar un producto desde un listado filtrado abre el formulario con ese producto', () => {
      buscar('silla');

      (
        fixture.nativeElement.querySelector(
          '.acciones-producto button.boton-secundario'
        ) as HTMLButtonElement
      ).click();
      fixture.detectChanges();

      expect(component.productoEnEdicion()?.id).toBe('producto-3');
    });
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

    it('oculta el botón superior mientras el formulario está abierto (solo queda Cancelar junto a Guardar)', () => {
      const botonSuperior = () => fixture.nativeElement.querySelector('button.añadirProductos');
      expect(botonSuperior()?.textContent?.trim()).toBe('Añadir Producto');

      component.editarProducto(productoConDatos);
      fixture.detectChanges();

      expect(botonSuperior()).toBeNull();
      const cancelar = Array.from(
        fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>
      ).filter((boton) => boton.textContent?.trim() === 'Cancelar');
      expect(cancelar).toHaveLength(1);
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

  describe('crear producto: imagen rechazada (HU17)', () => {
    const imagen = new File([new Uint8Array([1, 2, 3])], 'falsa.png', { type: 'image/png' });

    function prepararCreacion(): void {
      component.alternarFormulario();
      component.formularioProducto.patchValue({
        nombre: 'Teclado',
        categoria: 'Informática',
        descripcion: 'Teclado compacto',
        precio: 49.99,
        stock: 3
      });
      component.imagenSeleccionada.set(imagen);
    }

    it('muestra junto al campo el motivo del rechazo de la imagen devuelto por el backend', () => {
      productoServiceMock.crearProducto.mockReturnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 400,
              error: { error: 'La imagen debe ser PNG, JPG o GIF y no superar 5 MB.' }
            })
        )
      );
      prepararCreacion();

      component.guardarProducto();
      fixture.detectChanges();

      expect(component.errorImagen()).toBe('La imagen debe ser PNG, JPG o GIF y no superar 5 MB.');
      expect(component.mensajeError()).toContain('La imagen del producto no es válida');
      expect(component.mostrandoFormulario()).toBe(true);
      expect(component.enviandoProducto()).toBe(false);
      expect(fixture.nativeElement.querySelector('.campo-imagen').textContent).toContain(
        'no superar 5 MB'
      );
    });

    it('usa el mensaje genérico si el 400 no se debe a la imagen', () => {
      productoServiceMock.crearProducto.mockReturnValue(
        throwError(
          () => new HttpErrorResponse({ status: 400, error: { error: 'Los datos del producto no son válidos.' } })
        )
      );
      prepararCreacion();

      component.guardarProducto();

      expect(component.errorImagen()).toBe('');
      expect(component.mensajeError()).toBe(
        'No se pudo crear el producto. Revisa los datos e inténtalo otra vez.'
      );
    });

    it('limpia el error de imagen al cancelar el formulario', () => {
      productoServiceMock.crearProducto.mockReturnValue(
        throwError(
          () => new HttpErrorResponse({ status: 400, error: { error: 'La imagen debe ser PNG, JPG o GIF y no superar 5 MB.' } })
        )
      );
      prepararCreacion();
      component.guardarProducto();

      component.alternarFormulario();

      expect(component.errorImagen()).toBe('');
    });
  });

  describe('crear producto: solo premium y stock (HU17)', () => {
    function rellenarFormulario(valores: Record<string, unknown> = {}): void {
      component.formularioProducto.patchValue({
        nombre: 'Teclado',
        categoria: 'Informática',
        descripcion: 'Teclado compacto',
        precio: 49.99,
        stock: 3,
        descuentoPremium: 15,
        ...valores
      });
    }

    function casilla(): HTMLInputElement | null {
      return fixture.nativeElement.querySelector('#solo-premium-producto');
    }

    beforeEach(() => {
      productoServiceMock.crearProducto.mockReturnValue(of({}));
    });

    it('muestra la casilla «solo premium» al crear, sin marcar por defecto', () => {
      component.alternarFormulario();
      fixture.detectChanges();

      expect(casilla()).not.toBeNull();
      expect(casilla()?.checked).toBe(false);
    });

    it('envía soloPremium y el descuento premium al crear', () => {
      component.alternarFormulario();
      rellenarFormulario();
      component.soloPremium.setValue(true);

      component.guardarProducto();

      expect(productoServiceMock.crearProducto).toHaveBeenCalledWith(
        expect.objectContaining({ soloPremium: true, descuentoPremium: 15, stock: 3 }),
        null
      );
      expect(component.mensajeExito()).toBe('Producto creado correctamente.');
    });

    it('envía soloPremium false si no se marca la casilla', () => {
      component.alternarFormulario();
      rellenarFormulario();

      component.guardarProducto();

      expect(productoServiceMock.crearProducto).toHaveBeenCalledWith(
        expect.objectContaining({ soloPremium: false }),
        null
      );
    });

    it('reinicia la casilla tras crear el producto', () => {
      component.alternarFormulario();
      rellenarFormulario();
      component.soloPremium.setValue(true);

      component.guardarProducto();

      expect(component.soloPremium.value).toBe(false);
    });

    it('no envía la petición con stock 0 o negativo al crear', () => {
      component.alternarFormulario();
      rellenarFormulario({ stock: 0 });
      component.guardarProducto();
      rellenarFormulario({ stock: -1 });
      component.guardarProducto();

      expect(productoServiceMock.crearProducto).not.toHaveBeenCalled();
    });

    it('rechaza descuentos premium de 101 o negativos y acepta 0 y 100', () => {
      component.alternarFormulario();

      rellenarFormulario({ descuentoPremium: 101 });
      expect(component.formularioProducto.valid).toBe(false);
      rellenarFormulario({ descuentoPremium: -1 });
      expect(component.formularioProducto.valid).toBe(false);
      rellenarFormulario({ descuentoPremium: 0 });
      expect(component.formularioProducto.valid).toBe(true);
      rellenarFormulario({ descuentoPremium: 100 });
      expect(component.formularioProducto.valid).toBe(true);
    });

    it('al modificar oculta la casilla y se admite stock 0', () => {
      component.editarProducto(productoActivo);
      fixture.detectChanges();
      component.formularioProducto.patchValue({ stock: 0 });

      expect(casilla()).toBeNull();
      expect(component.formularioProducto.controls.stock.valid).toBe(true);
    });

    it('al cancelar la edición vuelve a exigir stock mayor que 0', () => {
      component.editarProducto(productoActivo);
      component.alternarFormulario();
      component.alternarFormulario();
      component.formularioProducto.patchValue({ stock: 0 });

      expect(component.formularioProducto.controls.stock.valid).toBe(false);
    });
  });
});
