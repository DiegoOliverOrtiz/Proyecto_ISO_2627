import { registerLocaleData } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { LOCALE_ID } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import localeEs from '@angular/common/locales/es';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthService } from '../login/auth.service';
import { CategoriasService } from '../categorias/categorias.service';
import { ProductoPropio, ProductoService } from './producto.service';
import { PanelVendedor } from './vendedor';
import { VendedorService } from './vendedor.service';
import { ConfirmacionService } from '../../shared/confirmacion/confirmacion.service';

// M8: la confirmación es un diálogo propio (asíncrono); se simula su respuesta.
const simularConfirmacion = (aceptada: boolean) =>
  vi.spyOn(TestBed.inject(ConfirmacionService), 'confirmar').mockResolvedValue(aceptada);
const esperarConfirmacion = () => new Promise<void>((resolver) => setTimeout(resolver));

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
    imagen: null,
    soloPremium: false
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
    eliminarProducto: vi.fn(),
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
    productoServiceMock.eliminarProducto.mockReturnValue(of(void 0));

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
      expect(acciones).toEqual([
        'Modificar Ratón inalámbrico',
        'Activar Ratón inalámbrico',
        'Eliminar Ratón inalámbrico'
      ]);
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
          '.acciones-producto button.boton-modificar'
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
      return Array.from(fixture.nativeElement.querySelectorAll('.acciones-producto button.boton-modificar'));
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
      expect(component.soloPremium.value).toBe(false);
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
          descuentoPremium: 10,
          soloPremium: false
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
      expect(botonSuperior()?.textContent?.trim()).toBe('Añadir producto');

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

  describe('modificar producto: solo premium (HU18)', () => {
    const productoSoloPremium: ProductoPropio = { ...productoActivo, soloPremium: true, descuentoPremium: 25 };

    function casilla(): HTMLInputElement | null {
      return fixture.nativeElement.querySelector('#solo-premium-producto');
    }

    beforeEach(() => {
      productoServiceMock.modificarProducto.mockReturnValue(of(productoSoloPremium));
    });

    it('muestra «Solo para clientes premium» solo en los productos marcados', () => {
      productoServiceMock.consultarProductosPropios.mockReturnValue(
        of([productoSoloPremium, productoDesactivado])
      );
      component.cargarProductos();
      fixture.detectChanges();

      const tarjetas: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('ul li'));
      expect(tarjetas[0].textContent).toContain('Solo para clientes premium');
      expect(tarjetas[1].textContent).not.toContain('Solo para clientes premium');
    });

    it('muestra la casilla precargada con el valor actual al modificar', () => {
      component.editarProducto(productoSoloPremium);
      fixture.detectChanges();

      expect(casilla()).not.toBeNull();
      expect(casilla()?.checked).toBe(true);
      expect(component.soloPremium.value).toBe(true);
    });

    it('precarga la casilla desmarcada si el producto no es solo premium', () => {
      component.editarProducto(productoActivo);
      fixture.detectChanges();

      expect(casilla()?.checked).toBe(false);
    });

    it('envía la nueva marca y conserva el resto de campos', () => {
      component.editarProducto(productoActivo);
      component.soloPremium.setValue(true);
      component.formularioProducto.patchValue({ descuentoPremium: 30 });

      component.guardarProducto();

      expect(productoServiceMock.modificarProducto).toHaveBeenCalledWith(
        'producto-1',
        {
          nombre: 'Teclado',
          categoria: 'Informática',
          descripcion: '',
          precio: 49.99,
          stock: 4,
          descuento: 0,
          descuentoPremium: 30,
          soloPremium: true
        },
        null
      );
    });

    it('envía soloPremium false al desmarcar la casilla', () => {
      component.editarProducto(productoSoloPremium);
      component.soloPremium.setValue(false);

      component.guardarProducto();

      expect(productoServiceMock.modificarProducto).toHaveBeenCalledWith(
        'producto-1',
        expect.objectContaining({ soloPremium: false }),
        null
      );
    });

    it('no envía la petición si el descuento premium supera 100', () => {
      component.editarProducto(productoSoloPremium);
      component.formularioProducto.patchValue({ descuentoPremium: 101 });

      component.guardarProducto();

      expect(productoServiceMock.modificarProducto).not.toHaveBeenCalled();
    });

    it('la casilla de un producto no afecta al siguiente producto que se modifica', () => {
      component.editarProducto(productoSoloPremium);
      component.alternarFormulario();
      component.editarProducto(productoActivo);

      expect(component.soloPremium.value).toBe(false);
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

    it('al modificar se admite stock 0', () => {
      component.editarProducto(productoActivo);
      fixture.detectChanges();
      component.formularioProducto.patchValue({ stock: 0 });

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

  describe('eliminar producto (HU-33)', () => {
    function botonesEliminar(): HTMLButtonElement[] {
      return Array.from(fixture.nativeElement.querySelectorAll('button.boton-eliminar'));
    }

    it('muestra la acción Eliminar en cada producto', () => {
      expect(botonesEliminar().map((boton) => boton.getAttribute('aria-label'))).toEqual([
        'Eliminar Teclado',
        'Eliminar Ratón'
      ]);
    });

    it('pide confirmación nombrando el producto antes de eliminarlo', async () => {
      const confirmar = simularConfirmacion(true);

      botonesEliminar()[0].click();
      await esperarConfirmacion();

      expect(confirmar).toHaveBeenCalledWith(
        expect.objectContaining({ mensaje: expect.stringContaining('«Teclado»'), peligro: true })
      );
    });

    it('elimina el producto confirmado, informa del resultado y recarga el catálogo', async () => {
      simularConfirmacion(true);
      productoServiceMock.consultarProductosPropios.mockReturnValue(of([productoDesactivado]));

      botonesEliminar()[0].click();
      await esperarConfirmacion();
      fixture.detectChanges();

      expect(productoServiceMock.eliminarProducto).toHaveBeenCalledWith('producto-1');
      expect(component.mensajeExito()).toBe('Producto eliminado correctamente.');
      expect(productoServiceMock.consultarProductosPropios).toHaveBeenCalledTimes(2);
      expect(fixture.nativeElement.textContent).not.toContain('Teclado');
      expect(component.productoEliminando()).toBeNull();
    });

    it('no elimina nada si se cancela la confirmación', async () => {
      simularConfirmacion(false);

      botonesEliminar()[0].click();
      await esperarConfirmacion();
      fixture.detectChanges();

      expect(productoServiceMock.eliminarProducto).not.toHaveBeenCalled();
      expect(component.mensajeExito()).toBe('');
      expect(productoServiceMock.consultarProductosPropios).toHaveBeenCalledTimes(1);
    });

    it('muestra el motivo del backend si el producto tiene pedidos en curso', async () => {
      simularConfirmacion(true);
      const motivo =
        'No se puede eliminar el producto porque figura en pedidos en curso (pagados o enviados).';
      productoServiceMock.eliminarProducto.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 409, error: { error: motivo } }))
      );

      botonesEliminar()[0].click();
      await esperarConfirmacion();
      fixture.detectChanges();

      expect(component.mensajeError()).toBe(motivo);
      expect(component.mensajeExito()).toBe('');
      expect(fixture.nativeElement.textContent).toContain(motivo);
    });

    it('muestra el mensaje del backend si el producto ya no existe y recarga el catálogo', async () => {
      simularConfirmacion(true);
      productoServiceMock.eliminarProducto.mockReturnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 404,
              error: { error: 'No se ha encontrado el producto solicitado.' }
            })
        )
      );

      botonesEliminar()[0].click();
      await esperarConfirmacion();

      expect(component.mensajeError()).toBe('No se ha encontrado el producto solicitado.');
      expect(productoServiceMock.consultarProductosPropios).toHaveBeenCalledTimes(2);
    });

    it('usa un mensaje genérico si el backend no indica el motivo', async () => {
      simularConfirmacion(true);
      productoServiceMock.eliminarProducto.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 500 }))
      );

      botonesEliminar()[0].click();
      await esperarConfirmacion();

      expect(component.mensajeError()).toBe('No se pudo eliminar el producto.');
    });

    it('muestra un mensaje de conexión si el servicio no responde', async () => {
      simularConfirmacion(true);
      productoServiceMock.eliminarProducto.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 0 }))
      );

      botonesEliminar()[0].click();
      await esperarConfirmacion();

      expect(component.mensajeError()).toBe('No se pudo conectar con el servicio. Inténtalo de nuevo.');
    });

    it('si no puede eliminarse, el producto sigue en el catálogo con sus acciones', async () => {
      simularConfirmacion(true);
      productoServiceMock.eliminarProducto.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 409, error: { error: 'Pedidos en curso' } }))
      );

      botonesEliminar()[0].click();
      await esperarConfirmacion();
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('Teclado');
      expect(botonesEliminar()[0].disabled).toBe(false);
    });

    it('desactiva el botón mientras se procesa la eliminación', async () => {
      simularConfirmacion(true);
      productoServiceMock.eliminarProducto.mockReturnValue(new Subject<void>());

      botonesEliminar()[0].click();
      await esperarConfirmacion();
      fixture.detectChanges();

      expect(botonesEliminar()[0].disabled).toBe(true);
      expect(botonesEliminar()[0].textContent?.trim()).toBe('Eliminando...');
      expect(botonesEliminar()[1].disabled).toBe(false);
    });

    it('cierra el formulario si se elimina el producto que se estaba modificando', async () => {
      simularConfirmacion(true);
      component.editarProducto(productoActivo);
      fixture.detectChanges();

      await component.eliminarProducto(productoActivo);

      expect(component.productoEnEdicion()).toBeNull();
      expect(component.mostrandoFormulario()).toBe(false);
    });
  });
});

