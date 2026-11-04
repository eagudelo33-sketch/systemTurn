import { test } from '@playwright/test'

// Saves a named screenshot under test-results/ and attaches it to the HTML report.
export async function captura(page, nombre) {
  const ruta = test.info().outputPath(`${nombre}.png`)
  await page.screenshot({ path: ruta })
  await test.info().attach(nombre, { path: ruta, contentType: 'image/png' })
}
