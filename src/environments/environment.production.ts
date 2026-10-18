export const environment = {
  production: true,
  apiBaseUrl: '/api',
  appName: 'PanGestión',
  /** En produccion nunca se muestran usuarios de prueba. */
  testUsers: [] as { label: string; detail: string; email: string; password: string }[],
};
