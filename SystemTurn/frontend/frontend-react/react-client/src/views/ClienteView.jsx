import { useEffect, useRef, useState } from 'react'
import {
  cancelarTurno,
  consultarTurno,
  obtenerEstadisticas,
  solicitarTurno,
  usandoSimulacion,
} from '../services/turnosService.js'
import {
  etiquetaEstado,
  formatearCantidad,
  formatearMinutos,
  formatearNumero,
} from '../utils/turnoFormat.js'
import '../App.css'

const SIMULACION = usandoSimulacion()

function ClienteView() {
  const [turno, setTurno] = useState(null)
  const [stats, setStats] = useState(null)
  const [phase, setPhase] = useState('idle')
  const [error, setError] = useState('')
  const [aviso, setAviso] = useState('')
  const [confirmarCancelacion, setConfirmarCancelacion] = useState(false)
  const ocupado = useRef(false)

  useEffect(() => {
    let vigente = true
    obtenerEstadisticas().then((data) => {
      if (vigente) setStats(data)
    })
    return () => {
      vigente = false
    }
  }, [turno?.numero, turno?.estado])

  async function ejecutar(accion, siguientePhase) {
    if (ocupado.current) return
    ocupado.current = true
    setError('')
    setAviso('')
    setPhase(siguientePhase)
    try {
      const data = await accion()
      setTurno(data)
      setPhase('idle')
      return data
    } catch (causa) {
      setError(causa instanceof Error ? causa.message : 'Ocurrió un error inesperado.')
      setPhase('idle')
      return null
    } finally {
      ocupado.current = false
    }
  }

  function onSolicitar() {
    if (turno?.estado === 'PENDIENTE' || phase !== 'idle') return
    setConfirmarCancelacion(false)
    ejecutar(solicitarTurno, 'requesting').then((data) => {
      if (data) setAviso(`Turno ${formatearNumero(data.numero)} solicitado.`)
    })
  }

  function onCancelar() {
    if (!turno || turno.estado !== 'PENDIENTE' || phase !== 'idle') return
    ejecutar(() => cancelarTurno(turno.id), 'cancelling').then((data) => {
      setConfirmarCancelacion(false)
      if (data) setAviso(`Turno ${formatearNumero(data.numero)} cancelado.`)
    })
  }

  function onActualizar() {
    if (!turno || phase !== 'idle') return
    ejecutar(() => consultarTurno(turno.id), 'refreshing').then((data) => {
      if (data) setAviso('Estado actualizado.')
    })
  }

  const pendiente = turno?.estado === 'PENDIENTE'
  const solicitando = phase === 'requesting'
  const cancelando = phase === 'cancelling'
  const actualizando = phase === 'refreshing'
  const puedeSolicitar = phase === 'idle' && !pendiente

  return (
    <main className="page">
      <section className="panel" aria-label="Vista del cliente">
        <Header />
        <div className="content">
          <p className="sr-only" aria-live="polite">
            {aviso}
          </p>
          {SIMULACION && (
            <p className="banner">
              Modo demostración: el backend de turnos todavía no está conectado. Los turnos de esta
              pantalla se simulan solo en tu navegador.
            </p>
          )}
          <Welcome />
          <button
            type="button"
            className="primary"
            onClick={onSolicitar}
            disabled={solicitando || pendiente || cancelando || actualizando}
            aria-busy={solicitando}
          >
            <TicketIcon />
            <span>
              {solicitando
                ? 'Solicitando turno...'
                : pendiente
                  ? 'Ya tienes un turno'
                  : 'Solicitar turno'}
            </span>
            <span className="arrow">{puedeSolicitar && <ArrowIcon />}</span>
          </button>
          {error && <p role="alert" className="alert">{error}</p>}
          <TurnoCard
            turno={turno}
            confirmar={confirmarCancelacion}
            cancelando={cancelando}
            onPedirConfirmacion={() => setConfirmarCancelacion(true)}
            onConservar={() => setConfirmarCancelacion(false)}
            onCancelar={onCancelar}
          />
          <StatsCards stats={stats} simulacion={SIMULACION} />
          <QueueSection turno={turno} />
          {turno && (
            <button
              type="button"
              className="linkish"
              onClick={onActualizar}
              disabled={phase !== 'idle'}
            >
              {actualizando ? 'Actualizando estado...' : 'Actualizar estado'}
            </button>
          )}
        </div>
      </section>
    </main>
  )
}

