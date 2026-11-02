import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { PagosService, MetodoPagoRequest, TipoPago, MetodoPagoResponse } from './pagos.service';

@Component({
  selector: 'app-pagos',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  styleUrl: './pagos.css',
  templateUrl: './pagos.html'
})
export class Pagos {
  private readonly servicioPagos = inject(PagosService);
  private readonly constructorFormularios = inject(FormBuilder);
  private readonly enrutador = inject(Router);

  readonly tipos = signal<TipoPago[]>([]);
  readonly metodosGuardados = signal<MetodoPagoResponse[]>([]);
  readonly cargando = signal(true);
  readonly guardando = signal(false);
  readonly editando = signal(false);
  
  readonly mensajeError = signal('');
  readonly mensajeExito = signal('');
  readonly mensajeFormulario = signal('');

  readonly formulario = this.constructorFormularios.nonNullable.group({
    tipo: ['', [Validators.required]],
    nombre: ['', [Validators.required, Validators.maxLength(50)]],
    numeroTarjeta: [''],
    titular: [''],
    caducidad: [''],
    cvv: [''],
    correoPaypal: [''],
    telefonoBizum: ['']
  });

  constructor() {
    this.cargarDatos();
  }

  cargarDatos(): void {
    this.cargando.set(true);
    this.mensajeError.set('');

    this.servicioPagos.consultarTipos().subscribe({
      next: (tiposRes) => {
        this.tipos.set(tiposRes);
        this.cargarMetodos();
      },
      error: (error: HttpErrorResponse) => {
        this.cargando.set(false);
        this.mensajeError.set(this.obtenerMensajeError(error, 'No se pudieron cargar los tipos de pago disponibles.'));
      }
    });
  }

  cargarMetodos(): void {
    this.servicioPagos.consultarMetodos().subscribe({
      next: (metodosRes) => {
        this.metodosGuardados.set(metodosRes);
        this.cargando.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.cargando.set(false);
        this.mensajeError.set(this.obtenerMensajeError(error, 'No se pudieron cargar tus métodos de pago.'));
      }
    });
  }

  iniciarCreacion(): void {
    this.formulario.reset({ tipo: '' });
    this.mensajeFormulario.set('');
    this.mensajeExito.set('');
    this.editando.set(true);
  }

  cancelarEdicion(): void {
    this.editando.set(false);
    this.mensajeFormulario.set('');
  }

  tipoSeleccionado(): string {
    return this.formulario.controls.tipo.value;
  }

  formatearCaducidad(event: Event): void {
    const input = event.target as HTMLInputElement;
    let valor = input.value.replace(/\D/g, ''); 
    
    if (valor.length > 4) {
      valor = valor.substring(0, 4);
    }
    
    if (valor.length > 2) {
      valor = valor.substring(0, 2) + '/' + valor.substring(2);
    }
    
    input.value = valor;
    this.formulario.controls.caducidad.setValue(valor, { emitEvent: false });
  }

  guardarMetodo(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      this.mensajeFormulario.set('Revisa los campos marcados antes de guardar.');
      return;
    }

    const valores = this.formulario.getRawValue();
    const solicitud: MetodoPagoRequest = {
      tipo: valores.tipo,
      nombre: valores.nombre.trim(),
      numeroTarjeta: this.valorNulo(valores.numeroTarjeta),
      titular: this.valorNulo(valores.titular),
      caducidad: this.valorNulo(valores.caducidad),
      cvv: this.valorNulo(valores.cvv),
      correoPaypal: this.valorNulo(valores.correoPaypal),
      telefonoBizum: this.valorNulo(valores.telefonoBizum)
    };

    this.guardando.set(true);
    this.mensajeFormulario.set('');
    this.mensajeExito.set('');

    this.servicioPagos.registrarMetodo(solicitud).subscribe({
      next: () => {
        this.guardando.set(false);
        this.editando.set(false);
        this.mensajeExito.set('Método de pago registrado correctamente.');
        this.cargarMetodos();
      },
      error: (error: HttpErrorResponse) => {
        this.guardando.set(false);
        if (error.status === 400 && error.error?.errores) {
          const mensajes = Object.values(error.error.errores).flat().join(' ');
          this.mensajeFormulario.set(mensajes || 'Revisa los datos introducidos.');
        } else {
          this.mensajeFormulario.set(this.obtenerMensajeError(error, 'No se pudo guardar el método.'));
        }
      }
    });
  }

  eliminarMetodo(id: string): void {
    if (!window.confirm('¿Estás seguro de que deseas eliminar este método de pago?')) {
      return;
    }

    this.mensajeError.set('');
    this.mensajeExito.set('');
    this.servicioPagos.eliminarMetodo(id).subscribe({
      next: () => {
        this.mensajeExito.set('Método de pago eliminado correctamente.');
        this.cargarMetodos();
      },
      error: (error: HttpErrorResponse) => {
        this.mensajeError.set(this.obtenerMensajeError(error, 'No se pudo eliminar el método de pago.'));
      }
    });
  }

  private valorNulo(valor: string): string | null {
    const limpio = valor.trim();
    return limpio.length > 0 ? limpio : null;
  }

  private obtenerMensajeError(error: HttpErrorResponse, mensajeGenerico: string): string {
    if (error.status === 401) {
      void this.enrutador.navigate(['/login'], { queryParams: { motivo: 'sesion-expirada' } });
      return 'Tu sesión ya no es válida. Vuelve a iniciar sesión.';
    }
    if (error.status === 403) {
      return 'No tienes permiso para consultar estos métodos de pago.';
    }
    return error.error?.message ?? mensajeGenerico;
  }
}