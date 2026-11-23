import { useSyncExternalStore } from 'react'

// Sesión HTTP Basic de los roles internos (boxes, cronometraje, mecánico, administrador).
// Vive solo en memoria: al recargar la página se vuelven a pedir las credenciales.
// El piloto no inicia sesión: sus endpoints son públicos y su reserva se identifica por un UUID.

type Sesion = { usuario: string; cabecera: string } | null

let actual: Sesion = null
const oyentes = new Set<() => void>()
const avisar = () => oyentes.forEach(o => o())

function codificar(texto: string) {
  return btoa(String.fromCharCode(...new TextEncoder().encode(texto)))
}

export function iniciarSesion(usuario: string, clave: string) {
  actual = { usuario, cabecera: `Basic ${codificar(`${usuario}:${clave}`)}` }
  avisar()
}

export function cerrarSesion() {
  if (actual === null) return
  actual = null
  avisar()
}

/** Cabecera Authorization de la sesión activa, o undefined si nadie ha iniciado sesión. */
export const cabeceraAutorizacion = () => actual?.cabecera

export function useUsuario() {
  return useSyncExternalStore(
    alCambiar => { oyentes.add(alCambiar); return () => { oyentes.delete(alCambiar) } },
    () => actual?.usuario ?? null,
  )
}
