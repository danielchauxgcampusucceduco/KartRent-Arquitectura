import type { ReactNode } from 'react'

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

export function Aviso({ aviso, cerrar }: { aviso: { tipo: 'ok' | 'error'; texto: string } | null; cerrar: () => void }) {
  if (!aviso) return null
  return (
    <div className={`aviso ${aviso.tipo}`} role="status">
      <span>{aviso.tipo === 'error' ? 'Regla de negocio' : 'Listo'}</span>
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