registerLocaleData(localeEs, 'es-ES');

describe('PanelVendedor - tarjetas de producto (A15, A16, M7, B7)', () => {
  let fixture: ComponentFixture<PanelVendedor>;

  const producto = (extra: Partial<ProductoPropio> = {}): ProductoPropio => ({
    id: 'p1',
    nombre: 'Teclado',
    categoria: 'Informática',
    stock: 4,
    descripcion: null,
    precio: 1099,
    descuento: 0,
    descuentoPremium: 0,
    esVisible: true,
    imagen: null,
    soloPremium: false,
    ...extra
  });

  const servicioProductos = {
    consultarProductosPropios: vi.fn(),
    activarProducto: vi.fn(),
    desactivarProducto: vi.fn(),
    eliminarProducto: vi.fn(),
    crearProducto: vi.fn(),
    modificarProducto: vi.fn(),
    urlImagen: vi.fn((imagen: string) => `http://api${imagen}`)
  };

  function crear(productos: ProductoPropio[]): HTMLElement {
    servicioProductos.consultarProductosPropios.mockReturnValue(of(productos));
    fixture = TestBed.createComponent(PanelVendedor);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PanelVendedor],
      providers: [
        provideRouter([]),
        { provide: LOCALE_ID, useValue: 'es-ES' },
        { provide: ProductoService, useValue: servicioProductos },
        { provide: CategoriasService, useValue: { obtenerCategorias: () => of([]) } },
        { provide: VendedorService, useValue: { obtenerPerfil: () => of({ nombreComercial: 'Tienda' }) } },
        { provide: AuthService, useValue: { cerrarSesion: () => of(void 0) } }
      ]
    }).compileComponents();
  });

  afterEach(() => {
    vi.clearAllMocks();
    TestBed.resetTestingModule();
  });

  it('muestra el precio con formato español (A15)', () => {
    const vista = crear([producto({ precio: 1099 }), producto({ id: 'p2', precio: 89.9 })]);

    const precios = Array.from(vista.querySelectorAll('li .precio')).map((p) => p.textContent?.trim());
    expect(precios).toEqual(['1.099,00 €', '89,90 €']);
  });

  it('oculta los descuentos a cero y muestra los que tienen valor (B7)', () => {
    const vista = crear([producto({ descuento: 0, descuentoPremium: 15 })]);

    const texto = vista.querySelector('li')?.textContent ?? '';
    expect(texto).not.toContain('Descuento: 0');
    expect(texto).toContain('Descuento Premium: 15 %');
  });

  it('indica el estado con un chip que conserva el texto «Estado: …»', () => {
    const vista = crear([producto(), producto({ id: 'p2', esVisible: false })]);

    const chips = vista.querySelectorAll('li .cabecera-producto .chip');
    expect(chips[0].classList).toContain('chip-exito');
    expect(chips[0].textContent?.replace(/\s+/g, ' ').trim()).toBe('Estado: Activo');
    expect(chips[1].classList).toContain('chip-neutro');
    expect(chips[1].textContent?.replace(/\s+/g, ' ').trim()).toBe('Estado: Desactivado');
  });

  it('reserva el marco con un icono si el producto no tiene imagen (M7)', () => {
    const vista = crear([producto({ imagen: null })]);

    const marco = vista.querySelector('li .marco-imagen');
    expect(marco?.querySelector('img')).toBeNull();
    expect(marco?.querySelector('svg')).not.toBeNull();
    expect(marco?.textContent).toContain('Producto sin imagen');
  });

  it('sustituye por el icono una imagen que no carga (M7)', () => {
    const vista = crear([producto({ imagen: '/product-images/roto.png' })]);

    const imagen = vista.querySelector('li .marco-imagen img') as HTMLImageElement;
    expect(imagen.getAttribute('src')).toBe('http://api/product-images/roto.png');
    imagen.dispatchEvent(new Event('error'));
    fixture.detectChanges();

    expect(vista.querySelector('li .marco-imagen img')).toBeNull();
    expect(vista.querySelector('li .marco-imagen svg')).not.toBeNull();
  });

  it('«Modificar» es la acción principal y «Eliminar» es de contorno (A16)', () => {
    const vista = crear([producto()]);

    expect(vista.querySelector('.boton-modificar')?.classList).toContain('boton-principal');
    expect(vista.querySelector('.boton-estado')?.classList).toContain('boton-secundario');
    expect(vista.querySelector('.boton-eliminar')?.classList).toContain('boton-peligro');
  });
  it('la búsqueda por nombre no distingue tildes ni mayúsculas (M22)', () => {
    const vista = crear([producto({ id: 'a', nombre: 'Ratón inalámbrico' }), producto({ id: 'b', nombre: 'Teclado' })]);

    fixture.componentInstance.terminoBusqueda.set('RATON');
    fixture.detectChanges();

    const titulos = Array.from(vista.querySelectorAll('li h2')).map((h) => h.textContent?.trim());
    expect(titulos).toEqual(['Ratón inalámbrico']);
  });

  it('busca con tilde en el texto y sin tilde en el nombre (M22)', () => {
    const vista = crear([producto({ id: 'a', nombre: 'Cafe molido' })]);

    fixture.componentInstance.terminoBusqueda.set('café');
    fixture.detectChanges();

    expect(vista.querySelectorAll('li').length).toBe(1);
  });
  describe('formulario de producto (M14, B11)', () => {
    function abrirFormulario(): HTMLElement {
      const vista = crear([producto()]);
      (vista.querySelector('button.añadirProductos') as HTMLButtonElement).click();
      fixture.detectChanges();
      return vista;
    }

    it('precio y stock empiezan vacíos y con placeholder, no con un 0', () => {
      const vista = abrirFormulario();

      const precio = vista.querySelector('#precio-producto') as HTMLInputElement;
      const stock = vista.querySelector('#stock-producto') as HTMLInputElement;
      expect(precio.value).toBe('');
      expect(precio.placeholder).toBe('0,00');
      expect(stock.value).toBe('');
      expect(stock.placeholder).toBe('Unidades');
    });

    it('no deja crear el producto con precio y stock vacíos', () => {
      abrirFormulario();

      fixture.componentInstance.guardarProducto();

      expect(servicioProductos.crearProducto).not.toHaveBeenCalled();
      expect(fixture.componentInstance.formularioProducto.controls.precio.hasError('required')).toBe(true);
      expect(fixture.componentInstance.formularioProducto.controls.stock.hasError('required')).toBe(true);
    });

    it('la ayuda del formato de imagen va en texto secundario y el error en rojo', () => {
      const vista = abrirFormulario();

      const ayuda = vista.querySelector('#ayuda-imagen') as HTMLElement;
      expect(ayuda.classList).toContain('ayuda-campo');
      expect(ayuda.classList).not.toContain('error-campo');

      fixture.componentInstance.errorImagen.set('La imagen debe ser PNG, JPG o GIF.');
      fixture.detectChanges();
      const error = vista.querySelector('.campo-imagen .error-campo') as HTMLElement;
      expect(error.getAttribute('role')).toBe('alert');
    });

    it('muestra el nombre del fichero elegido junto al botón «Elegir imagen»', () => {
      const vista = abrirFormulario();
      expect(vista.querySelector('.nombre-archivo')?.textContent).toContain('Ningún archivo seleccionado');
      expect(vista.querySelector('.selector-archivo label')?.textContent).toContain('Elegir imagen');

      fixture.componentInstance.imagenSeleccionada.set(new File([new Uint8Array([1])], 'teclado.png', { type: 'image/png' }));
      fixture.detectChanges();

      expect(vista.querySelector('.nombre-archivo')?.textContent).toContain('teclado.png');
    });

    it('los errores de campo usan la clase de error (0,875 rem)', () => {
      const vista = abrirFormulario();

      fixture.componentInstance.guardarProducto();
      fixture.detectChanges();

      const errores = vista.querySelectorAll('.campo .error-campo');
      expect(errores.length).toBeGreaterThan(0);
    });
  });
  describe('foco y desplazamiento del formulario (A20)', () => {
    function montar(): HTMLElement {
      const vista = crear([producto({ id: 'a', nombre: 'Teclado' }), producto({ id: 'b', nombre: 'Ratón' })]);
      document.body.appendChild(vista);
      return vista;
    }

    const modificar = (vista: HTMLElement, id: string) =>
      vista.querySelector(`.boton-modificar[data-producto="${id}"]`) as HTMLButtonElement;

    afterEach(() => {
      fixture?.nativeElement.remove();
    });

    it('al pulsar «Modificar» el foco entra en el título del formulario', async () => {
      const vista = montar();
      const boton = modificar(vista, 'b');
      boton.focus();

      boton.click();
      fixture.detectChanges();
      await fixture.whenStable();

      expect(document.activeElement?.id).toBe('titulo-formulario');
      expect(document.activeElement?.textContent).toContain('Modificar producto');
    });

    it('lleva el formulario a la vista con scrollIntoView', async () => {
      const vista = montar();
      const desplazar = vi.fn();
      (HTMLElement.prototype as { scrollIntoView?: unknown }).scrollIntoView = desplazar;

      modificar(vista, 'a').click();
      fixture.detectChanges();
      await fixture.whenStable();

      expect(desplazar).toHaveBeenCalledWith(expect.objectContaining({ block: 'start' }));
      delete (HTMLElement.prototype as { scrollIntoView?: unknown }).scrollIntoView;
    });

    it('al cancelar devuelve el foco al «Modificar» del mismo producto', async () => {
      const vista = montar();
      modificar(vista, 'b').click();
      fixture.detectChanges();
      await fixture.whenStable();

      fixture.componentInstance.alternarFormulario();
      fixture.detectChanges();
      await fixture.whenStable();

      expect(document.activeElement).toBe(modificar(vista, 'b'));
    });

    it('al cancelar «Añadir producto» el foco vuelve a ese botón', async () => {
      const vista = montar();
      const anadir = vista.querySelector('button.añadirProductos') as HTMLButtonElement;
      anadir.focus();
      anadir.click();
      fixture.detectChanges();
      await fixture.whenStable();
      expect(document.activeElement?.id).toBe('titulo-formulario');

      fixture.componentInstance.alternarFormulario();
      fixture.detectChanges();
      await fixture.whenStable();

      expect(document.activeElement).toBe(vista.querySelector('button.añadirProductos'));
    });
  });
});
