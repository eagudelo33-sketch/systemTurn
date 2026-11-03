import { useEffect, useState } from 'react'
import AsesorView from './views/AsesorView.jsx'
import ClienteView from './views/ClienteView.jsx'

function vistaActual() {
  return new URLSearchParams(window.location.search).get('vista') === 'asesor' ? 'asesor' : 'cliente'
}

function App() {
  const [vista, setVista] = useState(vistaActual)

  useEffect(() => {
    const actualizar = () => setVista(vistaActual())
    window.addEventListener('popstate', actualizar)
    return () => window.removeEventListener('popstate', actualizar)
  }, [])

  function cambiarVista(siguiente, event) {
    event.preventDefault()
    const url = siguiente === 'asesor' ? '?vista=asesor' : window.location.pathname
    window.history.pushState({}, '', url)
    setVista(siguiente)
  }

  return (
    <>
      {vista === 'asesor' ? <AsesorView /> : <ClienteView />}
      <nav className="vista-switch" aria-label="Cambiar de vista">
        <a href={window.location.pathname} aria-current={vista === 'cliente' ? 'page' : undefined} onClick={(event) => cambiarVista('cliente', event)}>Cliente</a>
        <a href="?vista=asesor" aria-current={vista === 'asesor' ? 'page' : undefined} onClick={(event) => cambiarVista('asesor', event)}>Asesor</a>
      </nav>
    </>
  )
}

export default App
