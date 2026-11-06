import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { Pagos } from './pagos';
import { MetodoPagoResponse, PagosService, TipoPago } from './pagos.service';

const tipos: TipoPago[] = [
  { codigo: 'TARJETA', nombre: 'Tarjeta' },
  { codigo: 'PAYPAL', nombre: 'PayPal' },
  { codigo: 'BIZUM', nombre: 'Bizum' }
];

const metodos: MetodoPagoResponse[] = [
  { id: 'm1', tipo: 'TARJETA', nombre: 'Visa personal', datosEnmascarados: '•••• 4242', titular: 'Ana', caducidad: '12/28' },
  { id: 'm2', tipo: 'BIZUM', nombre: 'Bizum', datosEnmascarados: '••••• 3456' }
];

describe('Pagos', () => {
  let fixture: ComponentFixture<Pagos>;
  let servicio: {
    consultarTipos: ReturnType<typeof vi.fn>;
    consultarMetodos: ReturnType<typeof vi.fn>;
  };

  function crear(): HTMLElement {
    fixture = TestBed.createComponent(Pagos);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(async () => {
    servicio = {
      consultarTipos: vi.fn(() => of(tipos)),
      consultarMetodos: vi.fn(() => of(metodos))
    };
    await TestBed.configureTestingModule({
      imports: [Pagos],
      providers: [provideRouter([]), { provide: PagosService, useValue: servicio }]
    }).compileComponents();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('muestra el nombre legible del tipo en lugar del valor del enum (B7)', () => {
    const vista = crear();

    const tarjetas = vista.querySelectorAll('.gestion-tarjeta');
    expect(tarjetas.length).toBe(2);
    expect(tarjetas[0].textContent).toContain('Tipo: Tarjeta');
    expect(tarjetas[0].textContent).not.toContain('TARJETA');
    expect(tarjetas[1].textContent).toContain('Tipo: Bizum');
  });

  it('usa el código sin mayúsculas si el tipo no está en el catálogo', () => {
    crear();

    expect(fixture.componentInstance.nombreTipo('TRANSFERENCIA')).toBe('Transferencia');
  });

  it('invita a añadir un método cuando no hay ninguno', () => {
    servicio.consultarMetodos.mockReturnValue(of([]));
    const vista = crear();

    const estado = vista.querySelector('.estado');
    expect(estado?.textContent).toContain('Todavía no tienes métodos de pago.');
    expect(estado?.querySelector('button')?.textContent).toContain('Añadir método');
  });

  it('ofrece reintentar si falla la carga y oculta «Añadir método»', () => {
    servicio.consultarTipos.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const vista = crear();

    const estado = vista.querySelector('.estado[role="alert"]');
    expect(estado?.querySelector('button')?.textContent).toContain('Reintentar');
    expect(vista.querySelector('.pagina-cabecera')?.textContent).not.toContain('Añadir método');
  });
});
