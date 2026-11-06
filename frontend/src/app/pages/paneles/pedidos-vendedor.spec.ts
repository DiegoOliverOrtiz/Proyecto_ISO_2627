import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import {
  afterEach,
  beforeEach,
  describe,
  expect,
  it,
  vi
} from 'vitest';

import { PedidosVendedor } from './pedidos-vendedor';
import {
  PedidoVendedor,
  PedidosVendedorService
} from './pedidos-vendedor.service';

describe('PedidosVendedor - HU-35', () => {
  let fixture: ComponentFixture<PedidosVendedor>;
  let componente: PedidosVendedor;

  const pedido: PedidoVendedor = {
    id: 'pedido-mixto',
    clienteNombre: 'cliente eliminado',
    estado: 'PAGADO_COMPLETADO',
    fechaCreacion: '2026-11-02T12:00:00Z',
    fechaPago: '2026-11-02T12:05:00Z',
    productos: [
      {
        productoId: 'roncola',
        nombreProducto: 'Roncola',
        precioCompra: 5,
        cantidad: 1
      }
    ],
    importeProductosPropios: 5,
    direccionEnvio: {
      id: 'direccion-1',
      nombre: 'Casa',
      calle: 'Calle Principal',
      numero: '2',
      portal: null,
      piso: null,
      codigoPostal: '45001',
      ciudad: 'Toledo',
      provincia: 'Toledo'
    }
  };

  const historico: PedidoVendedor = {
    ...pedido,
    id: 'pedido-finalizado',
    estado: 'FINALIZADO'
  };

  const servicioMock = {
    consultarActivos: vi.fn(),
    consultarHistorico: vi.fn(),
    consultarDetalle: vi.fn(),
    aceptarPedido: vi.fn()
  };

  function texto(): string {
    fixture.detectChanges();
    return fixture.nativeElement.textContent as string;
  }

  beforeEach(async () => {
    vi.clearAllMocks();

    servicioMock.consultarActivos.mockReturnValue(of([pedido]));
    servicioMock.consultarHistorico.mockReturnValue(of([historico]));
    servicioMock.consultarDetalle.mockReturnValue(of(pedido));

    await TestBed.configureTestingModule({
      imports: [PedidosVendedor],
      providers: [
        provideRouter([]),
        {
          provide: PedidosVendedorService,
          useValue: servicioMock
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(PedidosVendedor);
    componente = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('carga automáticamente los pedidos activos', () => {
    expect(servicioMock.consultarActivos).toHaveBeenCalledTimes(1);
    expect(componente.pedidos()).toEqual([pedido]);
    expect(componente.pestana()).toBe('activos');
    expect(texto()).toContain('Pedido #-MIXTO');
    expect(texto()).toContain('Cliente: cliente eliminado');
    expect(texto()).toContain('5.00 €');
  });

  it('muestra la referencia corta en la lista y la completa en el detalle (M6)', () => {
    const idLargo = 'a'.repeat(58) + 'b1c2d3';
    servicioMock.consultarActivos.mockReturnValue(of([{ ...pedido, id: idLargo }]));
    componente.cargarPedidos();
    fixture.detectChanges();

    const titulo: HTMLElement = fixture.nativeElement.querySelector('.tarjeta-pedido h3');
    expect(titulo.textContent?.trim()).toBe('Pedido #B1C2D3');
    expect(texto()).not.toContain(idLargo);

    componente.pedidoSeleccionado.set({ ...pedido, id: idLargo });
    fixture.detectChanges();
    expect(texto()).toContain(`Referencia: ${idLargo}`);
  });

  it('cambia al histórico y muestra los pedidos finalizados', () => {
    componente.cambiarPestana('historico');

    expect(servicioMock.consultarHistorico).toHaveBeenCalledTimes(1);
    expect(componente.pestana()).toBe('historico');
    expect(componente.pedidos()).toEqual([historico]);
    expect(texto()).toContain('Finalizado');
  });

  it('muestra el mensaje cuando no hay pedidos activos', () => {
    servicioMock.consultarActivos.mockReturnValue(of([]));

    componente.cargarPedidos();

    expect(componente.pedidos()).toEqual([]);
    expect(texto()).toContain('No tienes pedidos activos');
  });

  it('muestra el mensaje cuando el histórico está vacío', () => {
    servicioMock.consultarHistorico.mockReturnValue(of([]));

    componente.cambiarPestana('historico');

    expect(componente.pedidos()).toEqual([]);
    expect(texto()).toContain('Tu histórico está vacío');
  });

  it('permite cerrar el detalle', () => {
    componente.abrirDetalle(pedido);
    expect(componente.pedidoSeleccionado()).not.toBeNull();

    componente.cerrarDetalle();

    expect(componente.pedidoSeleccionado()).toBeNull();
    expect(texto()).not.toContain('Detalle del pedido');
  });

  it('muestra un error cuando falla la consulta de pedidos', () => {
    servicioMock.consultarActivos.mockReturnValue(
      throwError(() => new HttpErrorResponse({
        status: 500,
        statusText: 'Internal Server Error'
      }))
    );

    componente.cargarPedidos();

    expect(componente.cargando()).toBe(false);
    expect(texto()).toContain(
      'Ha ocurrido un error al consultar los pedidos.'
    );
  });

  it('informa de la falta de permisos', () => {
    servicioMock.consultarActivos.mockReturnValue(
      throwError(() => new HttpErrorResponse({
        status: 403,
        statusText: 'Forbidden'
      }))
    );

    componente.cargarPedidos();

    expect(texto()).toContain(
      'No tienes permiso para consultar estos pedidos.'
    );
  });

  it('muestra un 404 al intentar abrir un pedido no disponible', () => {
    servicioMock.consultarDetalle.mockReturnValue(
      throwError(() => new HttpErrorResponse({
        status: 404,
        statusText: 'Not Found'
      }))
    );

    componente.abrirDetalle(pedido);

    expect(componente.pedidoSeleccionado()).toBeNull();
    expect(componente.cargandoDetalle()).toBe(false);
    expect(texto()).toContain(
      'El pedido no existe o no está disponible.'
    );
  });

  it('muestra la carga mientras espera la respuesta', () => {
    const respuesta = new Subject<PedidoVendedor[]>();

    servicioMock.consultarActivos.mockReturnValue(
      respuesta.asObservable()
    );

    componente.cargarPedidos();

    expect(componente.cargando()).toBe(true);
    expect(texto()).toContain('Cargando pedidos...');

    respuesta.next([pedido]);
    respuesta.complete();

    expect(componente.cargando()).toBe(false);
    expect(texto()).toContain('Pedido #-MIXTO');
  });

  it('no sobrescribe el histórico con una respuesta antigua', () => {
    const respuestaActivos = new Subject<PedidoVendedor[]>();

    servicioMock.consultarActivos.mockReturnValue(
      respuestaActivos.asObservable()
    );

    componente.cargarPedidos();
    componente.cambiarPestana('historico');

    expect(componente.pedidos()).toEqual([historico]);

    respuestaActivos.next([pedido]);
    respuestaActivos.complete();

    expect(componente.pestana()).toBe('historico');
    expect(componente.pedidos()).toEqual([historico]);
  });

  it('redirige al login cuando la sesión ha caducado', () => {
    const router = TestBed.inject(Router);
    const navegar = vi.spyOn(router, 'navigateByUrl')
      .mockResolvedValue(true);

    servicioMock.consultarActivos.mockReturnValue(
      throwError(() => new HttpErrorResponse({
        status: 401,
        statusText: 'Unauthorized'
      }))
    );

    componente.cargarPedidos();

    expect(navegar).toHaveBeenCalledWith('/login');
    expect(componente.cargando()).toBe(false);
  });

  describe('HU-32 aceptar pedido', () => {
    const enviado: PedidoVendedor = { ...pedido, estado: 'TRAMITADO_ENVIADO' };

    function botonesAceptar(): HTMLButtonElement[] {
      fixture.detectChanges();
      return Array.from(
        fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>
      ).filter(boton => boton.textContent?.includes('Aceptar y enviar'));
    }

    it('muestra «Aceptar y enviar» solo en pedidos pagados', () => {
      expect(botonesAceptar()).toHaveLength(1);

      servicioMock.consultarActivos.mockReturnValue(of([enviado]));
      componente.cargarPedidos();

      expect(botonesAceptar()).toHaveLength(0);
      expect(texto()).toContain('Enviado');
    });

    it('no muestra la acción en el histórico', () => {
      componente.cambiarPestana('historico');

      expect(botonesAceptar()).toHaveLength(0);
    });

    it('acepta el pedido, informa del resultado y recarga la lista', () => {
      servicioMock.aceptarPedido.mockReturnValue(of(enviado));
      servicioMock.consultarActivos.mockReturnValue(of([enviado]));

      botonesAceptar()[0].click();

      expect(servicioMock.aceptarPedido).toHaveBeenCalledWith('pedido-mixto');
      expect(servicioMock.consultarActivos).toHaveBeenCalledTimes(2);
      expect(componente.pedidoEnProceso()).toBeNull();
      expect(texto()).toContain(
        'Pedido #-MIXTO aceptado. El envío (simulado) está en curso.'
      );
      expect(componente.pedidos()).toEqual([enviado]);
    });

    it('actualiza el detalle abierto tras aceptar desde él', () => {
      servicioMock.aceptarPedido.mockReturnValue(of(enviado));
      componente.abrirDetalle(pedido);
      expect(texto()).toContain('Detalle del pedido');

      componente.aceptarPedido(pedido);

      expect(componente.pedidoSeleccionado()?.estado).toBe('TRAMITADO_ENVIADO');
      expect(texto()).not.toContain('cliente ya no podrá cancelarlo');
    });

    it('desactiva la acción mientras se procesa y evita envíos dobles', () => {
      const respuesta = new Subject<PedidoVendedor>();
      servicioMock.aceptarPedido.mockReturnValue(respuesta.asObservable());

      componente.aceptarPedido(pedido);
      componente.aceptarPedido(pedido);

      expect(servicioMock.aceptarPedido).toHaveBeenCalledTimes(1);
      expect(componente.pedidoEnProceso()).toBe('pedido-mixto');
      expect(texto()).toContain('Aceptando...');

      respuesta.next(enviado);
      respuesta.complete();

      expect(componente.pedidoEnProceso()).toBeNull();
    });

    it('no intenta aceptar un pedido que no está pagado', () => {
      componente.aceptarPedido(enviado);

      expect(servicioMock.aceptarPedido).not.toHaveBeenCalled();
    });

    it('informa del 409 y refresca la lista', () => {
      servicioMock.aceptarPedido.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 409, statusText: 'Conflict' }))
      );

      componente.aceptarPedido(pedido);

      expect(servicioMock.consultarActivos).toHaveBeenCalledTimes(2);
      expect(componente.mensajeExito()).toBe('');
      expect(texto()).toContain('El pedido ya no está pendiente de tramitar.');
    });

    it('informa de la falta de permisos al aceptar', () => {
      servicioMock.aceptarPedido.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 403, statusText: 'Forbidden' }))
      );

      componente.aceptarPedido(pedido);

      expect(texto()).toContain('No tienes permiso para gestionar este pedido.');
    });

    it('el detalle de un pedido enviado no ofrece la acción', () => {
      servicioMock.consultarActivos.mockReturnValue(of([enviado]));
      servicioMock.consultarDetalle.mockReturnValue(of(enviado));
      componente.cargarPedidos();

      componente.abrirDetalle(enviado);

      expect(texto()).toContain('Detalle del pedido');
      expect(botonesAceptar()).toHaveLength(0);
    });

    it('redirige al login si la sesión caduca al aceptar', () => {
      const router = TestBed.inject(Router);
      const navegar = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
      servicioMock.aceptarPedido.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 401, statusText: 'Unauthorized' }))
      );

      componente.aceptarPedido(pedido);

      expect(navegar).toHaveBeenCalledWith('/login');
      expect(componente.pedidoEnProceso()).toBeNull();
      expect(componente.mensajeExito()).toBe('');
    });

    it('informa del 404 al aceptar un pedido no disponible', () => {
      servicioMock.aceptarPedido.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 404, statusText: 'Not Found' }))
      );

      componente.aceptarPedido(pedido);

      expect(texto()).toContain('El pedido no existe o no está disponible.');
    });
  });
  it('si falla la carga ofrece «Reintentar» y vuelve a consultar (M5)', () => {
    servicioMock.consultarActivos.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500 }))
    );
    componente.cargarPedidos();
    fixture.detectChanges();

    const reintentar = fixture.nativeElement.querySelector('.estado[role="alert"] button') as HTMLButtonElement;
    expect(reintentar.textContent?.trim()).toBe('Reintentar');

    servicioMock.consultarActivos.mockReturnValue(of([pedido]));
    reintentar.click();
    fixture.detectChanges();

    expect(componente.pedidos()).toEqual([pedido]);
    expect(texto()).toContain('Pedido #');
  });
});