function Header() {
  return (
    <header className="header">
      <div className="brand">
        <TicketIcon />
        <strong>Fila Virtual de Turnos</strong>
      </div>
      <div className="role">
        <UserIcon />
        <span>Cliente</span>
      </div>
    </header>
  )
}

function Welcome() {
  return (
    <section className="welcome" aria-labelledby="saludo">
      <div>
        <h1 id="saludo">¡Hola!</h1>
        <h2>Solicita tu turno y espera a ser atendido.</h2>
        <p>Al finalizar tu solicitud, podrás ver el número de tu turno y el estado de la fila.</p>
      </div>
      <Illustration />
    </section>
  )
}

function Illustration() {
  return (
    <div className="illustration" aria-hidden="true">
      <svg viewBox="0 0 160 140" fill="none" xmlns="http://www.w3.org/2000/svg">
        <ellipse cx="72" cy="126" rx="40" ry="8" fill="#F3E8FF" />
        <path d="M44 122c2-28 12-40 28-40s26 12 28 40" fill="#7C3AED" />
        <rect x="66" y="76" width="12" height="10" rx="4" fill="#F3C7A6" />
        <circle cx="72" cy="60" r="18" fill="#F3C7A6" />
        <path d="M54 62c0-22 8-32 20-32 13 0 20 10 18 28-6-3-12 1-18 1s-14-3-20 3Z" fill="#32284A" />
        <path d="M54 66c-1 16 3 28 8 34 1-12 1-22 0-34Z" fill="#32284A" />
        <path d="M66 66c2 3 8 3 11 0" stroke="#C48A6A" strokeWidth="1.6" strokeLinecap="round" />
        <rect x="96" y="96" width="16" height="26" rx="3" fill="#2A2340" />
        <rect x="98" y="99" width="12" height="16" rx="1.5" fill="#DDD6FE" />
        <rect x="90" y="8" width="64" height="30" rx="15" fill="#7C3AED" />
        <path d="M104 38l7 8 8-8" fill="#7C3AED" />
        <text
          x="122"
          y="27"
          textAnchor="middle"
          fill="#fff"
          fontSize="12"
          fontFamily="Segoe UI, sans-serif"
          fontWeight="700"
        >
          Ejemplo
        </text>
      </svg>
    </div>
  )
}

function TurnoCard({ turno, confirmar, cancelando, onPedirConfirmacion, onConservar, onCancelar }) {
  const estado = turno?.estado
  const claseEstado = estado ? `status-${estado.toLowerCase()}` : 'status-vacio'
  const mensaje = mensajeTurno(turno)
  const espera = formatearMinutos(turno?.esperaEstimadaMinutos)

  return (
    <article className="card" aria-labelledby="tu-turno">
      <div className="turno-row">
        <div className="ticket-badge">
          <TicketIcon />
        </div>
        <div className="turno-copy">
          <span id="tu-turno">Tu turno</span>
          <strong className={turno ? undefined : 'muted'}>{formatearNumero(turno?.numero)}</strong>
        </div>
        <span className={`status ${claseEstado}`}>{etiquetaEstado(estado)}</span>
      </div>
      <p className="hint">
        {mensaje}
        {espera ? ` Espera estimada: ${espera}.` : ''}
      </p>
      {estado === 'PENDIENTE' && !confirmar && (
        <button type="button" className="ghost solo" onClick={onPedirConfirmacion} disabled={cancelando}>
          Cancelar turno
        </button>
      )}
      {estado === 'PENDIENTE' && confirmar && (
        <div className="confirm">
          <p>Si cancelas, el asesor no llamará este turno.</p>
          <div className="confirm-actions">
            <button type="button" className="danger" onClick={onCancelar} disabled={cancelando}>
              {cancelando ? 'Cancelando...' : 'Sí, cancelar'}
            </button>
            <button type="button" className="ghost" onClick={onConservar} disabled={cancelando}>
              Conservar
            </button>
          </div>
        </div>
      )}
    </article>
  )
}

function mensajeTurno(turno) {
  if (!turno) return 'Aún no tienes un turno asignado.'
  if (turno.estado === 'PENDIENTE') return 'Consulta el estado de tu turno en esta pantalla.'
  if (turno.estado === 'CANCELADO') return 'Cancelaste este turno. El asesor no lo llamará.'
  if (turno.estado === 'ATENDIDO') return 'Este turno ya fue atendido.'
  return 'Revisa el estado recibido para este turno.'
}

