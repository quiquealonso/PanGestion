#!/usr/bin/env node
/**
 * Comprobacion de SOLO LECTURA de la API: entra con un usuario y recorre todas las consultas (GET) del panel,
 * con sus filtros, y el detalle del primer elemento de cada lista. Falla si alguna no responde 200.
 * Nunca crea, cambia ni borra nada: solo hace GET (y el login).
 *
 * Se usa en dos sitios (ver docs/despliegue.md):
 * - Antes de desplegar (e2e.yml): contra PanGestion montada como en produccion (PostgreSQL, datos de demo).
 * - Despues de desplegar (deploy.yml): contra produccion, con un usuario VISOR (solo puede consultar).
 *
 *   API_URL=https://... CHECK_EMAIL=... CHECK_PASSWORD=... node scripts/comprobar-api.mjs
 */
import { appendFileSync } from 'node:fs';

const api = (process.env.API_URL ?? '').replace(/\/$/, '');
const email = process.env.CHECK_EMAIL;
const password = process.env.CHECK_PASSWORD;
const title = process.env.CHECK_TITLE ?? 'Consultas de la API';
if (!api || !email || !password) {
  console.error('Faltan API_URL, CHECK_EMAIL o CHECK_PASSWORD');
  process.exit(2);
}

/** Fechas en hora de Espana, como las pide la app. */
const day = (offset = 0) =>
  new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Madrid' }).format(new Date(Date.now() + offset * 86_400_000));
const today = day();
const weekAgo = day(-7);

const results = [];
let token = '';

async function get(path) {
  const started = Date.now();
  let status = 0;
  let body = null;
  let detail = '';
  try {
    const response = await fetch(api + path, { headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' } });
    status = response.status;
    const text = await response.text();
    try {
      body = JSON.parse(text);
    } catch {
      body = null;
    }
    if (status !== 200) detail = (body && (body.message ?? body.error)) || text.slice(0, 120);
  } catch (error) {
    detail = String(error.message ?? error);
  }
  results.push({ path, status, ms: Date.now() - started, detail });
  return status === 200 ? body : null;
}

/** GET de una lista y, si tiene elementos, del detalle del primero (con los subrecursos indicados). */
async function listAndDetail(path, base, extra = []) {
  const list = await get(path);
  const first = Array.isArray(list) ? list[0] : null;
  if (first?.id != null) {
    await get(`${base}/${first.id}`);
    for (const sub of extra) await get(`${base}/${first.id}/${sub}`);
  }
  return list;
}

// ----- Entrar -----
// Con reintentos: recien arrancada, la API responde antes de terminar de crear los usuarios de demo
const attempts = Number(process.env.CHECK_LOGIN_ATTEMPTS ?? 1);
for (let attempt = 1; !token; attempt++) {
  const login = await fetch(`${api}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  }).catch((error) => ({ ok: false, status: 0, text: async () => String(error) }));
  if (login.ok) {
    token = (await login.json()).token;
  } else if (attempt >= attempts) {
    console.error(`No se puede entrar (${login.status}): ${(await login.text()).slice(0, 200)}`);
    process.exit(1);
  } else {
    await new Promise((resolve) => setTimeout(resolve, 3000));
  }
}

// ----- Consultas del panel -----
await get('/api/auth/me');
await get('/api/company');
await get(`/api/company/slots?date=${today}`);

const categories = await listAndDetail('/api/categories', '/api/categories');
await listAndDetail('/api/ingredients', '/api/ingredients');

const categoryId = Array.isArray(categories) && categories[0]?.id;
await listAndDetail('/api/products', '/api/products');
await get('/api/products?active=true');
await get('/api/products?active=false');
await get('/api/products?search=a');
if (categoryId) {
  await get(`/api/products?categoryId=${categoryId}`);
  await get(`/api/products?categoryId=${categoryId}&active=true`);
}

await listAndDetail('/api/customers', '/api/customers', ['orders']);
await get('/api/customers?search=a');

await listAndDetail('/api/orders', '/api/orders');
await get(`/api/orders?from=${today}&to=${today}`);
await get(`/api/orders?from=${weekAgo}&to=${today}`);
await get(`/api/orders?from=${weekAgo}`);
await get(`/api/orders?to=${today}`);
await get('/api/orders?status=PENDIENTE');
await get(`/api/orders?status=ENTREGADO&from=${weekAgo}&to=${today}`);
await get('/api/orders?search=a');
await get(`/api/orders/production?date=${today}`);
await get(`/api/orders/production?date=${today}&pending=false`);

await get('/api/dashboard');
await get(`/api/dashboard?date=${today}`);
await get('/api/dashboard/dining');
await get('/api/dashboard/weekly-sales');
await get('/api/reports/dining');
await get(`/api/reports/dining?from=${weekAgo}&to=${today}`);

await listAndDetail('/api/dining/areas', '/api/dining/areas');
await get('/api/service/floor');
await get('/api/service/day');
await get(`/api/service/day?date=${today}`);

// ----- Resultado -----
const failed = results.filter((r) => r.status !== 200);
for (const r of results) {
  console.log(`${r.status === 200 ? '✅' : '❌'} ${r.status} ${r.path} · ${r.ms} ms${r.detail ? ` · ${r.detail}` : ''}`);
}
console.log(`\n${failed.length ? '❌' : '✅'} ${results.length - failed.length} de ${results.length} consultas correctas`);

const summary = process.env.GITHUB_STEP_SUMMARY;
if (summary) {
  const rows = results.map(
    (r) => `| ${r.status === 200 ? '✅' : '❌'} | ${r.status} | \`${r.path}\` | ${r.ms} ms | ${r.detail.replace(/\|/g, '\\|')} |`,
  );
  appendFileSync(
    summary,
    [
      `## 🔎 ${title}: ${results.length - failed.length} de ${results.length} correctas`,
      '',
      `Solo lectura (GET) con el usuario \`${email}\` contra \`${api}\`.`,
      '',
      '| | Estado | Consulta | Tiempo | Error |',
      '|---|---|---|---|---|',
      ...rows,
      '',
    ].join('\n'),
  );
}
process.exit(failed.length ? 1 : 0);
