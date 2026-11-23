import { useCallback, useEffect, useState } from 'react'

/** Carga datos y expone recargar(); mantiene los últimos datos mientras recarga. */
export function useCarga<T>(fn: () => Promise<T>, deps: unknown[] = []) {
  const [datos, setDatos] = useState<T | null>(null)
  const [error, setError] = useState<string | null>(null)
  const recargar = useCallback(() => {
    fn().then(d => { setDatos(d); setError(null) }).catch(e => setError(e.message))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)
  useEffect(() => { recargar() }, [recargar])
  return { datos, error, recargar }
}

/** Ejecuta una acción mostrando un aviso con el resultado o el error de negocio. */
export function useAviso() {
  const [aviso, setAviso] = useState<{ tipo: 'ok' | 'error'; texto: string } | null>(null)
  const ejecutar = async <T,>(accion: () => Promise<T>, exito?: (r: T) => string) => {
    try {
      const r = await accion()
      if (exito) setAviso({ tipo: 'ok', texto: exito(r) })
      return r
    } catch (e) {
      setAviso({ tipo: 'error', texto: (e as Error).message })
      return undefined
    }
  }
  return { aviso, setAviso, ejecutar }
}
