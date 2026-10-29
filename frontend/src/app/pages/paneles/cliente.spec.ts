import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { environment } from '../../../environments/environment';
import { PanelCliente } from './cliente';
import { ProductoCatalogo } from './producto.service';

describe('PanelCliente - catálogo HU-19', () => {
  let fixture: ComponentFixture<PanelCliente>;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PanelCliente],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
    });
    fixture = TestBed.createComponent(PanelCliente);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
    TestBed.resetTestingModule();
  });

  it('muestra carga y el estado vacío cuando no hay productos activos', () => {
    expect(fixture.nativeElement.textContent).toContain('Cargando productos...');
    httpMock.expectOne(`${environment.apiUrl}/cliente/productos`).flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Todavía no hay productos disponibles.');
  });

  it('muestra los productos Premium como bloqueados sin ocultarlos', () => {
    httpMock.expectOne(`${environment.apiUrl}/cliente/productos`).flush([
      crearProducto('normal-1', false, true, 5),
      crearProducto('premium-1', true, false, 2)
    ]);
    fixture.detectChanges();

    const tarjetas = fixture.nativeElement.querySelectorAll('.product-card');
    expect(tarjetas.length).toBe(2);
    expect(tarjetas[1].textContent).toContain('Premium');
    expect(tarjetas[1].textContent).toContain('Solo clientes Premium pueden consultar este producto.');
    expect(tarjetas[1].textContent).toContain('ver sus descuentos Premium');
    expect(tarjetas[1].querySelector('button').disabled).toBe(true);

    tarjetas[1].querySelector('button').click();
    fixture.detectChanges();
    httpMock.expectNone(`${environment.apiUrl}/cliente/productos/premium-1`);
  });

  it('no bloquea productos normales si el backend aún no incluye la elegibilidad Premium', () => {
    const producto = crearProducto('normal-legado', false, true, 5);
    producto.puedeConsultarDetalle = undefined;
    httpMock.expectOne(`${environment.apiUrl}/cliente/productos`).flush([producto]);
    fixture.detectChanges();

    const tarjeta = fixture.nativeElement.querySelector('.product-card');
    expect(tarjeta.classList.contains('product-blocked')).toBe(false);
    expect(tarjeta.querySelector('.premium-message')).toBeNull();
    expect(tarjeta.querySelector('button').disabled).toBe(false);
  });

  it('envía filtros combinados al backend y muestra la respuesta filtrada', () => {
    httpMock.expectOne(`${environment.apiUrl}/cliente/productos`).flush([
      crearProducto('disponible', false, true, 5),
      crearProducto('agotado', false, true, 0)
    ]);
    fixture.detectChanges();

    const formulario = fixture.nativeElement.querySelector('.catalog-filters');
    const busqueda = formulario.querySelector('input[type="search"]');
    busqueda.value = 'agotado';
    busqueda.dispatchEvent(new Event('input'));
    const categorias = formulario.querySelectorAll('select');
    categorias[0].value = 'Informática';
    categorias[0].dispatchEvent(new Event('change'));
    const precios = formulario.querySelectorAll('input[type="number"]');
    precios[0].value = '10';
    precios[0].dispatchEvent(new Event('input'));
    precios[1].value = '30';
    precios[1].dispatchEvent(new Event('input'));
    const disponibilidad = categorias[1];
    disponibilidad.value = 'agotados';
    disponibilidad.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    formulario.dispatchEvent(new Event('submit'));
    const peticion = httpMock.expectOne((request) => request.url === `${environment.apiUrl}/cliente/productos`);
    expect(peticion.request.params.get('nombre')).toBe('agotado');
    expect(peticion.request.params.get('categoria')).toBe('Informática');
    expect(peticion.request.params.get('precioMinimo')).toBe('10');
    expect(peticion.request.params.get('precioMaximo')).toBe('30');
    expect(peticion.request.params.get('disponible')).toBe('false');
    peticion.flush([crearProducto('agotado', false, true, 0)]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.product-card').length).toBe(1);
    expect(fixture.nativeElement.querySelector('.product-card').textContent).toContain('agotado');
  });

  it('muestra el estado sin resultados cuando los filtros no encuentran productos', () => {
    httpMock.expectOne(`${environment.apiUrl}/cliente/productos`).flush([
      crearProducto('teclado', false, true, 4)
    ]);
    fixture.detectChanges();

    const formulario = fixture.nativeElement.querySelector('.catalog-filters');
    const busqueda = formulario.querySelector('input[type="search"]');
    busqueda.value = 'inexistente';
    busqueda.dispatchEvent(new Event('input'));
    formulario.dispatchEvent(new Event('submit'));
    httpMock.expectOne((request) =>
      request.url === `${environment.apiUrl}/cliente/productos` &&
      request.params.get('nombre') === 'inexistente'
    ).flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No se encontraron productos con esos filtros.');
  });

  it('valida el rango de precio antes de enviar los filtros', () => {
    httpMock.expectOne(`${environment.apiUrl}/cliente/productos`).flush([
      crearProducto('teclado', false, true, 4)
    ]);
    fixture.detectChanges();

    const formulario = fixture.nativeElement.querySelector('.catalog-filters');
    const precios = formulario.querySelectorAll('input[type="number"]');
    precios[0].value = '100';
    precios[0].dispatchEvent(new Event('input'));
    precios[1].value = '10';
    precios[1].dispatchEvent(new Event('input'));
    formulario.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('El precio mínimo no puede superar el precio máximo.');
    httpMock.expectNone((request) =>
      request.url === `${environment.apiUrl}/cliente/productos` &&
      request.params.has('precioMinimo')
    );
  });

  it('muestra el aviso informativo de stock bajo en el detalle', () => {
    httpMock.expectOne(`${environment.apiUrl}/cliente/productos`).flush([
      crearProducto('stock-bajo', false, true, 4)
    ]);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('.product-card button').click();
    const peticionDetalle = httpMock.expectOne(`${environment.apiUrl}/cliente/productos/stock-bajo`);
    expect(peticionDetalle.request.method).toBe('GET');
    peticionDetalle.flush({
      ...crearProducto('stock-bajo', false, true, 4),
      precioOriginal: 25,
      descuentoAplicado: 10,
      descripcion: 'Descripción',
      vendedor: 'Tienda',
      esVisible: true
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.low-stock-notice').textContent)
      .toContain('quedan pocas unidades (4)');
  });

  it('no muestra el aviso de stock bajo cuando quedan cinco unidades', () => {
    httpMock.expectOne(`${environment.apiUrl}/cliente/productos`).flush([
      crearProducto('stock-limite', false, true, 5)
    ]);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('.product-card button').click();
    httpMock.expectOne(`${environment.apiUrl}/cliente/productos/stock-limite`).flush({
      ...crearProducto('stock-limite', false, true, 5),
      precioOriginal: 25,
      descuentoAplicado: 10,
      descripcion: null,
      vendedor: 'Tienda',
      esVisible: true
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.low-stock-notice')).toBeNull();
  });

  it('muestra nombre, vendedor y descripción como texto sin crear elementos HTML', () => {
    httpMock.expectOne(`${environment.apiUrl}/cliente/productos`).flush([
      {
        ...crearProducto('xss', false, true, 6),
        nombre: '<img src=x onerror=alert(1)>',
        vendedor: '<svg onload=alert(1)>',
        precioOriginal: 25,
        descuentoAplicado: 0,
        descripcion: '<script>alert(1)</script>'
      }
    ]);
    fixture.detectChanges();

    const tarjeta = fixture.nativeElement.querySelector('.product-card');
    expect(tarjeta.querySelector('h3').textContent).toBe('<img src=x onerror=alert(1)>');
    expect(tarjeta.querySelector('img[src="x"]')).toBeNull();
    expect(tarjeta.textContent).toContain('Vendedor: <svg onload=alert(1)>');

    tarjeta.querySelector('button').click();
    httpMock.expectOne(`${environment.apiUrl}/cliente/productos/xss`).flush({
      ...crearProducto('xss', false, true, 6),
      nombre: '<img src=x onerror=alert(1)>',
      vendedor: '<svg onload=alert(1)>',
      precioOriginal: 25,
      descuentoAplicado: 0,
      descripcion: '<script>alert(1)</script>',
      esVisible: true
    });
    fixture.detectChanges();

    const detalle = fixture.nativeElement.querySelector('.product-detail');
    expect(detalle.textContent).toContain('<script>alert(1)</script>');
    expect(detalle.querySelector('script')).toBeNull();
    expect(detalle.querySelector('img[src="x"]')).toBeNull();
  });

  function crearProducto(
    id: string,
    soloPremium: boolean,
    puedeConsultarDetalle: boolean,
    stock: number
  ): ProductoCatalogo {
    return {
      id,
      nombre: `Producto ${id}`,
      categoria: 'Informática',
      stock,
      precio: 25,
      precioOriginal: 25,
      descuento: 10,
      descuentoPremium: 20,
      descuentoAplicado: 10,
      descripcion: null,
      imagen: null,
      vendedor: 'Tienda',
      esVisible: true,
      soloPremium,
      puedeConsultarDetalle
    };
  }
});
