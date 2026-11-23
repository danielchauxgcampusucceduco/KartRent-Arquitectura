// El piloto no inicia sesión: el navegador recuerda los códigos (UUID) de sus reservas y las consulta una a una.
const CLAVE = 'kartrent.reservas'
const MAXIMO = 20

export function codigosGuardados(): string[] {
  try {
    const v = JSON.parse(localStorage.getItem(CLAVE) ?? '[]')
    return Array.isArray(v) ? v.filter((c): c is string => typeof c === 'string') : []
  } catch {
    return []
  }
}

export function guardarCodigo(codigo: string) {
  try {
    localStorage.setItem(CLAVE, JSON.stringify([codigo, ...codigosGuardados().filter(c => c !== codigo)].slice(0, MAXIMO)))
  } catch {
    // Sin almacenamiento disponible (modo privado): la reserva sigue valiendo, solo no queda en el historial.
  }
}
