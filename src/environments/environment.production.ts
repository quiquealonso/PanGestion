/** API desplegada en la VM de Google Cloud (el frontend se sirve desde GitHub Pages). */
const apiOrigin = 'https://34-175-204-112.sslip.io';

export const environment = {
  production: true,
  apiOrigin,
  apiBaseUrl: `${apiOrigin}/api`,
  appName: 'PanGestión',
  /** En produccion nunca se muestran usuarios de prueba. */
  testUsers: [] as { label: string; detail: string; email: string; password: string }[],
};
