import { expect, test } from '@playwright/test';
import { expectScrollLocked } from './util';

/**
 * Un cliente hace un pedido desde el movil, de principio a fin:
 * tocar = añadir, tocar otra vez = ingredientes, version cambiada en su propia tarjeta,
 * datos (con el autorrelleno del navegador metiendose en el campo trampa), dia, hora y confirmar.
 */
test('un cliente hace un pedido desde el móvil', async ({ page }) => {
  await page.goto('/pedido');
  await page.getByRole('button', { name: /El Mosset/ }).click();

  // ----- Productos -----
  await expect(page.getByRole('heading', { name: 'Elige tus productos' })).toBeVisible();
  const vegetalNormal = page.getByRole('button', { name: /^Vegetal, .*Toca para añadirlo/ });

  // Tocar un producto lo añade sin abrir nada
  await vegetalNormal.click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByText('✓ Añadido: 1 × Vegetal')).toBeVisible();

  // Tocarlo otra vez abre sus ingredientes; la pagina de detras se bloquea
  await page.getByRole('button', { name: /^Vegetal: tienes 1\./ }).click();
  const dialog = page.getByRole('dialog', { name: 'Vegetal' });
  await expect(dialog).toBeVisible();
  await expectScrollLocked(page, true);
  await dialog.getByRole('checkbox', { name: 'Cebolla' }).uncheck();
  await dialog.getByRole('button', { name: 'Guardar' }).click();
  await expect(dialog).toHaveCount(0);
  await expectScrollLocked(page, false);

  // La version cambiada va en su propia tarjeta y la normal queda libre para añadir otra
  await expect(page.getByRole('button', { name: /^Vegetal sin cebolla: tienes 1\./ })).toBeVisible();
  await vegetalNormal.click();
  await expect(page.getByRole('button', { name: /^Vegetal: tienes 1\./ })).toBeVisible();
  await expect(page.getByText(/2 productos/)).toBeVisible();

  // ----- Revisar -----
  await page.getByRole('button', { name: 'Ver mi pedido →' }).click();
  await expect(page.getByRole('heading', { name: 'Revisa tu pedido' })).toBeVisible();
  await expect(page.getByText('sin cebolla', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Continuar →' }).click();

  // ----- Datos -----
  await expect(page.getByRole('heading', { name: 'Tus datos' })).toBeVisible();
  await page.locator('input[name=name]').fill('Ana Prueba');
  await page.locator('input[name=email]').fill('ana.prueba@example.com');
  await page.locator('input[name=phone]').fill('600000001');
  // El autorrelleno del movil rellenaba el campo trampa (oculto) con los datos del cliente y la API
  // rechazaba el pedido ("Pedido no valido"). Se simula: el pedido tiene que salir igual.
  await page.locator('.hp-field').evaluate((el: HTMLInputElement) => {
    el.value = 'Ana Prueba';
    el.dispatchEvent(new Event('input', { bubbles: true }));
  });
  await page.getByRole('button', { name: 'Continuar →' }).click();

  // ----- Dia y hora -----
  await expect(page.getByRole('heading', { name: /¿Cuándo lo/ })).toBeVisible();
  await page.locator('.day', { hasText: 'Mañana' }).click();
  // Baja solo hasta las horas, pero la pagina se puede seguir moviendo (para cambiar el dia)
  await expectScrollLocked(page, false);
  await page.locator('.hour:enabled').first().click();
  await page.getByRole('button', { name: 'Revisar y confirmar →' }).click();

  // ----- Confirmar -----
  await expectScrollLocked(page, true);
  await page.getByRole('button', { name: '✓ Confirmar pedido' }).click();
  await expect(page.getByRole('heading', { name: '¡Pedido hecho!' })).toBeVisible();
  await expect(page.getByText('Gracias, Ana Prueba.')).toBeVisible();
  await expectScrollLocked(page, false);
});
