const ESPERA_POR_POSICION_MIN = 4

let secuencia = 0
const turnos = new Map()

function esperar(ms) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms)
  })
}

function clonar(turno) {
  return {
    numero: turno.numero,
    estado: turno.estado,
    posicionEnFila: turno.posicionEnFila,
    esperaEstimadaMinutos: turno.esperaEstimadaMinutos,
  }
}

function reindexar() {
  let posicion = 1
  for (const turno of turnos.values()) {
    if (turno.estado === 'PENDIENTE') {
      turno.posicionEnFila = posicion
      turno.esperaEstimadaMinutos = posicion * ESPERA_POR_POSICION_MIN
      posicion += 1
    } else {
      turno.posicionEnFila = null
      turno.esperaEstimadaMinutos = null
    }
  }
}

export async function simularSolicitarTurno() {
  await esperar(700)
  secuencia += 1
  const turno = {
    numero: secuencia,
    estado: 'PENDIENTE',
    posicionEnFila: null,
    esperaEstimadaMinutos: null,
  }
  turnos.set(turno.numero, turno)
  reindexar()
  return clonar(turno)
}

export async function simularCancelarTurno(numero) {
  await esperar(500)
  const turno = turnos.get(Number(numero))
  if (!turno) {
    throw new Error('No encontramos ese turno en la simulación.')
  }
  if (turno.estado === 'PENDIENTE') {
    turno.estado = 'CANCELADO'
    reindexar()
  }
  return clonar(turno)
}

export async function simularConsultarTurno(numero) {
  await esperar(300)
  const turno = turnos.get(Number(numero))
  if (!turno) {
    throw new Error('No encontramos ese turno en la simulación.')
  }
  return clonar(turno)
}

export async function simularEstadisticas() {
  await esperar(200)
  const valores = [...turnos.values()]
  const atendidos = valores.filter((turno) => turno.estado === 'ATENDIDO')
  return {
    personasEnEspera: valores.filter((turno) => turno.estado === 'PENDIENTE').length,
    tiempoEsperaPromedioMinutos: null,
    turnosAtendidosHoy: atendidos.length,
  }
}
