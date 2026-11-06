import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-panel-administrador',
  standalone: true,
  imports: [RouterLink],
  styleUrl: './administrador.css',
  template: `
    <main class="admin-shell">
      <section class="admin-content">
        <header class="topbar">
          <span class="breadcrumb">
            Gestión
            <span aria-hidden="true">/</span>
            Panel de administración
          </span>
        </header>

        <div class="welcome-block">
          <p class="eyebrow">Espacio de gestión</p>
          <h1>Bienvenido, administrador</h1>
          <p>
            Consulta y gestiona la información administrativa de EsiBuy.
          </p>

          <a class="primary-action" routerLink="/admin/usuarios">
            Consultar usuarios
          </a>
        </div>

        <div class="overview-grid">
          <article>
            <span class="card-label">Área activa</span>
            <strong>Usuarios</strong>
            <small>Consulta administrativa</small>

            <a routerLink="/admin/usuarios">
              Abrir listado
              <span aria-hidden="true">→</span>
            </a>
          </article>

          <article>
            <span class="card-label">Área activa</span>
            <strong>Categorías</strong>
            <small>Consulta administrativa</small>

            <a routerLink="/admin/categorias">
              Abrir categorías
              <span aria-hidden="true">→</span>
            </a>
          </article>

          <article>
            <span class="card-label">Área activa</span>
            <strong>Productos</strong>
            <small>Consulta en solo lectura</small>

            <a routerLink="/admin/productos">
              Abrir productos
              <span aria-hidden="true">→</span>
            </a>
          </article>

          <article>
            <span class="card-label">Área activa</span>
            <strong>Administradores</strong>
            <small>Gestión interna</small>

            <a routerLink="/admin/administradores">
              Abrir administradores
              <span aria-hidden="true">→</span>
            </a>
          </article>
        </div>

      </section>
    </main>
  `
})
export class PanelAdministrador {}
