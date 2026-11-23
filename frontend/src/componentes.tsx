import { useState, type FormEvent, type ReactNode } from 'react'
import { cerrarSesion, iniciarSesion, useUsuario } from './sesion'
import type { AvisoEstado, Carga as EstadoCarga } from './useAsync'

export function Titulo({ hu, titulo, rol, children }: { hu: string; titulo: string; rol: string; children?: ReactNode }) {
  return (
    <div className="titulo">
      <div>
        <span className="etiqueta">{hu} · {rol}</span>
        <h1>{titulo}</h1>
      </div>
      {children}
    </div>
  )
}

export function Aviso({ aviso, cerrar }: { aviso: AvisoEstado | null; cerrar: () => void }) {
  if (!aviso) return null
  return (
    <div className={`aviso ${aviso.tipo}`} role={aviso.tipo === 'error' ? 'alert' : 'status'}>
      <span>{aviso.titulo ?? (aviso.tipo === 'error' ? 'Error' : 'Listo')}</span>
      {aviso.texto}
      <button onClick={cerrar} aria-label="Cerrar">×</button>
    </div>
  )
}

export function Estado({ valor }: { valor: string }) {
  return <span className={`estado e-${valor.toLowerCase()}`}>{valor.replaceAll('_', ' ')}</span>
}

export function Vacio({ children }: { children: ReactNode }) {
  return <p className="vacio">{children}</p>
}

/** Estados de una carga remota: cargando, error con reintento, vacío y datos. Lo usan Piloto, Taller y Admin. */
export function Carga<T>({ carga, vacio, children }: {
  carga: EstadoCarga<T>; vacio?: ReactNode; children: (datos: T) => ReactNode
}) {
  const { datos, error, cargando, recargar } = carga
  const falla = error && (
    <div className="error-carga" role="alert">
      <strong>{error.titulo}</strong>
      <span>{error.message}</span>
      <button className="secundario" onClick={recargar}>Reintentar</button>
    </div>
  )
  if (datos === null) return falla || (cargando ? <p className="cargando" role="status">Cargando…</p> : null)
  if (Array.isArray(datos) && datos.length === 0 && vacio) return <>{falla}<Vacio>{vacio}</Vacio></>
  return <>{falla}{children(datos)}</>
}

/** Inicio de sesión de los roles internos (HTTP Basic). El piloto no lo necesita. */
export function FormularioAcceso({ rol, ayuda }: { rol: string; ayuda: string }) {
  const [usuario, setUsuario] = useState('')
  const [clave, setClave] = useState('')
  const enviar = (e: FormEvent) => {
    e.preventDefault()
    if (usuario.trim() && clave) iniciarSesion(usuario.trim(), clave)
  }
  return (
    <form className="panel acceso" onSubmit={enviar}>
      <h2>Acceso · {rol}</h2>
      <p className="nota">{ayuda}</p>
      <input value={usuario} onChange={e => setUsuario(e.target.value)} placeholder="Usuario" aria-label="Usuario" autoComplete="username" />
      <input value={clave} onChange={e => setClave(e.target.value)} type="password" placeholder="Clave" aria-label="Clave" autoComplete="current-password" />
      <button type="submit" disabled={!usuario.trim() || !clave}>Entrar</button>
    </form>
  )
}

/** Muestra la pantalla solo con sesión iniciada; si no, pide las credenciales del rol. */
export function Protegida({ rol, ayuda, children }: { rol: string; ayuda: string; children: ReactNode }) {
  const usuario = useUsuario()
  return usuario ? <>{children}</> : <FormularioAcceso rol={rol} ayuda={ayuda} />
}

export function BarraSesion() {
  const usuario = useUsuario()
  if (!usuario) return null
  return (
    <div className="sesion">
      <span>Sesión: <b>{usuario}</b></span>
      <button className="secundario" onClick={cerrarSesion}>Salir</button>
    </div>
  )
}
