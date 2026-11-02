'use strict';

// Cuenta de demostración: es pública a propósito. El rol es de jefa de cocina y el proxy
// solo permite GET sobre /api/v1/*, así que no puede modificar nada aunque se conozca.
const DEMO = { username: 'demo.manager', password: 'Demo2026!segura' };

const euro = new Intl.NumberFormat('es-ES', {
  style: 'currency',
  currency: 'EUR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const quantity = new Intl.NumberFormat('es-ES', {
  minimumFractionDigits: 0,
  maximumFractionDigits: 3,
});

const STATUS_LABELS = {
  PENDING: 'Pendiente',
  IN_PROGRESS: 'En curso',
  READY: 'Terminada',
  SERVED: 'Servida',
  CANCELLED: 'Cancelada',
};

// La API devuelve el enum en mayúsculas; la etiqueta de la unidad base es más legible.
const UNIT_LABELS = { G: 'g', ML: 'ml', UNIT: 'ud' };

const state = { token: null };

const byId = (id) => document.getElementById(id);

function setStatus(message, kind) {
  const el = byId('status');
  el.textContent = message;
  el.className = kind ? `status ${kind}` : 'status';
}

async function login() {
  const response = await fetch('/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(DEMO),
  });
  if (!response.ok) {
    throw new Error(`No se pudo iniciar la sesión de demo (${response.status}).`);
  }
  const payload = await response.json();
  state.token = payload.accessToken;
}

async function call(path) {
  if (!state.token) {
    await login();
  }
  let response = await fetch(path, {
    headers: { Authorization: `Bearer ${state.token}` },
  });
  if (response.status === 401) {
    await login();
    response = await fetch(path, {
      headers: { Authorization: `Bearer ${state.token}` },
    });
  }
  if (!response.ok) {
    throw new Error(`La API respondió ${response.status} en ${path}.`);
  }
  return response.json();
}

// El plan del seed es el de mañana en la zona horaria del negocio.
function madridTomorrow() {
  const tomorrow = new Date(Date.now() + 24 * 60 * 60 * 1000);
  return tomorrow.toLocaleDateString('en-CA', { timeZone: 'Europe/Madrid' });
}

function cell(text, className) {
  const td = document.createElement('td');
  td.textContent = text;
  if (className) {
    td.className = className;
  }
  return td;
}

function row(cells) {
  const tr = document.createElement('tr');
  cells.forEach((item) => tr.appendChild(item));
  return tr;
}

function emptyRow(text, columns) {
  const td = cell(text, 'empty');
  td.colSpan = columns;
  const tr = document.createElement('tr');
  tr.appendChild(td);
  return tr;
}

function fillTable(id, rows, columns, emptyText) {
  const body = byId(id);
  body.replaceChildren(...(rows.length ? rows : [emptyRow(emptyText, columns)]));
}

function statusPill(status) {
  const span = document.createElement('span');
  span.className = `pill ${String(status || '').toLowerCase()}`;
  span.textContent = STATUS_LABELS[status] || status || '—';
  const td = document.createElement('td');
  td.appendChild(span);
  return td;
}

function renderKpis(products, alerts, expiring) {
  byId('kpi-products').textContent = String(products.total ?? products.content.length);
  byId('kpi-lowstock').textContent = String(alerts.lowStockTotal ?? 0);
  byId('kpi-lots').textContent = String(expiring.totalLots ?? 0);
  byId('kpi-value').textContent = euro.format(Number(expiring.totalValue ?? 0));
}

function renderRecipes(costs) {
  const rows = (costs.versions || []).map((version) => {
    const variation = version.variationPct === null || version.variationPct === undefined
      ? '—'
      : `${Number(version.variationPct).toFixed(1)} %`;
    return row([
      cell(version.recipeName),
      cell(String(version.baseRations), 'num'),
      cell(euro.format(Number(version.costPerRation)), 'num'),
      cell(variation, 'num'),
    ]);
  });
  fillTable('recipes-body', rows, 4, 'No hay recetas publicadas.');
  byId('recipes-summary').textContent = costs.recipeCount
    ? `${costs.recipeCount} recetas con ${costs.versionCount} versiones publicadas y su coste congelado.`
    : '';
}

function renderExpiring(expiring, units) {
  const rows = (expiring.lots || []).map((lot) => {
    const days = Number(lot.daysRemaining);
    const daysClass = days <= 2 ? 'num days urgent' : days <= 5 ? 'num days soon' : 'num days';
    const unit = UNIT_LABELS[units.get(lot.productId)] || '';
    const amount = `${quantity.format(Number(lot.remainingQty))} ${unit}`.trim();
    return row([
      cell(lot.productName),
      cell(String(days), daysClass),
      cell(amount, 'num'),
      cell(euro.format(Number(lot.value)), 'num'),
    ]);
  });
  fillTable('expiring-body', rows, 4, 'No hay lotes en riesgo.');
}

function renderPlan(plans) {
  const plan = (plans.content || [])[0];
  if (!plan || !plan.items || !plan.items.length) {
    fillTable('plan-body', [], 5, 'No hay plan de producción para mañana.');
    return;
  }
  const rows = plan.items.map((item) => {
    const batch = item.batch;
    const real = batch && batch.rations !== null && batch.rations !== undefined
      ? String(batch.rations)
      : '—';
    const deviation = batch && batch.deviation !== null && batch.deviation !== undefined
      ? String(batch.deviation)
      : '—';
    return row([
      cell(item.recipeName),
      cell(String(item.plannedRations), 'num'),
      statusPill(item.status),
      cell(real, 'num'),
      cell(deviation, 'num'),
    ]);
  });
  fillTable('plan-body', rows, 5, 'No hay plan de producción para mañana.');
}

async function load() {
  setStatus('Cargando datos de la demo…', null);
  try {
    const [products, alerts, costs, expiring, plans] = await Promise.all([
      call('/api/v1/products?size=100'),
      call('/api/v1/inventory/alerts'),
      call('/api/v1/reports/recipe-costs'),
      call('/api/v1/reports/expiring-lots?days=30'),
      call(`/api/v1/production-plans?serviceDate=${madridTomorrow()}`),
    ]);

    const units = new Map((products.content || []).map((product) => [product.id, product.baseUnit]));
    renderKpis(products, alerts, expiring);
    renderRecipes(costs);
    renderExpiring(expiring, units);
    renderPlan(plans);

    const time = new Date().toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' });
    setStatus(`Datos reales de la API, actualizados a las ${time}. Se refrescan cada minuto.`, 'ok');
  } catch (error) {
    setStatus(`No se pudo cargar la demo: ${error.message}`, 'error');
  }
}

byId('retry').addEventListener('click', load);
load();
setInterval(load, 60 * 1000);
