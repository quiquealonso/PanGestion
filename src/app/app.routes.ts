import { Routes } from '@angular/router';
import { authGuard, companyGuard, superAdminGuard, usersGuard } from './core/guards/auth.guard';
import { ShellComponent } from './layout/shell/shell.component';

/**
 * Cada pagina se carga solo cuando se visita (loadComponent), asi la app arranca mas ligera.
 */
export const routes: Routes = [
  {
    path: 'auth/login',
    loadComponent: () => import('./features/auth/auth-page.component').then((m) => m.AuthPageComponent),
    title: 'Acceso · PanGestión',
  },
  {
    path: 'pedido',
    loadComponent: () => import('./features/public-order/public-order-page.component').then((m) => m.PublicOrderPageComponent),
    title: 'Nuevo pedido · PanGestión',
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      // Trabajo diario de una empresa (el SUPERADMIN no tiene empresa: companyGuard lo lleva a Empresas)
      {
        path: '',
        canActivate: [companyGuard],
        children: [
          {
            path: 'dashboard',
            loadComponent: () => import('./features/dashboard/dashboard-page.component').then((m) => m.DashboardPageComponent),
            title: 'Inicio · PanGestión',
          },
          {
            path: 'orders',
            loadComponent: () => import('./features/orders/orders-page.component').then((m) => m.OrdersPageComponent),
            title: 'Pedidos · PanGestión',
          },
          {
            path: 'orders/new',
            loadComponent: () => import('./features/orders/create-order/create-order-page.component').then((m) => m.CreateOrderPageComponent),
            title: 'Nuevo pedido · PanGestión',
          },
          {
            path: 'orders/:id/edit',
            loadComponent: () => import('./features/orders/create-order/create-order-page.component').then((m) => m.CreateOrderPageComponent),
            title: 'Editar pedido · PanGestión',
          },
          {
            path: 'orders/:id',
            loadComponent: () => import('./features/orders/orders-page.component').then((m) => m.OrdersPageComponent),
            title: 'Pedidos · PanGestión',
          },
          {
            path: 'production',
            loadComponent: () => import('./features/production/production-page.component').then((m) => m.ProductionPageComponent),
            title: 'Producción del día · PanGestión',
          },
          {
            path: 'customers',
            loadComponent: () => import('./features/customers/customers-page.component').then((m) => m.CustomersPageComponent),
            title: 'Clientes · PanGestión',
          },
          {
            path: 'catalog/products',
            loadComponent: () => import('./features/catalog/products/products-page.component').then((m) => m.ProductsPageComponent),
            title: 'Productos · PanGestión',
          },
          {
            path: 'catalog/ingredients',
            loadComponent: () => import('./features/catalog/ingredients/ingredients-page.component').then((m) => m.IngredientsPageComponent),
            title: 'Ingredientes · PanGestión',
          },
          {
            path: 'catalog/categories',
            loadComponent: () => import('./features/catalog/categories/categories-page.component').then((m) => m.CategoriesPageComponent),
            title: 'Categorías · PanGestión',
          },
          {
            path: 'company',
            loadComponent: () => import('./features/company-settings/company-settings-page.component').then((m) => m.CompanySettingsPageComponent),
            title: 'Mi empresa · PanGestión',
          },
        ],
      },
      {
        path: 'companies',
        canActivate: [superAdminGuard],
        loadComponent: () => import('./features/companies/companies-page.component').then((m) => m.CompaniesPageComponent),
        title: 'Empresas · PanGestión',
      },
      {
        path: 'users',
        canActivate: [usersGuard],
        loadComponent: () => import('./features/users/users-page.component').then((m) => m.UsersPageComponent),
        title: 'Usuarios · PanGestión',
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
