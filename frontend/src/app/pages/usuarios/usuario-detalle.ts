import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  ModificarUsuarioRequest,
  UsuarioAdministracion,
  UsuariosService
} from './usuarios.service';
import { AdministradorService } from '../admin/administrador.service';

function mayorDeEdadValidator(
  control: AbstractControl
): ValidationErrors | null {

  const valor = control.value;

  if (!valor) {
    return null;
  }

  const partes = valor.split('-');

  if (partes.length !== 3) {
    return { fechaInvalida: true };
  }

  const anio = Number(partes[0]);
  const mes = Number(partes[1]);
  const dia = Number(partes[2]);

  const nacimiento = new Date(
    anio,
    mes - 1,
    dia
  );

  if (Number.isNaN(nacimiento.getTime())) {
    return { fechaInvalida: true };
  }

  const hoy = new Date();

  let edad =
    hoy.getFullYear() -
    nacimiento.getFullYear();

  const diferenciaMes =
    hoy.getMonth() -
    nacimiento.getMonth();

  if (
    diferenciaMes < 0 ||
    (
      diferenciaMes === 0 &&
      hoy.getDate() < nacimiento.getDate()
    )
  ) {
    edad--;
  }

  return edad >= 18
    ? null
    : { menorDeEdad: true };
}

@Component({
  selector: 'app-usuario-detalle',
  standalone: true,
  imports: [
    RouterLink,
    ReactiveFormsModule
  ],
  templateUrl: './usuario-detalle.html',
  styleUrl: './usuario-detalle.css'
})
export class UsuarioDetalle implements OnInit {

  private readonly route =
    inject(ActivatedRoute);

  private readonly router =
    inject(Router);

  private readonly usuariosService =
    inject(UsuariosService);

  private readonly administradorService =
    inject(AdministradorService);

  private readonly formBuilder =
    inject(FormBuilder);

  readonly usuario =
    signal<UsuarioAdministracion | null>(
      null
    );

  readonly cargando =
    signal(true);

  readonly mensajeError =
    signal('');

  readonly mensajeFormulario =
    signal('');

  readonly modoEdicion =
    signal(false);

  readonly volverUrl =
    signal('/admin/usuarios');

  readonly formulario =
    this.formBuilder.nonNullable.group({

      nombre: [
        '',
        Validators.required
      ],

      apellidos: [
        '',
        Validators.required
      ],

      telefono: [''],

      imagenPerfil: [''],

      dniNif: [''],

      fechaNacimiento: [
        '',
        mayorDeEdadValidator
      ],

      sede: ['']

    });

  ngOnInit(): void {

    const id =
      this.route.snapshot.paramMap.get(
        'id'
      );

    if (!id) {

      this.cargando.set(false);

      this.mensajeError.set(
        'No se ha indicado el usuario que quieres consultar.'
      );

      return;
    }

    this.usuariosService
      .consultarUsuario(id)
      .subscribe({

        next: (respuesta) => {

          this.usuario.set(
            respuesta
          );

          this.cargando.set(
            false
          );

          if (
            respuesta.rol === 'ADMIN'
          ) {

            this.volverUrl.set(
              '/admin/administradores'
            );

          }

          const editar =
            this.route.snapshot
              .queryParamMap
              .get('editar');

          if (editar === 'true') {

            this.iniciarEdicion(
              respuesta
            );

          }

        },

        error: (
          error: HttpErrorResponse
        ) => {

          this.cargando.set(false);

          this.mensajeError.set(
            this.mensajeParaError(
              error
            )
          );

        }

      });

  }

  iniciarEdicion(
    usuario: UsuarioAdministracion
  ): void {

    this.mensajeFormulario.set(
      ''
    );

    this.formulario.reset({

      nombre:
        usuario.nombre ?? '',

      apellidos:
        usuario.apellidos ?? '',

      telefono:
        usuario.telefono ?? '',

      imagenPerfil:
        usuario.imagenPerfil ?? '',

      dniNif:
        usuario.dniNif ?? '',

      fechaNacimiento:
        usuario.fechaNacimiento ?? '',

      sede:
        usuario.sede ?? ''

    });

    this.modoEdicion.set(true);

  }

