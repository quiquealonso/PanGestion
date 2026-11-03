import { expect, test } from '@playwright/test';
import { expectScrollLocked } from './util';

/**
 * Un cliente hace un pedido desde el movil, de principio a fin. Cada test.step es una comprobacion del "bot"
 * que sale en el informe (y en el resumen de GitHub).
 */
test('un cliente hace un pedido desde el móvil', async ({ page }) => {
  const vegetalNormal = page.getByRole('button', { name: /^Vegetal, .*Toca para añadirlo/ });

  await test.step('Abre la web de pedidos y elige El Mosset', async () => {
    await page.goto('pedido');
    await page.getByRole('button', { name: /El Mosset/ }).click();
    await expect(page.getByRole('heading', { name: 'Elige tus productos' })).toBeVisible();
  });

  await test.step('Tocar un producto lo añade sin abrir ningún panel', async () => {
    await vegetalNormal.click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(page.getByText('✓ Añadido: 1 × Vegetal')).toBeVisible();
  });

  await test.step('Tocarlo otra vez abre los ingredientes y bloquea la página de detrás', async () => {
    await page.getByRole('button', { name: /^Vegetal: tienes 1\./ }).click();
    await expect(page.getByRole('dialog', { name: 'Vegetal' })).toBeVisible();
    await expectScrollLocked(page, true);
  });

  await test.step('Quita la cebolla y guarda: el panel se cierra y la página se desbloquea', async () => {
    const dialog = page.getByRole('dialog', { name: 'Vegetal' });
    await dialog.getByRole('checkbox', { name: 'Cebolla' }).uncheck();
    await dialog.getByRole('button', { name: 'Guardar' }).click();
    await expect(dialog).toHaveCount(0);
    await expectScrollLocked(page, false);
  });

  await test.step('La versión «sin cebolla» va en su propia tarjeta y la normal se puede volver a añadir', async () => {
    await expect(page.getByRole('button', { name: /^Vegetal sin cebolla: tienes 1\./ })).toBeVisible();
    await vegetalNormal.click();
    await expect(page.getByRole('button', { name: /^Vegetal: tienes 1\./ })).toBeVisible();
    await expect(page.getByText(/2 productos/)).toBeVisible();
  });

  await test.step('Revisa el pedido: sale la Vegetal sin cebolla', async () => {
    await page.getByRole('button', { name: 'Ver mi pedido →' }).click();
    await expect(page.getByRole('heading', { name: 'Revisa tu pedido' })).toBeVisible();
    await expect(page.getByText('sin cebolla', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Continuar →' }).click();
  });

  await test.step('Rellena sus datos (con el autorrelleno metiéndose en el campo oculto antibots)', async () => {
    await expect(page.getByRole('heading', { name: 'Tus datos' })).toBeVisible();
    await page.locator('input[name=name]').fill('Ana Prueba');
    await page.locator('input[name=email]').fill('ana.prueba@example.com');
    await page.locator('input[name=phone]').fill('600000001');
    // El autorrelleno del movil rellenaba el campo trampa con los datos del cliente y la API rechazaba
    // el pedido ("Pedido no valido"). Se simula: el pedido tiene que salir igual.
    await page.locator('.hp-field').evaluate((el: HTMLInputElement) => {
      el.value = 'Ana Prueba';
      el.dispatchEvent(new Event('input', { bubbles: true }));
    });
    await page.getByRole('button', { name: 'Continuar →' }).click();
  });

  await test.step('Elige mañana y una hora libre; la página se puede seguir moviendo', async () => {
    await expect(page.getByRole('heading', { name: /¿Cuándo lo/ })).toBeVisible();
    await page.locator('.day', { hasText: 'Mañana' }).click();
    await expectScrollLocked(page, false);
    await page.locator('.hour:enabled').first().click();
    await page.getByRole('button', { name: 'Revisar y confirmar →' }).click();
    await expectScrollLocked(page, true);
  });

  await test.step('Confirma: el pedido se guarda y sale «¡Pedido hecho!»', async () => {
    await page.getByRole('button', { name: '✓ Confirmar pedido' }).click();
    await expect(page.getByRole('heading', { name: '¡Pedido hecho!' })).toBeVisible();
    await expect(page.getByText('Gracias, Ana Prueba.')).toBeVisible();
    await expectScrollLocked(page, false);
  });
});
