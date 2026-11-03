const BASE = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')

const ESTADOS = {
  Pendiente: 'PENDIENTE',
  Atendido: 'ATENDIDO',
  Cancelado: 'CANCELADO',
}

const ADVISOR_STATUS = {
  Pendiente: 'pending',
  Atendido: 'attended',
  Cancelado: 'cancelled',
}

export function usandoSimulacion() {
  return false
}

export const isDemoMode = false

async function request(path, options = {}) {
  let response
  try {
    response = await fetch(`${BASE}${path}`, {
      ...options,
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        ...options.headers,
      },
    })
  } catch {
    throw new Error('No pudimos conectar con el servidor. La solicitud no se repitió.')
  }

  if (response.status === 204) return null
  const data = await response.json().catch(() => null)
  if (!response.ok) {
    const error = new Error(data?.mensaje || 'No se pudo completar la operación.')
    error.code = data?.error
    throw error
  }
  return data
}

function estadoCliente(estado) {
  return ESTADOS[estado] ?? String(estado || '').toUpperCase()
}

function numeroONull(value) {
  if (value === null || value === undefined || value === '') return null
  const numero = Number(value)
  return Number.isFinite(numero) ? numero : null
}

function normalizarTurno(data, posicionFila = data?.posicionFila) {
  const numero = data?.codigo ?? data?.numero
  const estado = data?.estado
  if (numero === undefined || numero === null || !estado) {
    throw new Error('La respuesta del servidor no trae el número o el estado del turno.')
  }
  return {
    id: data.id,
    numero,
    estado: estadoCliente(estado),
    posicionEnFila: numeroONull(posicionFila),
    esperaEstimadaMinutos: null,
  }
}

function minutos(hora) {
  const match = String(hora || '').match(/(\d{1,2}):(\d{2})/)
  if (!match) return Number.MAX_SAFE_INTEGER
  return Number(match[1]) * 60 + Number(match[2])
}

function posicionEnDashboard(dash, id) {
  const pendientes = (dash.turnos || [])
    .filter((turno) => turno.estado === 'Pendiente')
    .sort((a, b) => minutos(a.horaSolicitud) - minutos(b.horaSolicitud) || a.id - b.id)
  const index = pendientes.findIndex((turno) => turno.id === id)
  return index >= 0 ? index + 1 : null
}

export async function solicitarTurno() {
  return normalizarTurno(await request('/api/v1/turnos', { method: 'POST', body: '{}' }))
}

export async function cancelarTurno(id) {
  return normalizarTurno(await request(`/api/v1/turnos/${encodeURIComponent(id)}/cancelar`, { method: 'PATCH' }))
}

export async function consultarTurno(id) {
  const dash = await request('/api/v1/turnos/dashboard')
  const propio = (dash.turnos || []).find((turno) => turno.id === id)
  if (!propio) throw new Error('No encontramos ese turno.')
  return normalizarTurno(propio, posicionEnDashboard(dash, id))
}

export async function obtenerEstadisticas() {
  try {
    const dash = await request('/api/v1/turnos/dashboard')
    const metricas = dash.metricas || {}
    return {
      personasEnEspera: numeroONull(metricas.pendientes),
      tiempoEsperaPromedioMinutos: numeroONull(metricas.esperaPromedioMinutos),
      turnosAtendidosHoy: numeroONull(metricas.atendidos),
    }
  } catch {
    return null
  }
}

function normalizeAdvisorTurn(turn) {
  const status = ADVISOR_STATUS[turn.estado] || turn.estado
  return {
    id: turn.id,
    number: turn.codigo ?? turn.numero,
    requestedAt: turn.horaSolicitud,
    attendedAt: !turn.horaAtencion || turn.horaAtencion === '-' ? null : turn.horaAtencion,
    status,
    statusLabel: turn.estado,
  }
}

export async function getDashboardData() {
  const data = await request('/api/v1/turnos/dashboard')
  return {
    turns: (data.turnos || []).map(normalizeAdvisorTurn),
    nextTurn: data.siguienteTurno ? normalizeAdvisorTurn(data.siguienteTurno) : null,
    metricas: data.metricas,
  }
}

export async function callNextTurn() {
  try {
    return await request('/api/v1/turnos/siguiente', { method: 'PATCH' })
  } catch (error) {
    if (error.code === 'FILA_VACIA') {
      throw new Error('No hay turnos pendientes en la fila.')
    }
    throw error
  }
}
