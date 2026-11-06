import { Routes } from '@angular/router';
import { Home } from './pages/home/home';
import { Login } from './pages/login/login';
import { Register } from './pages/register/register';
import { RecuperacionContrasena } from './pages/recuperacion-contrasena/recuperacion-contrasena';
import { PanelAdministrador } from './pages/paneles/administrador';
import { PanelCliente } from './pages/paneles/cliente';
import { PanelVendedor } from './pages/paneles/vendedor';
import { rolGuard } from './pages/login/rol.guard';
import { UsuarioDetalle } from './pages/usuarios/usuario-detalle';
import { Usuarios } from './pages/usuarios/usuarios';
import { Categorias } from './pages/categorias/categorias';
import { Administradores } from './pages/admin/administradores';
import { CrearAdministrador } from './pages/admin/crear-administrador';
import { ProductosAdmin } from './pages/admin/productos-admin';
import { Perfil } from './pages/perfil/perfil';
import { autenticadoGuard } from './pages/login/autenticado.guard';
import { Carrito } from './pages/carrito/carrito';
import { Checkout } from './pages/checkout/checkout';
import { Favoritos } from './pages/favoritos/favoritos';
import { Listas } from './pages/listas/listas';

import { Direcciones } from './pages/direcciones/direcciones';
import { Pagos } from './pages/pagos/pagos';
import { PedidosVendedor } from './pages/paneles/pedidos-vendedor';
import { MisPedidos } from './pages/pedidos/mis-pedidos';
import { NoEncontrada } from './pages/no-encontrada/no-encontrada';

export const routes: Routes = [
  {
    path: '',
    title: 'Inicio · ESIBuy',
    component: Home,
    data: { cabecera: false }
  },
  {
    path: 'login',
    title: 'Iniciar sesión · ESIBuy',
    component: Login,
    data: { cabecera: false }
  },
  {
    path: 'registro',
    title: 'Crear cuenta · ESIBuy',
    component: Register,
    data: { cabecera: false }
  },
  {
    path: 'recuperar-contrasena',
    title: 'Recuperar contraseña · ESIBuy',
    component: RecuperacionContrasena,
    data: { cabecera: false }
  },
  {
    path: 'perfil',
    title: 'Mi perfil · ESIBuy',
    component: Perfil,
    canActivate: [autenticadoGuard]
  },
  {
    // HU-39: todo usuario tiene listas (al menos la de favoritos).
    path: 'listas',
    title: 'Mis listas · ESIBuy',
    component: Listas,
    canActivate: [autenticadoGuard]
  },
  {
    path: 'perfil/direcciones',
    title: 'Mis direcciones · ESIBuy',
    component: Direcciones,
    canActivate: [rolGuard],
    data: { rol: 'CLIENTE' }
  },

  {
    path: 'perfil/pagos',
    title: 'Métodos de pago · ESIBuy',
    component: Pagos,
    canActivate: [rolGuard],
    data: { rol: 'CLIENTE' }
  },
  {
    path: 'admin',
    title: 'Panel de administración · ESIBuy',
    component: PanelAdministrador,
    canActivate: [rolGuard],
    data: { rol: 'ADMIN' }
  },
  {
    path: 'admin/usuarios',
    title: 'Usuarios · ESIBuy',
    component: Usuarios,
    canActivate: [rolGuard],
    data: { rol: 'ADMIN' }
  },
  {
    path: 'admin/usuarios/:id',
    title: 'Detalle de usuario · ESIBuy',
    component: UsuarioDetalle,
    canActivate: [rolGuard],
    data: { rol: 'ADMIN' }
  },
  {
    path: 'admin/categorias',
    title: 'Categorías · ESIBuy',
    component: Categorias,
    canActivate: [rolGuard],
    data: { rol: 'ADMIN' }
  },
  {
    path: 'admin/administradores',
    title: 'Administradores · ESIBuy',
    component: Administradores,
    canActivate: [rolGuard],
    data: { rol: 'ADMIN' }
  },
  {
    path: 'admin/administradores/nuevo',
    title: 'Nuevo administrador · ESIBuy',
    component: CrearAdministrador,
    canActivate: [rolGuard],
    data: { rol: 'ADMIN' }
  },
  {
    // HU-34: catálogo de todos los vendedores en solo lectura.
    path: 'admin/productos',
    title: 'Productos · ESIBuy',
    component: ProductosAdmin,
    canActivate: [rolGuard],
    data: { rol: 'ADMIN' }
  },
  {
    path: 'cliente/favoritos',
    title: 'Mis favoritos · ESIBuy',
    component: Favoritos,
    canActivate: [rolGuard],
    data: { rol: 'CLIENTE' }
  },
  {
    path: 'carrito',
    title: 'Mi carrito · ESIBuy',
    component: Carrito,
    canActivate: [rolGuard],
    data: { rol: 'CLIENTE' }
  },
  {
    path: 'cliente/checkout',
    title: 'Finalizar compra · ESIBuy',
    component: Checkout,
    canActivate: [rolGuard],
    data: { rol: 'CLIENTE' }
  },
  {
    path: 'cliente/pedidos',
    title: 'Mis pedidos · ESIBuy',
    component: MisPedidos,
    canActivate: [rolGuard],
    data: { rol: 'CLIENTE' }
  },
  {
    path: 'cliente',
    title: 'Catálogo · ESIBuy',
    component: PanelCliente,
    canActivate: [rolGuard],
    data: { rol: 'CLIENTE' }
  },
  {
    path: 'vendedor/pedidos',
    title: 'Pedidos · ESIBuy',
    component: PedidosVendedor,
    canActivate: [rolGuard],
    data: { rol: 'VENDEDOR' }
  },
  {
    path: 'vendedor',
    title: 'Mis productos · ESIBuy',
    component: PanelVendedor,
    canActivate: [rolGuard],
    data: { rol: 'VENDEDOR' }
  },
  {
    // Debe ir siempre al final: recoge cualquier ruta que no exista.
    path: '**',
    title: 'Página no encontrada · ESIBuy',
    component: NoEncontrada,
    data: { cabecera: false }
  }
];
