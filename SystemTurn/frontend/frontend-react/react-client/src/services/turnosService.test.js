import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  TURNO_NO_ENCONTRADO,
  callNextTurn,
  cancelarTurno,
  consultarTurno,
  solicitarTurno,
} from './turnosService.js'

function stubFetchResponse(status, body) {
  const response = {
    status,
    ok: status >= 200 && status < 300,
    json: () => Promise.resolve(body),
  }
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response))
}

describe('callNextTurn', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('maps an API 404 FILA_VACIA to the empty queue message', async () => {
    stubFetchResponse(404, { error: 'FILA_VACIA', mensaje: 'Fila vacía' })

    await expect(callNextTurn()).rejects.toThrow('No hay turnos pendientes en la fila.')
  })

  it('maps a network failure to the connection message', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    await expect(callNextTurn()).rejects.toThrow(
      'No pudimos conectar con el servidor. La solicitud no se repitió.',
    )
  })

  it('keeps the API message and code for other errors', async () => {
    stubFetchResponse(409, { error: 'OTRO', mensaje: 'Algo pasó' })

    await expect(callNextTurn()).rejects.toMatchObject({ message: 'Algo pasó', code: 'OTRO' })
  })

  it('returns the parsed body on success', async () => {
    stubFetchResponse(200, { id: 7, codigo: 'T-007' })

    await expect(callNextTurn()).resolves.toEqual({ id: 7, codigo: 'T-007' })
  })
})

describe('Service maps 503', () => {
  const MENSAJE = 'Servicio no disponible, intenta de nuevo en unos minutos.'

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('rejects solicitarTurno with the body mensaje and keeps the code, not refreshable', async () => {
    stubFetchResponse(503, { error: 'SERVICIO_NO_DISPONIBLE', mensaje: MENSAJE })

    const error = await solicitarTurno().catch((e) => e)

    expect(error.message).toBe(MENSAJE)
    expect(error.code).toBe('SERVICIO_NO_DISPONIBLE')
    expect(error.refreshable).toBeUndefined()
  })

  it('falls back to a friendly Spanish message when the body has no mensaje', async () => {
    stubFetchResponse(503, { error: 'SERVICIO_NO_DISPONIBLE' })

    await expect(callNextTurn()).rejects.toMatchObject({
      message: 'El servicio no está disponible en este momento. Intenta de nuevo en unos minutos.',
      code: 'SERVICIO_NO_DISPONIBLE',
    })
  })

  it('does not apply to a 503 without the SERVICIO_NO_DISPONIBLE code', async () => {
    stubFetchResponse(503, {})

    await expect(cancelarTurno(1)).rejects.toMatchObject({
      message: 'No se pudo completar la operación.',
    })
  })
})

describe('CONFLICTO mapping', () => {
  const MENSAJE = 'El turno cambió mientras se procesaba. Actualiza e intenta de nuevo.'
  const conflicto = { error: 'CONFLICTO', mensaje: MENSAJE }

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('flags a 409 CONFLICTO on callNextTurn as refreshable with a friendly message', async () => {
    stubFetchResponse(409, conflicto)

    await expect(callNextTurn()).rejects.toMatchObject({
      message: MENSAJE,
      code: 'CONFLICTO',
      refreshable: true,
    })
  })

  it('flags a 409 CONFLICTO on cancelarTurno as refreshable with a friendly message', async () => {
    stubFetchResponse(409, conflicto)

    await expect(cancelarTurno(12)).rejects.toMatchObject({
      message: MENSAJE,
      code: 'CONFLICTO',
      refreshable: true,
    })
  })

  it('uses the friendly message even when the body has no mensaje', async () => {
    stubFetchResponse(409, { error: 'CONFLICTO' })

    await expect(cancelarTurno(12)).rejects.toMatchObject({ message: MENSAJE, refreshable: true })
  })

  it('does not flag other errors as refreshable', async () => {
    stubFetchResponse(400, { error: 'OPERACION_INVALIDA', mensaje: 'No se puede cancelar.' })

    const error = await cancelarTurno(1).catch((e) => e)
    expect(error.message).toBe('No se puede cancelar.')
    expect(error.refreshable).toBeUndefined()
  })
})

describe('consultarTurno', () => {
  const MENSAJE_503 = 'Servicio no disponible, intenta de nuevo en unos minutos.'
  const dashboard = {
    turnos: [{ id: 5, codigo: 'T-005', estado: 'Pendiente', horaSolicitud: '09:00' }],
  }

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('consultarTurno rejects with the not-found code', async () => {
    stubFetchResponse(200, dashboard)

    await expect(consultarTurno(99)).rejects.toMatchObject({
      message: 'No encontramos ese turno.',
      code: TURNO_NO_ENCONTRADO,
    })
    expect(TURNO_NO_ENCONTRADO).toBe('TURNO_NO_ENCONTRADO')
  })

  it('consultarTurno resolves a present ticket', async () => {
    stubFetchResponse(200, dashboard)

    await expect(consultarTurno(5)).resolves.toMatchObject({
      id: 5,
      numero: 'T-005',
      estado: 'PENDIENTE',
      posicionEnFila: 1,
    })
  })

  it('503 is not converted to the not-found code', async () => {
    stubFetchResponse(503, { error: 'SERVICIO_NO_DISPONIBLE', mensaje: MENSAJE_503 })

    const error = await consultarTurno(5).catch((e) => e)

    expect(error.message).toBe(MENSAJE_503)
    expect(error.code).toBe('SERVICIO_NO_DISPONIBLE')
    expect(error.code).not.toBe(TURNO_NO_ENCONTRADO)
  })
})
