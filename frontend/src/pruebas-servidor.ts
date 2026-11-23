import { vi } from 'vitest'

// Servidor falso para las pruebas de interfaz: sustituye fetch y registra cada llamada a /api/v1.
export type Llamada = { metodo: string; ruta: string; cuerpo: unknown; cabeceras: Record<string, string> }
export type Respuesta = { estado: number; cuerpo?: unknown; texto?: string }
export type Ruta = { metodo: string; ruta: RegExp; responder: (llamada: Llamada) => Respuesta }

export function servidorFalso(rutas: Ruta[]) {
  const llamadas: Llamada[] = []
  vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit = {}) => {
    const ruta = String(url).replace('/api/v1', '')
    const llamada: Llamada = {
      metodo: init.method ?? 'GET', ruta,
      cuerpo: typeof init.body === 'string' ? JSON.parse(init.body) : undefined,
      cabeceras: (init.headers ?? {}) as Record<string, string>,
    }
    llamadas.push(llamada)
    const coincide = rutas.find(r => r.metodo === llamada.metodo && r.ruta.test(ruta))
    const r = coincide ? coincide.responder(llamada) : { estado: 404, cuerpo: { title: 'No encontrado', detail: `Sin ruta ${ruta}` } }
    const texto = r.texto ?? (r.cuerpo === undefined ? '' : JSON.stringify(r.cuerpo))
    return new Response(texto, { status: r.estado })
  }))
  return llamadas
}

export const sesionFalsa = (extra = {}) => ({
  codigo: 'T-2030', horaInicio: '2026-11-24T20:30:00', duracionMinutos: 10, cuposMaximos: 8, cuposVendidos: 3,
  cuposDisponibles: 5, tipoKart: 'ADULTO', estado: 'PROGRAMADA', asignaciones: [], ...extra,
})

export const reservaFalsa = (extra = {}) => ({
  codigo: '11111111-1111-4111-8111-111111111111', documentoCliente: '1085123456', codigoSesion: 'T-2030',
  fechaCreacion: '2026-11-24T20:00:00', cupos: 1, valorTotal: 45000, estado: 'PENDIENTE_PAGO', venceRetencion: '2026-11-24T20:10:00',
  ...extra,
})

export const pagoFalso = (extra = {}) => ({
  referenciaPasarela: 'PAY-1', monto: 45000, metodo: 'TARJETA', aprobado: true, reembolsado: false, fechaPago: '2026-11-24T20:01:00', ...extra,
})

export const facturaFalsa = {
  numero: 'FE-0001', cufe: 'abc123', codigoReserva: '11111111-1111-4111-8111-111111111111',
  fechaEmision: '2026-11-24T20:01:00', subtotal: 37815, iva: 7185, total: 45000,
}
