import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-panel-administrador',
  standalone: true,
  imports: [RouterLink],
  styleUrls: ['../tienda/tienda.css', './administrador.css'],
  template: `
    <main class="pagina">
      <header class="pagina-cabecera">
        <div>
          <h1 class="pagina-titulo">Bienvenido, administrador</h1>
          <p class="pagina-descripcion">
            Consulta y gestiona la información administrativa de ESIBuy.
          </p>
        </div>
      </header>

      <div class="overview-grid">
        <article class="tarjeta">
          <h2>Usuarios</h2>
          <p>Consulta administrativa</p>
          <a class="boton-secundario" routerLink="/admin/usuarios">Abrir listado</a>
        </article>

        <article class="tarjeta">
          <h2>Categorías</h2>
          <p>Consulta administrativa</p>
          <a class="boton-secundario" routerLink="/admin/categorias">Abrir categorías</a>
        </article>

        <article class="tarjeta">
          <h2>Productos</h2>
          <p>Consulta en solo lectura</p>
          <a class="boton-secundario" routerLink="/admin/productos">Abrir productos</a>
        </article>

        <article class="tarjeta">
          <h2>Administradores</h2>
          <p>Gestión interna</p>
          <a class="boton-secundario" routerLink="/admin/administradores">Abrir administradores</a>
        </article>
      </div>
    </main>
  `
})
export class PanelAdministrador {}
