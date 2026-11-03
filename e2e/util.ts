import { Page, expect } from '@playwright/test';

/** Usuarios de demo (los crea DataInitializer en la base de datos en memoria; no existen en produccion). */
export const USERS = {
  camarero: { email: 'camarero@pangestion.local', password: 'camarero123' },
  cocina: { email: 'cocina@pangestion.local', password: 'cocina123' },
};

/** Entra en el panel de gestion con el formulario de acceso. */
export async function login(page: Page, user: { email: string; password: string }): Promise<void> {
  await page.goto('/auth/login');
  await page.locator('input[type=email]').fill(user.email);
  await page.locator('input[type=password]').fill(user.password);
  await page.locator('button[type=submit]').click();
  await expect(page).not.toHaveURL(/auth\/login/);
}

/** La pagina de detras esta bloqueada (hay un panel o una ventana abierta). */
export async function expectScrollLocked(page: Page, locked: boolean): Promise<void> {
  const html = page.locator('html');
  if (locked) await expect(html).toHaveClass(/is-scroll-locked/);
  else await expect(html).not.toHaveClass(/is-scroll-locked/);
}
