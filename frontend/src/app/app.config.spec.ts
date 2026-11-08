import { DatePipe, DecimalPipe } from '@angular/common';
import { LOCALE_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { appConfig } from './app.config';

describe('appConfig · formato español (A15)', () => {
  it('fija el locale es-ES para toda la aplicación', () => {
    TestBed.configureTestingModule({ providers: appConfig.providers });

    expect(TestBed.inject(LOCALE_ID)).toBe('es-ES');
  });

  it('los precios usan coma decimal y punto de miles, y las fechas día/mes/año', () => {
    const numero = new DecimalPipe('es-ES');
    const fecha = new DatePipe('es-ES');

    expect(numero.transform(12345.6, '1.2-2')).toBe('12.345,60');
    expect(numero.transform(0.7, '1.2-2')).toBe('0,70');
    expect(fecha.transform('2026-11-06T09:05:00', 'dd/MM/yyyy HH:mm')).toBe('06/11/2026 09:05');
  });
});
