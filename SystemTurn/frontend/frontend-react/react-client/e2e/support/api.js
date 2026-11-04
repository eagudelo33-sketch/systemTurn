import { expect } from '@playwright/test'

const RUTA_DASHBOARD = '/api/v1/turnos/dashboard'

const rutaDe = (response) => new URL(response.url()).pathname

export const esLecturaDashboard = (response) =>
  rutaDe(response) === RUTA_DASHBOARD && response.request().method() === 'GET'

export const esSolicitudDeTurno = (response) =>
  rutaDe(response) === '/api/v1/turnos' && response.request().method() === 'POST'

export const esLlamadaAlSiguiente = (response) =>
  rutaDe(response) === '/api/v1/turnos/siguiente' && response.request().method() === 'PATCH'

// Reads the live dashboard through the Vite proxy, the same path the browser uses.
export async function leerDashboard(request) {
  const respuesta = await request.get(RUTA_DASHBOARD)
  expect(respuesta.ok(), 'GET dashboard must succeed').toBeTruthy()
  return respuesta.json()
}

// Parses "hh:mm a. m." / "hh:mm p. m." (es-CO, any whitespace flavor) to minutes since midnight.
export function minutosDelDia(hora) {
  const partes = /^(\d{1,2}):(\d{2})\s*([ap])\.?\s*m\.?$/i.exec(String(hora).trim())
  if (!partes) throw new Error(`Unrecognized time "${hora}"`)
  const [, horas, minutos, meridiano] = partes
  const base = Number(horas) % 12
  const sumaPm = meridiano.toLowerCase() === 'p' ? 12 : 0
  return (base + sumaPm) * 60 + Number(minutos)
}

// 1-based place of ticket `id` among Pendiente tickets, ordered by request time then id.
export function posicionEsperada(dashboard, id) {
  const pendientes = dashboard.turnos
    .filter((turno) => turno.estado === 'Pendiente')
    .sort((a, b) => minutosDelDia(a.horaSolicitud) - minutosDelDia(b.horaSolicitud) || a.id - b.id)
  const indice = pendientes.findIndex((turno) => turno.id === id)
  return indice >= 0 ? indice + 1 : null
}
