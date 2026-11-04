import { test, expect } from '@playwright/test'
import { esLecturaDashboard, esLlamadaAlSiguiente, leerDashboard } from './support/api.js'
import { captura } from './support/capturas.js'

// Opens the advisor view and waits until the table has rendered the live tickets.
async function abrirAsesor(page) {
  const [respuesta] = await Promise.all([page.waitForResponse(esLecturaDashboard), page.goto('/?vista=asesor')])
  await expect(page.getByRole('heading', { name: 'Lista de turnos' })).toBeVisible()
  return respuesta.json()
}

const filasDeDatos = (page) => page.getByRole('table').getByRole('row').filter({ hasNot: page.getByRole('columnheader') })

test('advisor loads the dashboard', async ({ page }) => {
  const dashboard = await abrirAsesor(page)
  const total = dashboard.turnos.length
  expect(total, 'the demo seed must provide tickets').toBeGreaterThan(0)

  await expect(page.getByText('Conectado al servidor')).toBeVisible()
  await expect(page.getByText(`${total} registros`)).toBeVisible()
  await expect(filasDeDatos(page)).toHaveCount(total)
  await captura(page, 'asesor-01-panel-cargado')
})

test('advisor searches by number', async ({ page, request }) => {
  await abrirAsesor(page)
  const buscar = page.getByLabel('Buscar turno')

  await buscar.fill('T-007')
  const esperadas = (await leerDashboard(request)).turnos.filter((turno) => turno.codigo.includes('T-007'))
  expect(esperadas.length, 'T-007 comes from the demo seed').toBeGreaterThan(0)
  await expect(filasDeDatos(page)).toHaveCount(esperadas.length)
  for (const fila of await filasDeDatos(page).all()) await expect(fila).toContainText('T-007')
  await captura(page, 'asesor-02-busqueda-con-resultado')

  await buscar.fill('T-999')
  await expect(page.getByText('No hay turnos que coincidan con la búsqueda.')).toBeVisible()
  await captura(page, 'asesor-03-busqueda-sin-resultado')
})

test('advisor filters by status', async ({ page, request }) => {
  await abrirAsesor(page)
  const { turnos } = await leerDashboard(request)
  const estado = page.getByRole('combobox', { name: /Estado/ })

  for (const [opcion, etiqueta] of [['Atendidos', 'Atendido'], ['Pendientes', 'Pendiente']]) {
    const esperados = turnos.filter((turno) => turno.estado === etiqueta).length
    expect(esperados, `the live dashboard must hold ${etiqueta} tickets`).toBeGreaterThan(0)
    await estado.selectOption({ label: opcion })
    await expect(filasDeDatos(page)).toHaveCount(esperados)
    for (const fila of await filasDeDatos(page).all()) await expect(fila).toContainText(etiqueta)
    await captura(page, `asesor-04-filtro-${opcion.toLowerCase()}`)
  }
})

test('advisor calls next', async ({ page, request }) => {
  const dashboard = await abrirAsesor(page)
  const siguiente = dashboard.siguienteTurno
  expect(siguiente, 'a pending ticket must be next').not.toBeNull()
  const region = page.getByRole('region', { name: 'Siguiente turno' })
  await expect(region.getByText(siguiente.codigo, { exact: true })).toBeVisible()
  await captura(page, 'asesor-05-antes-de-llamar')

  const llamada = page.waitForResponse(esLlamadaAlSiguiente)
  await page.getByRole('button', { name: 'Llamar al siguiente' }).click()
  expect((await llamada).status()).toBe(200)

  await expect(page.getByRole('row').filter({ hasText: siguiente.codigo })).toContainText('Atendido')
  const nuevo = (await leerDashboard(request)).siguienteTurno
  expect(nuevo?.codigo, 'the head of the queue must have moved').not.toBe(siguiente.codigo)
  await expect(nuevo ? region.getByText(nuevo.codigo, { exact: true }) : region.getByText('No hay turnos pendientes')).toBeVisible()
  await captura(page, 'asesor-06-despues-de-llamar')
})

test('view switch', async ({ page }) => {
  await page.goto('/')
  const nav = page.getByRole('navigation', { name: 'Cambiar de vista' })
  await expect(page.getByRole('heading', { name: '¡Hola!' })).toBeVisible()

  await nav.getByRole('link', { name: 'Asesor' }).click()
  await expect(page).toHaveURL(/vista=asesor/)
  await expect(page.getByRole('heading', { name: 'Buenos días, asesor' })).toBeVisible()
  await expect(nav.getByRole('link', { name: 'Asesor' })).toHaveAttribute('aria-current', 'page')
  await captura(page, 'vista-01-asesor')

  await nav.getByRole('link', { name: 'Cliente' }).click()
  await expect(page).not.toHaveURL(/vista=asesor/)
  await expect(page.getByRole('heading', { name: '¡Hola!' })).toBeVisible()
  await expect(nav.getByRole('link', { name: 'Cliente' })).toHaveAttribute('aria-current', 'page')
  await captura(page, 'vista-02-cliente')
})
