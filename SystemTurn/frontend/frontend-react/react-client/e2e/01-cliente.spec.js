import { test, expect } from '@playwright/test'
import {
  esLecturaDashboard,
  esSolicitudDeTurno,
  leerDashboard,
  posicionEsperada,
} from './support/api.js'
import { captura } from './support/capturas.js'

// Opens the client view and requests a ticket; returns the id the backend assigned.
async function solicitarTurno(page) {
  await page.goto('/')
  const respuesta = page.waitForResponse(esSolicitudDeTurno)
  await page.getByRole('button', { name: 'Solicitar turno' }).click()
  const { id } = await (await respuesta).json()
  await expect(page.getByRole('button', { name: 'Ya tienes un turno' })).toBeVisible()
  return id
}

test('client requests a ticket', async ({ page, request }) => {
  const antes = await leerDashboard(request)
  expect(antes.turnos.length, 'DB not fresh: expected the 11-ticket demo seed').toBe(11)

  await page.goto('/')
  await captura(page, 'cliente-01-inicio')
  const respuesta = page.waitForResponse(esSolicitudDeTurno)
  await page.getByRole('button', { name: 'Solicitar turno' }).click()
  const { id } = await (await respuesta).json()

  // The status text is sr-only: attached and announced, never visible.
  await expect(page.getByText('Turno T-012 solicitado.')).toBeAttached()
  const tarjeta = page.getByRole('article', { name: 'Tu turno', exact: true })
  await expect(tarjeta).toContainText('T-012')
  await expect(tarjeta).toContainText('Pendiente')
  await expect(page.getByRole('button', { name: 'Ya tienes un turno' })).toBeVisible()

  const posicion = posicionEsperada(await leerDashboard(request), id)
  expect(posicion, 'the new ticket must be pending in the live dashboard').not.toBeNull()
  const fila = page.getByRole('region', { name: 'Tu turno en la fila', exact: true })
  await expect(fila.getByRole('cell').first()).toHaveText(String(posicion))
  await captura(page, 'cliente-02-turno-solicitado')
})

test('client cancel keep', async ({ page }) => {
  await solicitarTurno(page)

  await page.getByRole('button', { name: 'Cancelar turno' }).click()
  const aviso = page.getByText('Si cancelas, el asesor no llamará este turno.')
  await expect(aviso).toBeVisible()
  await captura(page, 'cliente-03-confirmar-cancelacion')

  await page.getByRole('button', { name: 'Conservar' }).click()
  await expect(aviso).toBeHidden()
  await expect(page.getByRole('article', { name: 'Tu turno', exact: true })).toContainText('Pendiente')
  await captura(page, 'cliente-04-turno-conservado')
})

test('client cancel confirm', async ({ page }) => {
  await solicitarTurno(page)

  await page.getByRole('button', { name: 'Cancelar turno' }).click()
  await page.getByRole('button', { name: 'Sí, cancelar' }).click()

  await expect(page.getByText('Cancelaste este turno. El asesor no lo llamará.')).toBeVisible()
  await expect(page.getByRole('article', { name: 'Tu turno', exact: true })).toContainText('Cancelado')
  await captura(page, 'cliente-05-turno-cancelado')
})

test('client stats', async ({ page }) => {
  const [respuesta] = await Promise.all([page.waitForResponse(esLecturaDashboard), page.goto('/')])
  const { metricas } = await respuesta.json()

  const region = page.getByRole('region', { name: 'Información de la fila' })
  const cifra = (nombre) => region.getByRole('article').filter({ hasText: nombre }).locator('strong')
  await expect(cifra('Personas en espera')).toHaveText(String(metricas.pendientes))
  await expect(cifra('Turnos atendidos hoy')).toHaveText(String(metricas.atendidos))
  await expect(page.getByText('Cifras obtenidas del servicio.')).toBeVisible()
  await captura(page, 'cliente-06-estadisticas')
})
