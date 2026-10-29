export const environment = {
  production: false,
  apiBaseUrl: '/api',
  appName: 'PanGestión',
  /**
   * Usuarios de prueba que se muestran en el login para entrar rapido (solo en desarrollo).
   * Son los mismos que crea backend/.../config/DataInitializer. Quitar cuando ya no hagan falta.
   */
  testUsers: [
    { label: 'Superadministrador', detail: 'Gestiona las empresas', email: 'superadmin@admin.es', password: 'admin1234' },
    { label: 'Admin · Panadería', detail: 'Forn Campanero / Panadería Demo', email: 'admin@admin.es', password: 'admin1234' },
    { label: 'Admin · Bar', detail: 'El Mosset', email: 'admin2@admin.es', password: 'admin1234' },
    { label: 'Camarero · Bar', detail: 'El Mosset: servicio de mesas', email: 'camarero@pangestion.local', password: 'camarero123' },
    { label: 'Cocina · Bar', detail: 'El Mosset: solo la pantalla de cocina', email: 'cocina@pangestion.local', password: 'cocina123' },
    { label: 'Repartidor · Bar', detail: 'El Mosset: pedidos a domicilio', email: 'repartidor@pangestion.local', password: 'repartidor123' },
  ],
};
