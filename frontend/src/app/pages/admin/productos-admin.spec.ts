import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ProductoService } from '../paneles/producto.service';
import { ProductosAdmin } from './productos-admin';
import { ProductoAdministracion, ProductosAdminService } from './productos-admin.service';

describe('ProductosAdmin (HU-34)', () => {
  let fixture: ComponentFixture<ProductosAdmin>;
  let component: ProductosAdmin;

  const productos: ProductoAdministracion[] = [
    {
      id: 'p1',
      nombre: 'Teclado mecánico',
      categoria: 'Informática',
      stock: 3,
      descripcion: 'Retroiluminado',
      precio: 49.99,
      descuento: 10,
      descuentoPremium: 20,
      esVisible: true,
      imagen: '/product-images/teclado.png',
      soloPremium: true,
      vendedor: 'Tienda A'
    },
    {
      id: 'p2',
      nombre: 'Ratón',
      categoria: 'Periféricos',
      stock: 0,
      descripcion: null,
      precio: 15,
      descuento: 0,
      descuentoPremium: 0,
      esVisible: false,
      imagen: null,
      soloPremium: false,
      vendedor: 'Tienda B'
    }
  ];

  const productosAdminServiceMock = { consultarProductos: vi.fn() };
  const productoServiceMock = {
    urlImagen: vi.fn((imagen: string) => `http://api${imagen}`)
  };

  async function crearComponente(): Promise<HTMLElement> {
    fixture = TestBed.createComponent(ProductosAdmin);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  function seleccionar(elemento: HTMLElement, id: string, valor: string): void {
    const select = elemento.querySelector<HTMLSelectElement>(`#${id}`)!;
    select.value = valor;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function nombresMostrados(elemento: HTMLElement): string[] {
    return Array.from(elemento.querySelectorAll('.tarjeta-producto h2')).map(
      (titulo) => titulo.textContent?.trim() ?? ''
    );
  }

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      imports: [ProductosAdmin],
      providers: [
        provideRouter([]),
        { provide: ProductosAdminService, useValue: productosAdminServiceMock },
        { provide: ProductoService, useValue: productoServiceMock }
      ]
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('muestra los productos de todos los vendedores con su vendedor y estado, desactivados incluidos', async () => {
    productosAdminServiceMock.consultarProductos.mockReturnValue(of(productos));

    const elemento = await crearComponente();
    const tarjetas = elemento.querySelectorAll('.tarjeta-producto');

    expect(tarjetas.length).toBe(2);
    expect(tarjetas[0].textContent).toContain('Vendedor: Tienda A');
    expect(tarjetas[0].textContent).toContain('Estado: Activo');
    expect(tarjetas[1].textContent).toContain('Vendedor: Tienda B');
    expect(tarjetas[1].textContent).toContain('Estado: Desactivado');
    expect(tarjetas[1].classList).toContain('producto-desactivado');
  });

  it('muestra la misma información que ve el vendedor', async () => {
    productosAdminServiceMock.consultarProductos.mockReturnValue(of(productos));

    const elemento = await crearComponente();
    const tarjeta = elemento.querySelector('.tarjeta-producto')!;
    const texto = tarjeta.textContent ?? '';

    expect(texto).toContain('Informática');
    expect(texto).toContain('49.99 €');
    expect(texto).toContain('Stock3');
    expect(texto).toContain('10 %');
    expect(texto).toContain('20 %');
    expect(texto).toContain('Solo premium');
    expect(texto).toContain('Retroiluminado');
    expect(tarjeta.querySelector('img')?.getAttribute('src')).toBe(
      'http://api/product-images/teclado.png'
    );
  });

  it('no muestra acciones de crear, modificar, activar, desactivar ni eliminar', async () => {
    productosAdminServiceMock.consultarProductos.mockReturnValue(of(productos));

    const elemento = await crearComponente();
    const botones = Array.from(elemento.querySelectorAll('button')).map(
      (boton) => boton.textContent?.trim() ?? ''
    );

    expect(elemento.textContent).toContain('Modo solo lectura');
    for (const accion of ['Añadir', 'Crear', 'Modificar', 'Activar', 'Desactivar', 'Eliminar']) {
      expect(botones.some((texto) => texto.includes(accion))).toBe(false);
    }
    expect(elemento.querySelectorAll('.tarjeta-producto button').length).toBe(0);
    expect(elemento.querySelector('input[type="file"]')).toBeNull();
  });

  it('filtra por nombre como texto literal', async () => {
    productosAdminServiceMock.consultarProductos.mockReturnValue(of(productos));
    const elemento = await crearComponente();

    const busqueda = elemento.querySelector<HTMLInputElement>('#busqueda-producto-admin')!;
    busqueda.value = 'TECLADO';
    busqueda.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(nombresMostrados(elemento)).toEqual(['Teclado mecánico']);

    busqueda.value = '.*';
    busqueda.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(nombresMostrados(elemento)).toEqual([]);
    expect(elemento.textContent).toContain('No se encontraron productos');
  });

  it('filtra por vendedor, categoría y estado y permite limpiar los filtros', async () => {
    productosAdminServiceMock.consultarProductos.mockReturnValue(of(productos));
    const elemento = await crearComponente();

    seleccionar(elemento, 'filtro-vendedor-admin', 'Tienda B');
    expect(nombresMostrados(elemento)).toEqual(['Ratón']);

    seleccionar(elemento, 'filtro-vendedor-admin', '');
    seleccionar(elemento, 'filtro-categoria-admin', 'Informática');
    expect(nombresMostrados(elemento)).toEqual(['Teclado mecánico']);

    seleccionar(elemento, 'filtro-categoria-admin', '');
    seleccionar(elemento, 'filtro-estado-admin', 'desactivados');
    expect(nombresMostrados(elemento)).toEqual(['Ratón']);

    seleccionar(elemento, 'filtro-estado-admin', 'activos');
    expect(nombresMostrados(elemento)).toEqual(['Teclado mecánico']);

    elemento.querySelector<HTMLButtonElement>('.boton-limpiar')!.click();
    fixture.detectChanges();
    expect(nombresMostrados(elemento)).toEqual(['Teclado mecánico', 'Ratón']);
    expect(component.hayFiltrosActivos()).toBe(false);
  });

  it('ofrece como opciones los vendedores y categorías de los productos', async () => {
    productosAdminServiceMock.consultarProductos.mockReturnValue(of(productos));
    await crearComponente();

    expect(component.vendedoresDisponibles()).toEqual(['Tienda A', 'Tienda B']);
    expect(component.categoriasDisponibles()).toEqual(['Informática', 'Periféricos']);
  });

  it('informa cuando no hay ningún producto', async () => {
    productosAdminServiceMock.consultarProductos.mockReturnValue(of([]));

    const elemento = await crearComponente();

    expect(elemento.textContent).toContain('Todavía no hay productos');
    expect(elemento.querySelector('.filtros-catalogo')).toBeNull();
    expect(elemento.querySelector('[role="alert"]')).toBeNull();
  });

  it('muestra un error de carga y permite reintentar', async () => {
    productosAdminServiceMock.consultarProductos.mockReturnValueOnce(
      throwError(() => new HttpErrorResponse({ status: 500 }))
    );
    const elemento = await crearComponente();

    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain(
      'No se pudieron cargar los productos'
    );
    expect(elemento.textContent).not.toContain('Todavía no hay productos');

    productosAdminServiceMock.consultarProductos.mockReturnValueOnce(of(productos));
    elemento.querySelector<HTMLButtonElement>('.boton-reintentar')!.click();
    fixture.detectChanges();

    expect(productosAdminServiceMock.consultarProductos).toHaveBeenCalledTimes(2);
    expect(elemento.querySelector('[role="alert"]')).toBeNull();
    expect(elemento.querySelectorAll('.tarjeta-producto').length).toBe(2);
  });

  it('distingue el error de conexión', async () => {
    productosAdminServiceMock.consultarProductos.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 0 }))
    );

    const elemento = await crearComponente();

    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain(
      'No se pudo conectar con el servicio'
    );
  });
});