function StatsCards({ stats, simulacion }) {
  const personas = formatearCantidad(stats?.personasEnEspera)
  const tiempo = formatearMinutos(stats?.tiempoEsperaPromedioMinutos)
  const atendidos = formatearCantidad(stats?.turnosAtendidosHoy)

  return (
    <section aria-label="Información de la fila">
      <div className="stats">
        <Stat icon="violet" label="Personas en espera" value={personas} glyph={<PeopleIcon />} />
        <Stat icon="green" label="Tiempo de espera promedio" value={tiempo} glyph={<ClockIcon />} />
        <Stat icon="blue" label="Turnos atendidos hoy" value={atendidos} glyph={<CheckIcon />} />
      </div>
      <p className="stat-note">
        {simulacion
          ? 'Estas cifras salen de la simulación local. El tiempo promedio no está disponible porque ningún turno ha sido atendido aquí.'
          : stats
            ? 'Cifras obtenidas del servicio.'
            : 'Las estadísticas no están disponibles: el servicio no entregó estos datos.'}
      </p>
    </section>
  )
}

function Stat({ icon, label, value, glyph }) {
  return (
    <article className="stat">
      <div className={`stat-icon ${icon}`}>{glyph}</div>
      <span>{label}</span>
      <strong>{value ?? '—'}</strong>
    </article>
  )
}

function textoPosicion(turno) {
  if (typeof turno.posicionEnFila === 'number') return String(turno.posicionEnFila)
  if (turno.estado === 'PENDIENTE') return 'No disponible'
  return '—'
}

function QueueSection({ turno }) {
  return (
    <section className="queue" aria-labelledby="fila-titulo">
      <div className="queue-title" id="fila-titulo">
        Tu turno en la fila
      </div>
      <table>
        <thead>
          <tr>
            <th scope="col">Posición</th>
            <th scope="col">Número de turno</th>
            <th scope="col">Estado</th>
          </tr>
        </thead>
        <tbody>
          {turno ? (
            <tr>
              <td>{textoPosicion(turno)}</td>
              <td>{formatearNumero(turno.numero)}</td>
              <td>
                <span className={`status status-${turno.estado.toLowerCase()}`}>
                  {etiquetaEstado(turno.estado)}
                </span>
              </td>
            </tr>
          ) : (
            <tr className="empty-row">
              <td colSpan={3}>Cuando solicites un turno, verás aquí tu posición y tu estado.</td>
            </tr>
          )}
        </tbody>
      </table>
      {turno?.estado === 'PENDIENTE' && typeof turno.posicionEnFila !== 'number' && (
        <p className="hint">La posición en la fila no está disponible.</p>
      )}
      <p className="queue-note">
        <ClockIcon />
        Los turnos se atienden en orden de llegada.
      </p>
    </section>
  )
}

function TicketIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M4 8.5A2.5 2.5 0 0 1 6.5 6h11A2.5 2.5 0 0 1 20 8.5V10a1.5 1.5 0 0 0 0 3v1.5a2.5 2.5 0 0 1-2.5 2.5h-11A2.5 2.5 0 0 1 4 14.5V13a1.5 1.5 0 0 0 0-3V8.5Z"
        stroke="currentColor"
        strokeWidth="1.8"
      />
      <path d="M9 9.5v5M12 9.5v5M15 9.5v5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  )
}

function ArrowIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M5 12h14M13 6l6 6-6 6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

function UserIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="8" r="3.2" stroke="currentColor" strokeWidth="1.8" />
      <path d="M6 18.5c1.2-2.6 3.2-3.8 6-3.8s4.8 1.2 6 3.8" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  )
}

function PeopleIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="9" cy="9" r="2.4" stroke="currentColor" strokeWidth="1.8" />
      <circle cx="16" cy="10" r="2" stroke="currentColor" strokeWidth="1.8" />
      <path d="M4.8 17.5c.8-2.2 2.4-3.2 4.2-3.2 1.8 0 3.4 1 4.2 3.2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
      <path d="M13.5 14.6c1.4.1 2.6.9 3.3 2.4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  )
}

function ClockIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="7" stroke="currentColor" strokeWidth="1.8" />
      <path d="M12 8.5V12l2.5 2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

function CheckIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="7" stroke="currentColor" strokeWidth="1.8" />
      <path d="M8.8 12.2 11 14.4 15.4 9.8" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

export default ClienteView
