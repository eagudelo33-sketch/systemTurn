import { useCallback, useEffect, useMemo, useState } from 'react'
import { callNextTurn, getDashboardData } from '../services/turnosService.js'
import { estadoPanel } from '../utils/panelAsesor.js'
import '../asesor.css'

function formatTime(value) {
  if (!value) return 'Sin atención'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' })
}

async function cargarPanel() {
  try {
    return { data: await getDashboardData() }
  } catch (requestError) {
    return { message: requestError.message || 'No fue posible consultar los turnos.' }
  }
}

function AsesorView() {
  const [turns, setTurns] = useState(null)
  const [nextTurn, setNextTurn] = useState(null)
  const [status, setStatus] = useState('loading')
  const [error, setError] = useState('')
  const [conflicto, setConflicto] = useState(false)
  const [isCalling, setIsCalling] = useState(false)
  const [filter, setFilter] = useState('all')
  const [search, setSearch] = useState('')

  const aplicarResultado = useCallback((resultado) => {
    if (resultado.data) {
      setTurns(resultado.data.turns)
      setNextTurn(resultado.data.nextTurn)
      setStatus('ready')
      return true
    }
    setTurns(null)
    setNextTurn(null)
    setStatus('error')
    setError(resultado.message)
    return false
  }, [])

  const refreshDashboard = useCallback(async () => {
    setStatus('loading')
    setError('')
    setConflicto(false)
    return aplicarResultado(await cargarPanel())
  }, [aplicarResultado])

  useEffect(() => {
    let vigente = true
    cargarPanel().then((resultado) => {
      if (vigente) aplicarResultado(resultado)
    })
    return () => {
      vigente = false
    }
  }, [aplicarResultado])

  const visibleTurns = useMemo(() => (turns ?? []).filter((turn) => {
    const matchesFilter = filter === 'all' || turn.status === filter
    const matchesSearch = String(turn.number).toLowerCase().includes(search.toLowerCase())
    return matchesFilter && matchesSearch
  }), [filter, search, turns])
  const panel = estadoPanel({ turns, nextTurn, status, visibles: visibleTurns.length })

  async function handleCallNext() {
    if (isCalling || !nextTurn) return
    setIsCalling(true)
    setError('')
    try {
      await callNextTurn()
      await refreshDashboard()
    } catch (requestError) {
      const mensaje = requestError.message || 'No fue posible llamar al siguiente turno.'
      if (requestError.refreshable) {
        // The refresh clears errors, so show the conflict message only after it succeeds.
        if (await refreshDashboard()) {
          setError(mensaje)
          setConflicto(true)
        }
      } else {
        setError(mensaje)
        setStatus('ready')
      }
    } finally {
      setIsCalling(false)
    }
  }

  return (
    <div className="asesor">
      <div className="app-shell">
        <aside className="sidebar">
          <div className="brand">
            <div className="brand-mark" aria-hidden="true">ST</div>
            <div><strong>SystemTurn</strong><span>Fila Virtual</span></div>
          </div>
          <nav aria-label="Navegación principal">
            <a className="nav-item active" href="#inicio"><span>⌂</span>Inicio</a>
            <a className="nav-item" href="#turnos"><span>▤</span>Turnos</a>
          </nav>
          <div className="sidebar-footer">Atención clara, sin esperas innecesarias</div>
        </aside>
        <main className="main-content" id="inicio">
          <header className="topbar">
            <div className="mobile-brand"><div className="brand-mark">ST</div><strong>SystemTurn</strong></div>
            <div className="role"><span className="status-dot" />Asesor <span className="avatar">A</span></div>
          </header>
          <div className="content">
            <section className="welcome">
              <div>
                <p className="eyebrow">PANEL DE GESTIÓN</p>
                <h1>Buenos días, asesor</h1>
                <p>Gestiona la fila de turnos y brinda una atención eficiente.</p>
              </div>
              <div className={`data-mode ${panel.claseInsignia}`} role="status">
                <span className="status-dot" />
                {panel.insignia}
              </div>
            </section>
            {error && <div className="alert" role="alert">{error}{!conflicto && <button type="button" onClick={refreshDashboard}>Reintentar</button>}</div>}
            <section className="next-card" aria-labelledby="next-heading">
              <div className="next-copy">
                <div className="section-kicker">GESTIÓN DE FILA</div>
                <h2 id="next-heading">Siguiente turno</h2>
                <p>{panel.textoSiguiente}</p>
              </div>
              <div className="next-action">
                <div className="turn-number">{nextTurn ? nextTurn.number : '—'}</div>
                {nextTurn && <span className="request-time">Solicitado a las {formatTime(nextTurn.requestedAt)}</span>}
                <button className="primary-button" type="button" disabled={!nextTurn || isCalling || status === 'loading'} onClick={handleCallNext}>
                  {isCalling ? 'Llamando…' : 'Llamar al siguiente'} <span aria-hidden="true">→</span>
                </button>
              </div>
            </section>
            <section className="turns-section" id="turnos" aria-labelledby="turns-heading">
              <div className="section-heading">
                <div><h2 id="turns-heading">Lista de turnos</h2><p>Consulta el estado de todos los turnos de hoy.</p></div>
                <span className="total-badge">{panel.conteo}</span>
              </div>
              <div className="table-tools">
                <label className="search-box">⌕<span className="sr-only">Buscar turno</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar por número" /></label>
                <label className="filter-label">Estado:
                  <select value={filter} onChange={(event) => setFilter(event.target.value)}>
                    <option value="all">Todos</option>
                    <option value="pending">Pendientes</option>
                    <option value="attended">Atendidos</option>
                    <option value="cancelled">Cancelados</option>
                  </select>
                </label>
              </div>
              <div className="table-wrap">
                <table>
                  <thead><tr><th>Número</th><th>Hora de solicitud</th><th>Hora de atención</th><th>Estado</th></tr></thead>
                  <tbody>
                    {visibleTurns.map((turn) => (
                      <tr key={turn.id}>
                        <td><strong className="number-cell">{turn.number}</strong></td>
                        <td>{formatTime(turn.requestedAt)}</td>
                        <td>{formatTime(turn.attendedAt)}</td>
                        <td><span className={`state-pill ${turn.status}`}>{turn.statusLabel}</span></td>
                      </tr>
                    ))}
                    {panel.mensajeTabla && <tr><td className="empty-row" colSpan="4">{panel.mensajeTabla}</td></tr>}
                  </tbody>
                </table>
              </div>
            </section>
          </div>
        </main>
      </div>
    </div>
  )
}

export default AsesorView
