import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { environment } from '../../../environments/environment';
import { PanelCliente } from './cliente';
import { ProductoCatalogo } from './producto.service';
import type { ResumenLista } from '../listas/listas.service';

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
    for (const peticion of httpMock.match((request) =>
      request.url.endsWith('/valoraciones')
    )) {
      peticion.flush({ media: 0, total: 0, valoraciones: [] });
    }

    const peticionesFavoritos = httpMock.match(
      `${environment.apiUrl}/usuarios/favoritos`
    );

    for (const peticion of peticionesFavoritos) {
      peticion.flush({
        ids: [],
        productos: []
      });
    }

    // HU-39: el detalle consulta las listas del usuario.
    for (const peticion of httpMock.match(`${environment.apiUrl}/usuarios/listas`)) {
      peticion.flush([]);
    }

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
    const botonDetalle = (tarjeta as HTMLElement).querySelector<HTMLButtonElement>(
      '.product-actions .secondary-button'
    );

    expect(botonDetalle).not.toBeNull();
    expect(botonDetalle?.disabled).toBe(false);
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

    fixture.nativeElement
      .querySelector('.product-card .product-actions .secondary-button')
      .click();
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

  it('muestra media y comentarios anónimos en el detalle del producto', () => {
    const producto = crearProducto('valoraciones-1', false, true, 4);
    httpMock.expectOne(`${environment.apiUrl}/cliente/productos`).flush([producto]);
    fixture.detectChanges();

    fixture.nativeElement
      .querySelector('.product-card .product-actions .secondary-button')
      .click();
    httpMock.expectOne(`${environment.apiUrl}/usuarios/listas`).flush([]);
    httpMock.expectOne(`${environment.apiUrl}/cliente/productos/valoraciones-1`)
      .flush({
        ...producto,
        precioOriginal: 25,
        descuentoAplicado: 0,
        descripcion: 'Descripción',
        vendedor: 'Tienda',
        esVisible: true
      });
    httpMock.expectOne(`${environment.apiUrl}/cliente/productos/valoraciones-1/valoraciones`)
      .flush({
        media: 4.5,
        total: 2,
        valoraciones: [
          { puntuacion: 5, comentario: 'Excelente' },
          { puntuacion: 4, comentario: '<img src=x onerror=alert(1)>' }
        ]
      });
    fixture.detectChanges();

    const opiniones = fixture.nativeElement.querySelector('.detail-reviews');
    expect(opiniones.textContent).toContain('4.5 / 5');
    expect(opiniones.textContent).toContain('2 valoraciones');
    expect(opiniones.textContent).toContain('Excelente');
    expect(opiniones.textContent).toContain('<img src=x onerror=alert(1)>');
    expect(opiniones.querySelector('img')).toBeNull();
    expect(opiniones.textContent).toContain('Cliente verificado');
    expect(opiniones.textContent).not.toContain('clienteId');
  });

  it('no muestra el aviso de stock bajo cuando quedan cinco unidades', () => {
    httpMock.expectOne(`${environment.apiUrl}/cliente/productos`).flush([
      crearProducto('stock-limite', false, true, 5)
    ]);
    fixture.detectChanges();

    fixture.nativeElement
      .querySelector('.product-card .product-actions .secondary-button')
      .click();
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

    tarjeta
      .querySelector('.product-actions .secondary-button')
      .click();
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

  function prepararCarrito() {
    const producto = crearProducto('carrito-1', false, true, 10);
    httpMock.expectOne(`${environment.apiUrl}/cliente/productos`).flush([producto]);
    fixture.detectChanges();
    return producto;
  }

  function peticionCarrito() {
    httpMock.expectOne(`${environment.apiUrl}/usuarios/csrf`).flush({ token: 'csrf' });
    return httpMock.expectOne(`${environment.apiUrl}/cliente/carrito/productos`);
  }

  it('añade desde catálogo, muestra carga, evita doble envío y confirma el importe', () => {
    prepararCarrito();
    const boton = fixture.nativeElement.querySelector('.catalog-cart-button');
    boton.click();
    fixture.detectChanges();
    expect(boton.disabled).toBe(true);
    expect(boton.getAttribute('aria-busy')).toBe('true');
    expect(boton.getAttribute('aria-label')).toBe('Añadir al carrito');
    expect(boton.getAttribute('title')).toBe('Añadir al carrito');
    expect(boton.querySelector('svg')).not.toBeNull();
    expect(boton.textContent.trim()).toBe('');
    boton.click();
    const peticion = peticionCarrito();
    expect(peticion.request.body).toEqual({ productoId: 'carrito-1', cantidad: 1 });
    peticion.flush({ productos: [], importe: 25 });
    fixture.detectChanges();
    expect(boton.disabled).toBe(false);
    expect(boton.getAttribute('aria-busy')).toBe('false');
    expect(fixture.nativeElement.querySelector('.product-card output.status-message').textContent)
      .toContain('Producto añadido al carrito. Importe del carrito: 25.00 €');
  });

  it('añade desde el detalle la cantidad seleccionada sin cambiar el stock mostrado', () => {
    const producto = prepararCarrito();
    fixture.nativeElement.querySelector('.product-card .secondary-button').click();
    httpMock.expectOne(`${environment.apiUrl}/cliente/productos/carrito-1`).flush(producto);
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[aria-label="Aumentar cantidad"]').click();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('.detail-cart-button').click();
    const peticion = peticionCarrito();
    expect(peticion.request.body.cantidad).toBe(2);
    peticion.flush({ productos: [], importe: 50 });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.product-detail .detail-purchase + .status-message, .product-detail output.status-message').textContent)
      .toContain('Producto añadido al carrito.');
    expect(fixture.componentInstance.detalle()?.stock).toBe(10);
    expect(fixture.componentInstance.cantidadDetalle()).toBe(2);
  });

  it.each([0, -1, 1.5, NaN, Infinity])('rechaza cantidad inválida %s sin enviar HTTP', cantidad => {
    const producto = prepararCarrito();
    fixture.componentInstance.anadirAlCarrito(producto, cantidad);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('número entero');
    httpMock.expectNone(`${environment.apiUrl}/usuarios/csrf`);
  });

  it('rechaza cantidad superior al stock y producto Premium no permitido antes de enviar', () => {
    const producto = prepararCarrito();
    fixture.componentInstance.anadirAlCarrito(producto, 11);
    expect(fixture.componentInstance.errorCarrito()).toContain('stock suficiente');
    fixture.componentInstance.anadirAlCarrito({ ...producto, soloPremium: true, puedeConsultarDetalle: false }, 1);
    expect(fixture.componentInstance.errorCarrito()).toContain('Premium');
    httpMock.expectNone(`${environment.apiUrl}/usuarios/csrf`);
  });

  it.each([
    [400, 'No hay stock suficiente para la cantidad total solicitada.'],
    [400, 'El producto ya no está disponible.'],
    [404, 'El producto ya no está disponible o ha sido eliminado.'],
    [409, 'El carrito se ha modificado. Inténtalo de nuevo.']
  ])('muestra el motivo funcional HTTP %s y restablece el botón', (status, mensaje) => {
    prepararCarrito();
    fixture.nativeElement.querySelector('.catalog-cart-button').click();
    peticionCarrito().flush({ error: mensaje }, { status: Number(status), statusText: 'Error' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain(mensaje);
    expect(fixture.nativeElement.querySelector('.catalog-cart-button').disabled).toBe(false);
    expect(fixture.componentInstance.exitoCarrito()).toBe('');
  });

  it('no muestra detalles técnicos de errores internos', () => {
    prepararCarrito();
    fixture.nativeElement.querySelector('.catalog-cart-button').click();
    peticionCarrito().flush({ error: 'MongoException: credenciales internas' }, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('MongoException');
    expect(fixture.componentInstance.errorCarrito()).toContain('No se pudo añadir');
  });

  it('informa de error de comunicación', () => {
    prepararCarrito();
    fixture.nativeElement.querySelector('.catalog-cart-button').click();
    peticionCarrito().error(new ProgressEvent('error'));
    expect(fixture.componentInstance.errorCarrito()).toContain('No se pudo conectar');
    expect(fixture.componentInstance.anadiendoProducto()).toBeNull();
  });

  it('muestra restricción Premium del servidor sin redirigir una sesión válida', () => {
    prepararCarrito();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.nativeElement.querySelector('.catalog-cart-button').click();
    peticionCarrito().flush({ error: 'Este producto solo está disponible para clientes Premium.' },
      { status: 403, statusText: 'Forbidden' });
    httpMock.expectOne(`${environment.apiUrl}/usuarios/sesion-actual`).flush({ email: 'cliente@example.com', role: 'CLIENTE' });
    expect(fixture.componentInstance.errorCarrito()).toContain('Premium');
    expect(navegar).not.toHaveBeenCalled();
  });

  it.each([401, 403])('redirige al login al detectar sesión inválida con HTTP %s', status => {
    prepararCarrito();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.nativeElement.querySelector('.catalog-cart-button').click();
    peticionCarrito().flush({}, { status, statusText: 'Unauthorized' });
    if (status === 403) {
      httpMock.expectOne(`${environment.apiUrl}/usuarios/sesion-actual`)
        .flush({}, { status: 403, statusText: 'Forbidden' });
    }
    expect(navegar).toHaveBeenCalledWith(['/login'], { queryParams: { motivo: 'sesion-expirada' } });
  });

  it('deshabilita añadir productos agotados y exclusivos Premium', () => {
    httpMock.expectOne(`${environment.apiUrl}/cliente/productos`).flush([
      crearProducto('agotado', false, true, 0), crearProducto('premium', true, false, 5)
    ]);
    fixture.detectChanges();
    for (const boton of fixture.nativeElement.querySelectorAll('.catalog-cart-button')) {
      expect(boton.disabled).toBe(true);
    }
  });

  describe('detalle de producto con teclado (C2)', () => {
    async function abrirDetalle(): Promise<HTMLButtonElement> {
      httpMock.expectOne(`${environment.apiUrl}/cliente/productos`)
        .flush([crearProducto('teclado-1', false, true, 6)]);
      fixture.detectChanges();

      const verDetalle: HTMLButtonElement = fixture.nativeElement
        .querySelector('.product-actions .secondary-button');
      verDetalle.focus();
      verDetalle.click();
      httpMock.expectOne(`${environment.apiUrl}/cliente/productos/teclado-1`)
        .flush({ ...crearProducto('teclado-1', false, true, 6), esVisible: true });
      fixture.detectChanges();
      await fixture.whenStable();
      return verDetalle;
    }

    const pulsar = (key: string, shiftKey = false): KeyboardEvent => {
      const evento = new KeyboardEvent('keydown', { key, shiftKey, bubbles: true, cancelable: true });
      (document.activeElement ?? document.body).dispatchEvent(evento);
      fixture.detectChanges();
      return evento;
    };

    it('al abrirse lleva el foco al botón «Cerrar» del diálogo', async () => {
      await abrirDetalle();

      expect(document.activeElement?.classList).toContain('detail-close');
    });

    it('Escape cierra el detalle y devuelve el foco a «Ver detalle»', async () => {
      const verDetalle = await abrirDetalle();

      pulsar('Escape');

      expect(fixture.nativeElement.querySelector('.product-detail')).toBeNull();
      expect(document.activeElement).toBe(verDetalle);
    });

    it('cerrar con el botón también devuelve el foco a «Ver detalle»', async () => {
      const verDetalle = await abrirDetalle();

      fixture.nativeElement.querySelector('.detail-close').click();
      fixture.detectChanges();

      expect(document.activeElement).toBe(verDetalle);
    });

    it('Tab no sale del diálogo: del último vuelve al primero y Mayús+Tab al revés', async () => {
      await abrirDetalle();
      const dialogo: HTMLElement = fixture.nativeElement.querySelector('.product-detail');
      const enfocables = Array.from(dialogo.querySelectorAll<HTMLElement>(
        'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
      ));
      const primero = enfocables[0];
      const ultimo = enfocables[enfocables.length - 1];

      ultimo.focus();
      expect(pulsar('Tab').defaultPrevented).toBe(true);
      expect(document.activeElement).toBe(primero);

      expect(pulsar('Tab', true).defaultPrevented).toBe(true);
      expect(document.activeElement).toBe(ultimo);
    });
  });

  describe('rediseño del catálogo (UI-cliente)', () => {
    const cargarCatalogo = (productos: ProductoCatalogo[]) => {
      httpMock.expectOne(`${environment.apiUrl}/cliente/productos`).flush(productos);
      fixture.detectChanges();
    };

    it('busca al escribir, con 300 ms de espera y una sola petición (M22)', () => {
      vi.useFakeTimers();
      try {
        cargarCatalogo([crearProducto('a', false, true, 5)]);
        const busqueda: HTMLInputElement = fixture.nativeElement.querySelector('input[type="search"]');

        for (const texto of ['r', 'ra', 'raton']) {
          busqueda.value = texto;
          busqueda.dispatchEvent(new Event('input'));
        }
        vi.advanceTimersByTime(299);
        httpMock.expectNone((r) => r.url === `${environment.apiUrl}/cliente/productos`);

        vi.advanceTimersByTime(1);
        const peticion = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/cliente/productos`);
        expect(peticion.request.params.get('nombre')).toBe('raton');
        peticion.flush([]);
      } finally {
        vi.useRealTimers();
      }
    });

    it('el botón «Filtros» indica cuántos hay activos y si están desplegados (A14)', () => {
      cargarCatalogo([crearProducto('a', false, true, 5)]);
      const boton: HTMLButtonElement = fixture.nativeElement.querySelector('.boton-filtros');
      expect(boton.textContent?.trim()).toBe('Filtros');
      expect(boton.getAttribute('aria-expanded')).toBe('false');

      const categoria: HTMLSelectElement = fixture.nativeElement.querySelector('#filtros-avanzados select');
      categoria.value = 'Informática';
      categoria.dispatchEvent(new Event('change'));
      boton.click();
      fixture.detectChanges();

      expect(boton.textContent?.trim()).toBe('Filtros (1)');
      expect(boton.getAttribute('aria-expanded')).toBe('true');
      expect(fixture.nativeElement.querySelector('#filtros-avanzados').classList).toContain('abiertos');
    });

    it('sin imagen o si falla, la tarjeta muestra un marcador en un marco fijo (M7)', () => {
      const conImagen = { ...crearProducto('img', false, true, 5), imagen: 'rota.png' };
      cargarCatalogo([crearProducto('sin', false, true, 5), conImagen]);
      const tarjetas = fixture.nativeElement.querySelectorAll('.product-card');

      expect(tarjetas[0].querySelector('.marco-imagen svg')).not.toBeNull();
      tarjetas[1].querySelector('img').dispatchEvent(new Event('error'));
      fixture.detectChanges();
      expect(tarjetas[1].querySelector('img')).toBeNull();
      expect(tarjetas[1].querySelector('.marco-imagen').textContent).toContain('Producto sin imagen');
    });

    it('sin resultados ofrece «Limpiar filtros» junto al mensaje (B4)', () => {
      cargarCatalogo([crearProducto('a', false, true, 5)]);
      fixture.componentInstance.soloFavoritos.set(true);
      fixture.detectChanges();

      const estado: HTMLElement = fixture.nativeElement.querySelector('.estado');
      expect(estado.textContent).toContain('No tienes productos favoritos');
      expect(estado.querySelector('button')?.textContent).toContain('Limpiar filtros');
    });

    it('el detalle no muestra descuentos del 0 % (B7)', () => {
      cargarCatalogo([crearProducto('cero', false, true, 5)]);
      fixture.nativeElement.querySelector('.product-actions .secondary-button').click();
      httpMock.expectOne(`${environment.apiUrl}/cliente/productos/cero`)
        .flush({ ...crearProducto('cero', false, true, 5), descuento: 0, descuentoPremium: 0 });
      fixture.detectChanges();

      const detalle: HTMLElement = fixture.nativeElement.querySelector('.product-detail');
      expect(detalle.textContent).not.toContain('Descuento');
    });
  });

  describe('listas de productos HU-39', () => {
    const urlListas = `${environment.apiUrl}/usuarios/listas`;
    const listas: ResumenLista[] = [
      { id: 'favoritos:c1', nombre: 'Favoritos', predeterminada: true, productoIds: [] },
      { id: 'l1', nombre: 'Comida', predeterminada: false, productoIds: [] },
      { id: 'l2', nombre: 'Regalos', predeterminada: false, productoIds: ['lista-1'] }
    ];

    function abrirDetalle(respuestaListas: ResumenLista[] = listas) {
      const producto = crearProducto('lista-1', false, true, 10);
      httpMock.expectOne(`${environment.apiUrl}/cliente/productos`).flush([producto]);
      fixture.detectChanges();
      fixture.nativeElement.querySelector('.product-card .product-actions .secondary-button').click();
      httpMock.expectOne(urlListas).flush(respuestaListas);
      httpMock.expectOne(`${environment.apiUrl}/cliente/productos/lista-1`).flush(producto);
      fixture.detectChanges();
      return producto;
    }

    function botonLista(): HTMLButtonElement {
      return fixture.nativeElement.querySelector('.detail-lists button');
    }

    it('ofrece solo las listas propias, sin favoritos, y preselecciona la primera', () => {
      abrirDetalle();

      const opciones = [...fixture.nativeElement.querySelectorAll('#lista-destino option')]
        .map((opcion: HTMLOptionElement) => opcion.textContent?.trim());
      expect(opciones).toEqual(['Comida', 'Regalos']);
      expect(fixture.componentInstance.listaSeleccionada()).toBe('l1');
      expect(botonLista().textContent).toContain('Añadir a la lista');
    });

    it('añade el producto a la lista elegida con CSRF y lo marca como incluido', () => {
      abrirDetalle();

      botonLista().click();
      httpMock.expectOne(`${environment.apiUrl}/usuarios/csrf`).flush({ token: 'csrf' });
      const peticion = httpMock.expectOne(`${urlListas}/l1/productos/lista-1`);
      expect(peticion.request.method).toBe('PUT');
      expect(peticion.request.headers.get('X-XSRF-TOKEN')).toBe('csrf');
      peticion.flush({
        id: 'l1', nombre: 'Comida', predeterminada: false,
        productos: [{ id: 'lista-1', nombre: 'Producto', imagen: null, precio: 25, stock: 10, disponible: true, aviso: null }]
      });
      fixture.detectChanges();

      expect(fixture.componentInstance.mensajeListas()).toBe('Producto añadido a «Comida».');
      expect(botonLista().textContent).toContain('Quitar de la lista');
    });

    it('quita el producto si ya estaba en la lista seleccionada', () => {
      abrirDetalle();
      const selector: HTMLSelectElement = fixture.nativeElement.querySelector('#lista-destino');
      selector.value = 'l2';
      selector.dispatchEvent(new Event('change'));
      fixture.detectChanges();
      expect(botonLista().textContent).toContain('Quitar de la lista');

      botonLista().click();
      httpMock.expectOne(`${environment.apiUrl}/usuarios/csrf`).flush({ token: 'csrf' });
      const peticion = httpMock.expectOne(`${urlListas}/l2/productos/lista-1`);
      expect(peticion.request.method).toBe('DELETE');
      peticion.flush({ id: 'l2', nombre: 'Regalos', predeterminada: false, productos: [] });
      fixture.detectChanges();

      expect(fixture.componentInstance.mensajeListas()).toBe('Producto quitado de «Regalos».');
      expect(botonLista().textContent).toContain('Añadir a la lista');
    });

    it('invita a crear una lista cuando solo existe favoritos', () => {
      abrirDetalle([listas[0]]);

      expect(fixture.nativeElement.querySelector('.detail-lists').textContent)
        .toContain('No tienes listas propias.');
      expect(fixture.nativeElement.querySelector('#lista-destino')).toBeNull();
    });

    it('muestra un error si el producto ya no está disponible', () => {
      abrirDetalle();

      botonLista().click();
      httpMock.expectOne(`${environment.apiUrl}/usuarios/csrf`).flush({ token: 'csrf' });
      httpMock.expectOne(`${urlListas}/l1/productos/lista-1`)
        .flush({ error: 'No se ha encontrado el producto solicitado.' }, { status: 404, statusText: 'Not Found' });
      fixture.detectChanges();

      expect(fixture.componentInstance.errorListas()).toBe('El producto o la lista ya no están disponibles.');
      expect(fixture.componentInstance.modificandoLista()).toBe(false);
    });
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
