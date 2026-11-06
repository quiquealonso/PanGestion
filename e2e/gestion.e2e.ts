import { Page, Response, expect, test } from '@playwright/test';
import { USERS, login } from './util';

/**
 * Panel de gestion: listados con filtros. Con PostgreSQL (el modo produccion del CI) los filtros opcionales
 * ("de esta fecha", "de esta categoria") daban 500 y con H2 no; por eso se prueban aqui y no solo en los tests de la API.
 * Cada test.step es una comprobacion del "bot" que sale en el informe (y en el resumen de GitHub).
 */
test('el administrador consulta los pedidos del día y filtra el catálogo', async ({ page }) => {
  await test.step('El administrador de El Mosset entra en el panel', async () => {
    await login(page, USERS.admin);
  });

  await test.step('Pedidos: la lista del día (filtrada por fecha) carga sin error', async () => {
    const response = waitForApi(page, (url) => url.pathname.endsWith('/api/orders') && url.searchParams.has('from'));
    await page.goto('orders');
    expect((await response).status()).toBe(200);
    await expect(page.getByRole('heading', { name: 'Del encargo a la entrega' })).toBeVisible();
    await expect(page.getByRole('alert')).toHaveCount(0);
  });

  await test.step('Catálogo: filtrar por categoría y por activos carga sin error', async () => {
    await page.goto('catalog/products');
    await expect(page.getByRole('heading', { name: 'Productos' })).toBeVisible();

    let response = waitForApi(page, (url) => url.pathname.endsWith('/api/products') && url.searchParams.has('categoryId'));
    await page.locator('select[name=category]').selectOption({ label: 'Pizzas' });
    expect((await response).status()).toBe(200);
    await expect(page.locator('.card', { hasText: 'Vegetal' })).toBeVisible();

    response = waitForApi(page, (url) => url.pathname.endsWith('/api/products') && url.searchParams.has('active'));
    await page.locator('select[name=active]').selectOption({ index: 1 });
    expect((await response).status()).toBe(200);
    await expect(page.locator('.card', { hasText: 'Vegetal' })).toBeVisible();
  });
});

/** Espera la siguiente respuesta de la API que cumpla la condicion (en produccion la API esta en otro origen). */
function waitForApi(page: Page, matches: (url: URL) => boolean): Promise<Response> {
  return page.waitForResponse((r) => r.request().method() === 'GET' && matches(new URL(r.url())));
}
