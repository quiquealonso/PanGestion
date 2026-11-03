import { defineConfig, devices } from '@playwright/test';

/**
 * Pruebas end-to-end (e2e/): un navegador de verdad usa la app como lo haria una persona.
 *
 * Dos formas de pasarlas (E2E_TARGET):
 * - produccion (la del CI): contra PanGestion montada como en la VM, con Docker Compose, PostgreSQL, Caddy (HTTPS)
 *   y el build de produccion servido como GitHub Pages. Antes: scripts/e2e-produccion.sh
 * - desarrollo (por defecto, sin Docker): arranca su propia API (el jar, con H2 y los datos de demo) y `ng serve`,
 *   en puertos distintos de los de desarrollo (8090 y 4300). Antes: cd backend && ./mvnw package -DskipTests
 */
const PRODUCTION = process.env['E2E_TARGET'] === 'produccion';
const API_PORT = 8090;
const WEB_PORT = 4300;

export default defineConfig({
  testDir: 'e2e',
  testMatch: '**/*.e2e.ts',
  timeout: 90_000,
  expect: { timeout: 15_000 },
  // Comparten la misma API (y su base de datos): una prueba detras de otra
  workers: 1,
  fullyParallel: false,
  retries: process.env['CI'] ? 1 : 0,
  forbidOnly: !!process.env['CI'],
  // Cada comprobacion del "bot" en pantalla (y, en GitHub, en el resumen de la ejecucion); html para ver capturas
  reporter: [['./e2e/resumen-reporter.ts'], ['html', { open: 'never' }]],
  use: {
    // Las rutas de las pruebas son relativas ('pedido'): en produccion la app esta en /PanGestion/app/
    baseURL: PRODUCTION ? 'https://localhost:4443/PanGestion/app/' : `http://localhost:${WEB_PORT}/`,
    // Certificado HTTPS propio de Caddy para localhost (en la VM es de Let's Encrypt)
    ignoreHTTPSErrors: PRODUCTION,
    locale: 'es-ES',
    timezoneId: 'Europe/Madrid',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    // Lo que se usa con el movil: la web de pedidos de los clientes
    { name: 'movil', testMatch: 'pedido-movil.e2e.ts', use: { ...devices['Pixel 7'] } },
    // Camarero (movil) y cocina (tablet/ordenador): cada prueba crea sus propios navegadores
    { name: 'sala', testMatch: 'mesa-cocina.e2e.ts', use: { ...devices['Desktop Chrome'] } },
  ],
  webServer: PRODUCTION
    ? undefined
    : [
        {
          command:
            `java -Duser.timezone=Europe/Madrid -jar backend/target/pangestion-api-0.0.1-SNAPSHOT.jar --server.port=${API_PORT}` +
            ' --spring.datasource.url=jdbc:h2:mem:e2e;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE' +
            ' --app.seed-data=true --app.rate-limit.enabled=false --app.uploads.dir=backend/target/e2e-uploads',
          url: `http://localhost:${API_PORT}/api/public/companies`,
          timeout: 180_000,
          reuseExistingServer: !process.env['CI'],
        },
        {
          command: `npx ng serve --port ${WEB_PORT} --proxy-config e2e/proxy.e2e.json`,
          url: `http://localhost:${WEB_PORT}`,
          timeout: 180_000,
          reuseExistingServer: !process.env['CI'],
        },
      ],
});
