const TEXTO_SIGUIENTE = 'Este es el próximo turno según el orden registrado por el servidor.'

const FASES = {
  cargando: {
    insignia: 'Conectando con el servidor…',
    claseInsignia: 'cargando',
    textoSiguiente: 'Cargando el siguiente turno…',
    conteo: 'Cargando…',
    mensajeTabla: 'Cargando turnos…',
  },
  error: {
    insignia: 'Servidor no disponible',
    claseInsignia: 'error',
    textoSiguiente: 'No se pudo consultar el siguiente turno.',
    conteo: 'Registros no disponibles',
    mensajeTabla: 'No se pudieron cargar los turnos.',
  },
}

function mensajeTablaListo(turns, visibles) {
  if (visibles > 0) return null
  if (turns.length === 0) return 'Aún no hay turnos registrados hoy.'
  return 'No hay turnos que coincidan con la búsqueda.'
}

// Maps the advisor state to every visible piece of copy. `turns === null` means "unknown".
export function estadoPanel({ turns, nextTurn, status, visibles }) {
  if (turns === null) {
    const fase = status === 'error' ? 'error' : 'cargando'
    return { fase, ...FASES[fase] }
  }
  return {
    fase: 'listo',
    insignia: 'Conectado al servidor',
    claseInsignia: '',
    textoSiguiente: nextTurn ? TEXTO_SIGUIENTE : 'No hay turnos pendientes',
    conteo: `${turns.length} registros`,
    mensajeTabla: mensajeTablaListo(turns, visibles),
  }
}
