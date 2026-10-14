import { Routes } from '@angular/router';
import { adminGuard, authGuard } from './core/guards/auth.guard';
import { AuthPageComponent } from './features/auth/auth-page.component';
import { CategoriesPageComponent } from './features/catalog/categories/categories-page.component';
import { IngredientsPageComponent } from './features/catalog/ingredients/ingredients-page.component';
import { ProductsPageComponent } from './features/catalog/products/products-page.component';
import { CompaniesPageComponent } from './features/companies/companies-page.component';
import { CustomersPageComponent } from './features/customers/customers-page.component';
import { DashboardPageComponent } from './features/dashboard/dashboard-page.component';
import { CreateOrderPageComponent } from './features/orders/create-order/create-order-page.component';
import { OrdersPageComponent } from './features/orders/orders-page.component';
import { PublicOrderPageComponent } from './features/public-order/public-order-page.component';
import { StoresPageComponent } from './features/stores/stores-page.component';
import { UsersPageComponent } from './features/users/users-page.component';
import { ShellComponent } from './layout/shell/shell.component';

export const routes: Routes = [
  { path: 'auth/login', component: AuthPageComponent, title: 'Acceso · PanGestión' },
  { path: 'pedido', component: PublicOrderPageComponent, title: 'Nuevo pedido · PanGestión' },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', component: DashboardPageComponent, title: 'Inicio · PanGestión' },
      { path: 'orders', component: OrdersPageComponent, title: 'Pedidos · PanGestión' },
      { path: 'orders/new', component: CreateOrderPageComponent, title: 'Nuevo pedido · PanGestión' },
      { path: 'orders/:id/edit', component: CreateOrderPageComponent, title: 'Editar pedido · PanGestión' },
      { path: 'orders/:id', component: OrdersPageComponent, title: 'Pedidos · PanGestión' },
      { path: 'customers', component: CustomersPageComponent, title: 'Clientes · PanGestión' },
      { path: 'companies', component: CompaniesPageComponent, title: 'Empresas · PanGestión' },
      { path: 'catalog/products', component: ProductsPageComponent, title: 'Productos · PanGestión' },
      { path: 'catalog/ingredients', component: IngredientsPageComponent, title: 'Ingredientes · PanGestión' },
      { path: 'catalog/categories', component: CategoriesPageComponent, title: 'Categorías · PanGestión' },
      { path: 'stores', component: StoresPageComponent, title: 'Tiendas · PanGestión' },
      { path: 'users', component: UsersPageComponent, canActivate: [adminGuard], title: 'Usuarios · PanGestión' },
    ],
  },
  { path: '**', redirectTo: '' },
];
