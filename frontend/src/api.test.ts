import { describe, expect, it } from 'vitest'
import { api, ErrorApi } from './api'
import { iniciarSesion, cabeceraAutorizacion } from './sesion'
import { servidorFalso } from './pruebas-servidor'
import { useServidorArrancando } from './arranque'
import { renderHook } from '@testing-library/react'

const falla = async (p: Promise<unknown>) => p.then(() => { throw new Error('debía fallar') }, e => e as ErrorApi)

describe('cliente HTTP: errores de la API', () => {
  it.each([
    [400, 'validacion', 'Datos o regla de negocio'],
    [404, 'no-encontrado', 'No encontrado'],
    [409, 'conflicto', 'Conflicto con el estado actual'],
    [500, 'servidor', 'Error del servidor'],
  ])('HTTP %i se clasifica como %s', async (estado, tipo, titulo) => {
    servidorFalso([{ metodo: 'GET', ruta: /sesiones/, responder: () => ({ estado, cuerpo: { title: 'x', detail: 'detalle del servidor' } }) }])
    const e = await falla(api.sesiones())
    expect(e).toBeInstanceOf(ErrorApi)
    expect(e.tipo).toBe(tipo)
    expect(e.estado).toBe(estado)
    // Los errores de cliente muestran el título del Problem Details; el 500 usa el título fijo para no filtrar detalles internos.
    expect(e.titulo).toBe(estado === 500 ? titulo : 'x')
    expect(e.message).toBe('detalle del servidor')
  })

  it('un 500 con HTML (sin JSON) no revienta: usa el mensaje por defecto', async () => {
    servidorFalso([{ metodo: 'GET', ruta: /sesiones/, responder: () => ({ estado: 500, texto: '<html>Bad gateway</html>' }) }])
    const e = await falla(api.sesiones())
    expect(e.tipo).toBe('servidor')
    expect(e.message).toMatch(/Error inesperado del servidor/)
  })

  it('un fallo de red en una operación que modifica datos se informa como sin conexión, sin reintentar', async () => {
    const { vi } = await import('vitest')
    const f = vi.fn(async () => { throw new TypeError('Failed to fetch') })
    vi.stubGlobal('fetch', f)
    const e = await falla(api.reservar('1', 'T-1', 1))
    expect(f).toHaveBeenCalledTimes(1)
    expect(e.tipo).toBe('red')
    expect(e.titulo).toBe('Sin conexión con el servidor')
  })

  it('una respuesta exitosa que no es JSON se rechaza', async () => {
    servidorFalso([{ metodo: 'GET', ruta: /sesiones/, responder: () => ({ estado: 200, texto: 'no es json' }) }])
    const e = await falla(api.sesiones())
    expect(e.message).toMatch(/no es JSON válido/)
  })

  it('un 401 con sesión iniciada la cierra para volver a pedir las credenciales', async () => {
    servidorFalso([{ metodo: 'GET', ruta: /flota/, responder: () => ({ estado: 401, cuerpo: { detail: 'Credenciales inválidas' } }) }])
    iniciarSesion('admin', 'clave-incorrecta')
    expect(cabeceraAutorizacion()).toBe(`Basic ${btoa('admin:clave-incorrecta')}`)
    const e = await falla(api.karts())
    expect(e.tipo).toBe('no-autenticado')
    expect(cabeceraAutorizacion()).toBeUndefined()
  })
})

describe('cliente HTTP: servidor arrancando (despliegue gratuito)', () => {
  it('una consulta GET espera mientras el proxy responde 503 y luego entrega los datos', async () => {
    const { vi } = await import('vitest')
    vi.useFakeTimers()
    let llamadas = 0
    servidorFalso([{ metodo: 'GET', ruta: /sesiones/, responder: () => (++llamadas < 3 ? { estado: 503, texto: '<html>Loading</html>' } : { estado: 200, cuerpo: [] }) }])
    const aviso = renderHook(() => useServidorArrancando())
    const p = api.sesiones()
    await vi.advanceTimersByTimeAsync(2000)
    expect(aviso.result.current).toBe(true)
    await vi.advanceTimersByTimeAsync(3000)
    await expect(p).resolves.toEqual([])
    expect(llamadas).toBe(3)
    expect(aviso.result.current).toBe(false)
    vi.useRealTimers()
  })

  it('una operación POST con 503 no se repite (podría duplicar la reserva)', async () => {
    let llamadas = 0
    servidorFalso([{ metodo: 'POST', ruta: /reservas/, responder: () => { llamadas++; return { estado: 503, texto: 'Loading' } } }])
    const e = await falla(api.reservar('1', 'T-1', 1))
    expect(e.tipo).toBe('servidor')
    expect(llamadas).toBe(1)
  })
})
