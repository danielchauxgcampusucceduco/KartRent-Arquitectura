import { useSyncExternalStore } from 'react'

// Estado "el servidor está arrancando". En el despliegue gratuito el backend se apaga tras un rato sin uso y
// tarda uno o dos minutos en volver: mientras tanto el proxy responde 502/503/504 y las consultas GET se reintentan.

let esperando = false
const oyentes = new Set<() => void>()

export function marcarArranque(valor: boolean) {
  if (esperando === valor) return
  esperando = valor
  oyentes.forEach(o => o())
}

export function useServidorArrancando() {
  return useSyncExternalStore(
    alCambiar => { oyentes.add(alCambiar); return () => { oyentes.delete(alCambiar) } },
    () => esperando,
  )
}

/** Estados con los que el proxy indica que el backend todavía no responde. */
export const esArranque = (estado: number) => estado === 502 || estado === 503 || estado === 504