  cancelarEdicion(): void {

    this.modoEdicion.set(false);

    this.mensajeFormulario.set('');

    this.formulario.reset();

  }

  guardarCambios(): void {

    if (this.formulario.invalid) {

      this.formulario
        .markAllAsTouched();

      return;
    }

    const usuarioActual =
      this.usuario();

    if (!usuarioActual) {
      return;
    }

    const valores =
      this.formulario
        .getRawValue();

    const datos:
      ModificarUsuarioRequest = {

      nombre:
        valores.nombre.trim(),

      apellidos:
        valores.apellidos.trim(),

      telefono:
        valores.telefono.trim()
        || null,

      imagenPerfil:
        valores.imagenPerfil.trim()
        || null

    };

    if (
      usuarioActual.rol ===
      'CLIENTE'
    ) {

      datos.dniNif =
        valores.dniNif.trim()
        || null;

      datos.fechaNacimiento =
        valores.fechaNacimiento
        || null;

    }

    if (
      usuarioActual.rol ===
      'VENDEDOR'
    ) {

      datos.dniNif =
        valores.dniNif.trim()
        || null;

    }

    if (
      usuarioActual.rol ===
      'ADMIN'
    ) {

      datos.sede =
        valores.sede.trim()
        || null;

    }

    this.mensajeFormulario.set('');

    this.usuariosService
      .modificarUsuario(
        usuarioActual.id,
        datos
      )
      .subscribe({

        next: (respuesta) => {

          this.usuario.set(
            respuesta
          );

          this.modoEdicion.set(
            false
          );

          window.alert(
            'Los datos del usuario se han actualizado correctamente.'
          );

        },

        error: (
          error: HttpErrorResponse
        ) => {

          if (error.status === 400) {

            this.mensajeFormulario.set(
              error.error?.error ||
              'Los datos introducidos no son válidos.'
            );

            return;
          }

          if (error.status === 404) {

            this.mensajeFormulario.set(
              'No se ha encontrado el usuario.'
            );

            return;
          }

          if (
            error.status === 401 ||
            error.status === 403
          ) {

            this.mensajeFormulario.set(
              'No tienes permisos para modificar este usuario.'
            );

            return;
          }

          if (error.status === 0) {

            this.mensajeFormulario.set(
              'No se pudo conectar con el servicio.'
            );

            return;
          }

          this.mensajeFormulario.set(
            'No se pudieron guardar los cambios.'
          );

        }

      });

  }

  eliminarAdministrador(
    id: string,
    nombre: string
  ): void {

    const seguro =
      window.confirm(
        `¿Estás seguro de que deseas eliminar al administrador ${nombre}?\n\nEsta es una acción destructiva y no se puede deshacer.`
      );

    if (!seguro) {
      return;
    }

    this.administradorService
      .eliminarAdministrador(id)
      .subscribe({

        next: () => {

          void this.router.navigate([
            '/admin/administradores'
          ]);

        },

        error: (
          error: HttpErrorResponse
        ) => {

          const mensajeBackend =
            error.error?.error;

          window.alert(
            mensajeBackend ||
            'No se pudo eliminar el administrador. Inténtalo de nuevo.'
          );

        }

      });

  }

  private mensajeParaError(
    error: HttpErrorResponse
  ): string {

    if (error.status === 404) {

      return 'No se ha encontrado el usuario solicitado.';

    }

    if (
      error.status === 401 ||
      error.status === 403
    ) {

      return 'No tienes permisos para consultar este usuario.';

    }

    if (error.status === 0) {

      return 'No se pudo conectar con el servicio. Inténtalo de nuevo.';

    }

    return 'No se pudo cargar la información del usuario.';

  }

}