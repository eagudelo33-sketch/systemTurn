import { describe, expect, it } from 'vitest'
import { estadoPanel } from './panelAsesor.js'

const turno = { id: 1, number: 'T-001' }

describe('estadoPanel', () => {
  it('reports cargando while the first load has no data', () => {
    expect(estadoPanel({ turns: null, nextTurn: null, status: 'loading', visibles: 0 })).toEqual({
      fase: 'cargando',
      insignia: 'Conectando con el servidor…',
      claseInsignia: 'cargando',
      textoSiguiente: 'Cargando el siguiente turno…',
      conteo: 'Cargando…',
      mensajeTabla: 'Cargando turnos…',
    })
  })

  it('reports error when a load failed and no data is known', () => {
    expect(estadoPanel({ turns: null, nextTurn: null, status: 'error', visibles: 0 })).toEqual({
      fase: 'error',
      insignia: 'Servidor no disponible',
      claseInsignia: 'error',
      textoSiguiente: 'No se pudo consultar el siguiente turno.',
      conteo: 'Registros no disponibles',
      mensajeTabla: 'No se pudieron cargar los turnos.',
    })
  })

  it('reports listo with data and no table message', () => {
    expect(estadoPanel({ turns: [turno, turno], nextTurn: turno, status: 'ready', visibles: 2 })).toEqual({
      fase: 'listo',
      insignia: 'Conectado al servidor',
      claseInsignia: '',
      textoSiguiente: 'Este es el próximo turno según el orden registrado por el servidor.',
      conteo: '2 registros',
      mensajeTabla: null,
    })
  })

  it('reports an empty day as empty, not as an error', () => {
    expect(estadoPanel({ turns: [], nextTurn: null, status: 'ready', visibles: 0 })).toMatchObject({
      fase: 'listo',
      textoSiguiente: 'No hay turnos pendientes',
      conteo: '0 registros',
      mensajeTabla: 'Aún no hay turnos registrados hoy.',
    })
  })

  it('reports a filter with no match when the day has turns', () => {
    expect(estadoPanel({ turns: [turno], nextTurn: turno, status: 'ready', visibles: 0 })).toMatchObject({
      fase: 'listo',
      conteo: '1 registros',
      mensajeTabla: 'No hay turnos que coincidan con la búsqueda.',
    })
  })
})
