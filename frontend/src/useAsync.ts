import { useCallback, useEffect, useRef, useState } from 'react'
import { comoErrorApi, type ErrorApi } from './api'

export type Carga<T> = { datos: T | null; error: ErrorApi | null; cargando: boolean; recargar: () => void }

/** Carga datos y expone recargar(); mantiene los últimos datos mientras recarga y descarta respuestas atrasadas. */
export function useCarga<T>(fn: () => Promise<T>, deps: unknown[] = []): Carga<T> {
  const [datos, setDatos] = useState<T | null>(null)
  const [error, setError] = useState<ErrorApi | null>(null)
  const [cargando, setCargando] = useState(true)
  const ultima = useRef(0)
  const recargar = useCallback(() => {
    const turno = ++ultima.current
    setCargando(true)
    fn()
      .then(d => { if (turno === ultima.current) { setDatos(d); setError(null) } })
      .catch(e => { if (turno === ultima.current) setError(comoErrorApi(e)) })
      .finally(() => { if (turno === ultima.current) setCargando(false) })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)
  useEffect(() => { recargar() }, [recargar])
  return { datos, error, cargando, recargar }
}

export type AvisoEstado = { tipo: 'ok' | 'error'; texto: string; titulo?: string }

/** Ejecuta una acción mostrando un aviso con el resultado o el error (título según 400/404/409/500/red). */
export function useAviso() {
  const [aviso, setAviso] = useState<AvisoEstado | null>(null)
  const ejecutar = async <T,>(accion: () => Promise<T>, exito?: (r: T) => string) => {
    try {
      const r = await accion()
      if (exito) setAviso({ tipo: 'ok', texto: exito(r) })
      return r
    } catch (e) {
      const error = comoErrorApi(e)
      setAviso({ tipo: 'error', texto: error.message, titulo: error.titulo })
      return undefined
    }
  }
  return { aviso, setAviso, ejecutar }
}
