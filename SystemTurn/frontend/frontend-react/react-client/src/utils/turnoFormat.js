const ESTADOS = {
  PENDIENTE: 'Pendiente',
  ATENDIDO: 'Atendido',
  CANCELADO: 'Cancelado',
}

export function etiquetaEstado(estado) {
  if (!estado) return 'Sin turno'
  return ESTADOS[estado] ?? estado
}

export function formatearNumero(numero) {
  if (numero === null || numero === undefined || numero === '') return '—'
  const texto = String(numero)
  if (/^\d+$/.test(texto)) return `T-${texto.padStart(3, '0')}`
  return texto
}

export function formatearMinutos(valor) {
  if (valor === null || valor === undefined) return null
  const texto = new Intl.NumberFormat('es-CO', {
    maximumFractionDigits: 1,
  }).format(valor)
  return `${texto} min`
}

export function formatearCantidad(valor) {
  if (valor === null || valor === undefined) return null
  return new Intl.NumberFormat('es-CO').format(valor)
}
