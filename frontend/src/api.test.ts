import { describe, expect, it } from 'vitest'
import { api, ErrorApi } from './api'
import { iniciarSesion, cabeceraAutorizacion } from './sesion'
import { servidorFalso } from './pruebas-servidor'

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

  it('un fallo de red se informa como sin conexión', async () => {
    const { vi } = await import('vitest')
    vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('Failed to fetch') }))
    const e = await falla(api.sesiones())
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
