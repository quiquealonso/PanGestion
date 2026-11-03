import { BrowserContext, Page, devices, expect, test } from '@playwright/test';
import { USERS, expectScrollLocked, login } from './util';

/**
 * Servicio de mesas de principio a fin, con dos personas a la vez: el camarero (movil) abre una mesa, apunta
 * y envia a cocina; cocina (tablet) lo prepara y lo deja listo; al camarero le aparece para servir.
 * Cada test.step es una comprobacion del "bot" que sale en el informe (y en el resumen de GitHub).
 */
test('el camarero apunta una mesa y cocina la prepara', async ({ browser, baseURL }) => {
  const contexts: BrowserContext[] = [];
  const open = async (options: Parameters<typeof browser.newContext>[0]): Promise<Page> => {
    const context = await browser.newContext({ baseURL, ignoreHTTPSErrors: true, locale: 'es-ES', timezoneId: 'Europe/Madrid', ...options });
    contexts.push(context);
    return context.newPage();
  };
  let waiter!: Page;
  let kitchen!: Page;
  let table = '';

  await test.step('El camarero entra desde el móvil y abre la primera mesa libre', async () => {
    waiter = await open({ ...devices['Pixel 7'] });
    await login(waiter, USERS.camarero);
    await expect(waiter).toHaveURL(/\/service$/);
    await waiter.getByRole('button', { name: 'Lista', exact: true }).click();
    const free = waiter.locator('.table-card:not(.is-busy)').first();
    table = (await free.locator('strong').innerText()).trim();
    await free.click();
    await waiter.getByRole('button', { name: 'Abrir mesa', exact: true }).click();
    await expect(waiter).toHaveURL(/\/service\/\d+$/);
  });

  await test.step('Tocar un plato lo apunta sin diálogo y el + de la tarjeta suma otro', async () => {
    await waiter.getByRole('button', { name: 'Vegetal. Toca para apuntar uno' }).click();
    await expect(waiter.getByRole('dialog')).toHaveCount(0);
    await waiter.getByRole('button', { name: 'Añadir otro Vegetal' }).click();
    await expect(waiter.getByRole('button', { name: /^Vegetal: 2\./ })).toBeVisible();
  });

  await test.step('Cambia una de las dos: sin cebolla y «poco hecha», en su propia tarjeta', async () => {
    await waiter.getByRole('button', { name: /^Vegetal: 2\./ }).click();
    const dialog = waiter.getByRole('dialog', { name: 'Vegetal' });
    await expect(dialog.getByText('¿A cuántos se lo cambias?')).toBeVisible();
    await expectScrollLocked(waiter, true);
    await dialog.getByRole('checkbox', { name: 'Cebolla' }).first().uncheck();
    await dialog.getByPlaceholder('Poco hecha, sin sal…').fill('poco hecha');
    await dialog.getByRole('button', { name: 'Guardar' }).click();
    await expect(dialog).toHaveCount(0);
    await expectScrollLocked(waiter, false);
    await expect(waiter.getByRole('button', { name: /^Vegetal: 1\./ })).toBeVisible();
    await expect(waiter.getByRole('button', { name: /^Vegetal sin Cebolla · poco hecha: 1\./ })).toBeVisible();
  });

  await test.step('Bebidas: cada toque suma una caña, sin diálogo ni notas', async () => {
    await waiter.locator('.cats .chip', { hasText: 'Bebidas' }).click();
    await waiter.getByRole('button', { name: 'Caña. Toca para apuntar uno' }).click();
    await waiter.getByRole('button', { name: /^Caña: 1\. Toca para añadir otro/ }).click();
    await waiter.getByRole('button', { name: /^Caña: 2\. Toca para añadir otro/ }).click();
    await expect(waiter.getByRole('button', { name: /^Caña: 3\./ })).toBeVisible();
    await expect(waiter.getByRole('dialog')).toHaveCount(0);
  });

  await test.step('Envía a cocina: sale la ronda con los minutos de cada producto', async () => {
    await waiter.getByRole('button', { name: 'Enviar', exact: true }).click();
    await expect(waiter.getByText('Ronda 1 enviada a cocina')).toBeVisible();
    await expect(waiter.locator('.line--sent .status', { hasText: /Pendiente · \d+ min/ }).first()).toBeVisible();
  });

  await test.step('Cocina entra desde la tablet y le llega la mesa, sin líneas fuera del ticket', async () => {
    kitchen = await open({ viewport: { width: 1280, height: 900 } });
    await login(kitchen, USERS.cocina);
    await expect(kitchen).toHaveURL(/\/kitchen$/);
    const pending = kitchen.locator('.column--pendiente .ticket', { hasText: `Mesa ${table}` });
    await expect(pending).toBeVisible();
    const box = await pending.boundingBox();
    for (const item of await pending.locator('.item').all()) {
      const itemBox = await item.boundingBox();
      expect(itemBox!.x + itemBox!.width).toBeLessThanOrEqual(box!.x + box!.width + 1);
    }
  });

  await test.step('Cocina la pasa a preparando y luego a listo', async () => {
    await kitchen.locator('.column--pendiente .ticket', { hasText: `Mesa ${table}` })
      .getByRole('button', { name: `Pasar todo Mesa ${table}` }).click();
    await kitchen.locator('.column--preparando .ticket', { hasText: `Mesa ${table}` })
      .getByRole('button', { name: `Pasar todo Mesa ${table}` }).click();
    await expect(kitchen.locator('.column--listo .ticket', { hasText: `Mesa ${table}` })).toBeVisible();
  });

  await test.step('Al camarero le sale en directo para servir, y lo sirve', async () => {
    const serveAll = waiter.getByRole('button', { name: /Servir todo lo listo \(2\)/ });
    await expect(serveAll).toBeVisible({ timeout: 30_000 });
    await serveAll.click();
    await expect(serveAll).toHaveCount(0);
  });

  for (const context of contexts) await context.close();
});
