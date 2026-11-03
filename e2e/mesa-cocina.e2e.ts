import { devices, expect, test } from '@playwright/test';
import { USERS, expectScrollLocked, login } from './util';

/**
 * Servicio de mesas de principio a fin, con dos personas a la vez:
 * el camarero (movil) abre una mesa, apunta y envia a cocina; cocina (tablet) lo prepara y lo deja listo;
 * al camarero le aparece para servir.
 */
test('el camarero apunta una mesa y cocina la prepara', async ({ browser }) => {
  // ----- Camarero, en el movil -----
  const waiterContext = await browser.newContext({ ...devices['Pixel 7'], locale: 'es-ES', timezoneId: 'Europe/Madrid' });
  const waiter = await waiterContext.newPage();
  await login(waiter, USERS.camarero);
  await expect(waiter).toHaveURL(/\/service$/);

  // Abre la primera mesa libre
  await waiter.getByRole('button', { name: 'Lista', exact: true }).click();
  const free = waiter.locator('.table-card:not(.is-busy)').first();
  const table = (await free.locator('strong').innerText()).trim();
  await free.click();
  await waiter.getByRole('button', { name: 'Abrir mesa', exact: true }).click();
  await expect(waiter).toHaveURL(/\/service\/\d+$/);

  // Tocar = apuntar uno; el + de la tarjeta suma otro
  await waiter.getByRole('button', { name: 'Vegetal. Toca para apuntar uno' }).click();
  await expect(waiter.getByRole('dialog')).toHaveCount(0);
  await waiter.getByRole('button', { name: 'Añadir otro Vegetal' }).click();

  // Tocar uno ya apuntado: notas e ingredientes, solo para una de las dos unidades
  await waiter.getByRole('button', { name: /^Vegetal: 2\./ }).click();
  const dialog = waiter.getByRole('dialog', { name: 'Vegetal' });
  await expect(dialog.getByText('¿A cuántos se lo cambias?')).toBeVisible();
  await expectScrollLocked(waiter, true);
  await dialog.getByRole('checkbox', { name: 'Cebolla' }).first().uncheck();
  await dialog.getByPlaceholder('Poco hecha, sin sal…').fill('poco hecha');
  await dialog.getByRole('button', { name: 'Guardar' }).click();
  await expect(dialog).toHaveCount(0);
  await expectScrollLocked(waiter, false);
  // Quedan dos tarjetas: la normal y la cambiada, al lado
  await expect(waiter.getByRole('button', { name: /^Vegetal: 1\./ })).toBeVisible();
  await expect(waiter.getByRole('button', { name: /^Vegetal sin Cebolla · poco hecha: 1\./ })).toBeVisible();

  // Bebidas: cada toque suma uno, sin dialogo ni notas
  await waiter.locator('.cats .chip', { hasText: 'Bebidas' }).click();
  await waiter.getByRole('button', { name: 'Caña. Toca para apuntar uno' }).click();
  await waiter.getByRole('button', { name: /^Caña: 1\. Toca para añadir otro/ }).click();
  await waiter.getByRole('button', { name: /^Caña: 2\. Toca para añadir otro/ }).click();
  await expect(waiter.getByRole('button', { name: /^Caña: 3\./ })).toBeVisible();
  await expect(waiter.getByRole('dialog')).toHaveCount(0);

  // Enviar a cocina: vuelve a lo que tiene la mesa, con los minutos de cada producto
  await waiter.getByRole('button', { name: 'Enviar', exact: true }).click();
  await expect(waiter.getByText('Ronda 1 enviada a cocina')).toBeVisible();
  await expect(waiter.locator('.line--sent .status', { hasText: /Pendiente · \d+ min/ }).first()).toBeVisible();

  // ----- Cocina, en la tablet -----
  const kitchenContext = await browser.newContext({ viewport: { width: 1280, height: 900 }, locale: 'es-ES', timezoneId: 'Europe/Madrid' });
  const kitchen = await kitchenContext.newPage();
  await login(kitchen, USERS.cocina);
  await expect(kitchen).toHaveURL(/\/kitchen$/);

  const pending = kitchen.locator('.column--pendiente .ticket', { hasText: `Mesa ${table}` });
  await expect(pending).toBeVisible();
  // Las lineas no se salen del ticket
  const box = await pending.boundingBox();
  for (const item of await pending.locator('.item').all()) {
    const itemBox = await item.boundingBox();
    expect(itemBox!.x + itemBox!.width).toBeLessThanOrEqual(box!.x + box!.width + 1);
  }
  await pending.getByRole('button', { name: `Pasar todo Mesa ${table}` }).click();
  const preparing = kitchen.locator('.column--preparando .ticket', { hasText: `Mesa ${table}` });
  await preparing.getByRole('button', { name: `Pasar todo Mesa ${table}` }).click();
  await expect(kitchen.locator('.column--listo .ticket', { hasText: `Mesa ${table}` })).toBeVisible();

  // ----- El camarero lo ve listo (en directo) y lo sirve -----
  const serveAll = waiter.getByRole('button', { name: /Servir todo lo listo \(2\)/ });
  await expect(serveAll).toBeVisible({ timeout: 30_000 });
  await serveAll.click();
  await expect(serveAll).toHaveCount(0);

  await waiterContext.close();
  await kitchenContext.close();
});
