import { test, expect } from '@playwright/test'
import {
  CONFLICTO,
  SERVICIO_NO_DISPONIBLE,
  esLecturaDashboard,
  esSolicitudDeTurno,
  leerDashboard,
} from './support/api.js'
import { captura } from './support/capturas.js'

const RUTA_DASHBOARD = '**/api/v1/turnos/dashboard'

const esLecturaDashboardRequest = (request) =>
  new URL(request.url()).pathname === '/api/v1/turnos/dashboard' && request.method() === 'GET'

// Opens the client view and requests a real ticket; returns the id the backend assigned.
async function solicitarTurno(page) {
  await page.goto('/')
  const respuesta = page.waitForResponse(esSolicitudDeTurno)
  await page.getByRole('button', { name: 'Solicitar turno' }).click()
  const { id } = await (await respuesta).json()
  await expect(page.getByRole('button', { name: 'Ya tienes un turno' })).toBeVisible()
  return id
}

test('Client cancel conflict re-fetches', async ({ page }) => {
  await solicitarTurno(page)

  // Only the cancel request is mocked; the re-fetch that follows reaches the real backend.
  let servido = false
  await page.route('**/api/v1/turnos/*/cancelar', async (route) => {
    servido = true
    await route.fulfill({ status: 409, json: CONFLICTO })
  })

  await page.getByRole('button', { name: 'Cancelar turno' }).click()
  const refetch = page.waitForRequest((request) => servido && esLecturaDashboardRequest(request))
  await page.getByRole('button', { name: 'Sí, cancelar' }).click()
  await refetch

  const alerta = page.getByRole('alert')
  await expect(alerta).toContainText(CONFLICTO.mensaje)
  await expect(page.getByRole('article', { name: 'Tu turno', exact: true })).toContainText('Pendiente')
  await expect(page.getByRole('button', { name: 'Cancelar turno' })).toBeVisible()
  await captura(page, 'conflicto-cliente-01-alerta-tras-refetch')
})

test('Advisor conflict reloads without retry', async ({ page, request }) => {
  await page.goto('/?vista=asesor')
  await expect(page.getByRole('heading', { name: 'Lista de turnos' })).toBeVisible()
  const region = page.getByRole('region', { name: 'Siguiente turno' })
  const siguiente = (await leerDashboard(request)).siguienteTurno
  expect(siguiente, 'a pending ticket must be next').not.toBeNull()
  await expect(region.getByText(siguiente.codigo, { exact: true })).toBeVisible()
  await captura(page, 'conflicto-asesor-01-antes')

  let servido = false
  await page.route('**/api/v1/turnos/siguiente', async (route) => {
    servido = true
    await route.fulfill({ status: 409, json: CONFLICTO })
  })

  const recarga = page.waitForRequest((peticion) => servido && esLecturaDashboardRequest(peticion))
  await page.getByRole('button', { name: 'Llamar al siguiente' }).click()
  await recarga

  await expect(page.getByRole('alert')).toContainText(CONFLICTO.mensaje)
  await expect(page.getByRole('button', { name: 'Reintentar' })).toHaveCount(0)
  await expect(page.getByRole('table').getByRole('row')).not.toHaveCount(1)
  await expect(region.getByText(siguiente.codigo, { exact: true })).toBeVisible()
  await captura(page, 'conflicto-asesor-02-alerta-tras-recarga')
})

test("Browser holds a ticket missing from today's queue", async ({ page }) => {
  const id = await solicitarTurno(page)

  // Real body, minus the ticket the browser holds.
  await page.route(RUTA_DASHBOARD, async (route) => {
    const response = await route.fetch()
    const cuerpo = await response.json()
    await route.fulfill({ response, json: { ...cuerpo, turnos: cuerpo.turnos.filter((turno) => turno.id !== id) } })
  })

  await page.getByRole('button', { name: 'Actualizar estado' }).click()

  await expect(page.getByRole('alert')).toContainText('No encontramos ese turno.')
  await expect(page.getByText(/vencid|expir|ayer/i)).toHaveCount(0)
  await captura(page, 'turno-ausente-01-no-encontramos')
})

test('advisor 503 shows Reintentar and recovers', async ({ page }) => {
  // StrictMode loads twice in dev, so the mock stays until it is removed.
  const simular = (route) => route.fulfill({ status: 503, json: SERVICIO_NO_DISPONIBLE })
  await page.route(RUTA_DASHBOARD, simular)

  await page.goto('/?vista=asesor')
  const alerta = page.getByRole('alert')
  await expect(alerta).toContainText(SERVICIO_NO_DISPONIBLE.mensaje)
  await expect(page.getByRole('button', { name: 'Reintentar' })).toBeVisible()
  await captura(page, 'asesor-503-01-alerta-con-reintentar')

  await page.unroute(RUTA_DASHBOARD, simular)
  const recarga = page.waitForResponse((respuesta) => esLecturaDashboard(respuesta) && respuesta.status() === 200)
  await page.getByRole('button', { name: 'Reintentar' }).click()
  await recarga

  await expect(alerta).toBeHidden()
  await expect(page.getByRole('table').getByRole('row')).not.toHaveCount(1)
  await captura(page, 'asesor-503-02-recuperado')
})
