// Cliente HTTP de la API KartRent (/api/v1). Los errores llegan como Problem Details (RFC 7807).
import { cabeceraAutorizacion, cerrarSesion } from './sesion'
import { esArranque, marcarArranque } from './arranque'

export type Cliente = { documento: string; nombre: string; correo: string; telefono: string; fechaNacimiento: string; edad: number; exoneracionFirmada: boolean }
export type Reserva = { codigo: string; documentoCliente: string; codigoSesion: string; fechaCreacion: string; cupos: number; valorTotal: number; estado: string; venceRetencion: string }
export type Factura = { numero: string; cufe: string; codigoReserva: string; fechaEmision: string; subtotal: number; iva: number; total: number }
export type Pago = { referenciaPasarela: string; monto: number; metodo: string; aprobado: boolean; reembolsado: boolean; fechaPago: string }
export type Asignacion = { numeroParrilla: number; codigoReserva: string; documentoPiloto: string; nombrePiloto: string; numeroKart: number; codigoTransponder: string; vueltas: number }
export type Sesion = { codigo: string; horaInicio: string; duracionMinutos: number; cuposMaximos: number; cuposVendidos: number; cuposDisponibles: number; tipoKart: string; estado: string; inicioReal?: string; finReal?: string; asignaciones: Asignacion[] }
export type Kart = { numero: number; tipo: string; estado: string; kilometraje: number; horasMotor: number; horasDesdeServicio: number; codigoTransponder: string; requiereMantenimiento: boolean }
export type ReservaPendiente = { codigoReserva: string; documentoCliente: string; nombreCliente: string; cupos: number; asignados: number }
export type Parrilla = { sesion: Sesion; reservas: ReservaPendiente[]; kartsDisponibles: Kart[] }
export type Linea = { posicion: number; numeroKart: number; piloto: string; vueltas: number; mejorVueltaMs: number | null; ultimaVueltaMs: number | null }
export type Clasificacion = { codigoSesion: string; estado: string; clasificacion: Linea[] }
export type Orden = { numero: string; numeroKart: number; tipo: string; estado: string; fechaApertura: string; fechaCierre?: string; horasMotorAlAbrir: number; codigoMecanico?: string; observaciones?: string }
export type Mecanico = { codigoEmpleado: string; nombre: string; especialidad: string }
export type Tarifa = { tipoKart: string; valorPorCupo: number; vigenteDesde: string }
export type LineaReporte = { codigoSesion: string; inicio: string; tipoKart: string; cuposOfrecidos: number; cuposVendidos: number; ocupacionPorcentaje: number; ingresos: number }
export type Reporte = { tandas: LineaReporte[]; cuposOfrecidos: number; cuposVendidos: number; ocupacionPorcentaje: number; ingresos: number }

export type TipoError = 'validacion' | 'no-autenticado' | 'prohibido' | 'no-encontrado' | 'conflicto' | 'servidor' | 'red' | 'otro'

const TITULOS: Record<TipoError, string> = {
  validacion: 'Datos o regla de negocio',
  'no-autenticado': 'Inicia sesión',
  prohibido: 'Sin permiso',
  'no-encontrado': 'No encontrado',
  conflicto: 'Conflicto con el estado actual',
  servidor: 'Error del servidor',
  red: 'Sin conexión con el servidor',
  otro: 'Error',
}

const MENSAJES: Record<TipoError, string> = {
  validacion: 'La solicitud no cumple las reglas de negocio.',
  'no-autenticado': 'Credenciales requeridas o incorrectas.',
  prohibido: 'Tu usuario no tiene permiso para esta operación.',
  'no-encontrado': 'El recurso pedido no existe.',
  conflicto: 'La operación choca con el estado actual del recurso.',
  servidor: 'Error inesperado del servidor. Inténtalo de nuevo en unos minutos.',
  red: 'No se pudo conectar con el servidor. Revisa tu conexión e inténtalo de nuevo.',
  otro: 'Ocurrió un error inesperado.',
}

/** Clasifica un código HTTP (0 = sin respuesta) en la taxonomía de errores que maneja la interfaz. */
export function tipoDeEstado(estado: number): TipoError {
  if (estado === 0) return 'red'
  if (estado === 400) return 'validacion'
  if (estado === 401) return 'no-autenticado'
  if (estado === 403) return 'prohibido'
  if (estado === 404) return 'no-encontrado'
  if (estado === 409) return 'conflicto'
  if (estado >= 500) return 'servidor'
  return 'otro'
}

export class ErrorApi extends Error {
  estado: number
  tipo: TipoError
  titulo: string
  constructor(estado: number, mensaje?: string, titulo?: string) {
    const tipo = tipoDeEstado(estado)
    super(mensaje || MENSAJES[tipo])
    this.estado = estado
    this.tipo = tipo
    this.titulo = titulo || TITULOS[tipo]
  }
}

/** Convierte cualquier excepción en ErrorApi para que las pantallas traten un solo tipo. */
export function comoErrorApi(e: unknown): ErrorApi {
  if (e instanceof ErrorApi) return e
  return new ErrorApi(0, e instanceof Error ? e.message : undefined, 'Error')
}

function leerJson(texto: string): unknown {
  try { return JSON.parse(texto) } catch { return undefined }
}

const textoDe = (v: unknown) => (typeof v === 'string' && v.trim() ? v : undefined)

// Consultas GET que se reintentan mientras el backend arranca (hasta unos tres minutos).
const ESPERAS_MS = [2000, 3000, 5000, 5000, 8000, 10000, 10000, 15000, 15000, 15000, 20000, 20000, 20000, 20000, 20000]
const dormir = (ms: number) => new Promise(r => setTimeout(r, ms))

async function enviar(metodo: string, ruta: string, cuerpo: unknown, cabeceras: Record<string, string>, autorizacion?: string) {
  const pedir = () => fetch(`/api/v1${ruta}`, {
    method: metodo,
    headers: { 'Content-Type': 'application/json', ...(autorizacion ? { Authorization: autorizacion } : {}), ...cabeceras },
    body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
  })
  // Solo se reintentan lecturas: repetir un POST podría duplicar una operación.
  const reintentable = metodo === 'GET'
  for (let intento = 0; ; intento++) {
    let r: Response | null = null
    try { r = await pedir() } catch { r = null }
    const arrancando = r === null || esArranque(r.status)
    if (!arrancando || !reintentable || intento >= ESPERAS_MS.length) {
      marcarArranque(false)
      if (r === null) throw new ErrorApi(0)
      return r
    }
    marcarArranque(true)
    await dormir(ESPERAS_MS[intento])
  }
}

async function llamar<T>(metodo: string, ruta: string, cuerpo?: unknown, cabeceras: Record<string, string> = {}): Promise<T> {
  const autorizacion = cabeceraAutorizacion()
  const r = await enviar(metodo, ruta, cuerpo, cabeceras, autorizacion)
  const texto = await r.text().catch(() => '')
  const datos = texto ? leerJson(texto) : null
  if (!r.ok) {
    // Problem Details (RFC 7807) cuando el servidor lo envía; si responde HTML o texto plano, se usa el mensaje por defecto.
    const problema = (datos && typeof datos === 'object' ? datos : {}) as Record<string, unknown>
    if (r.status === 401 && autorizacion) cerrarSesion()
    throw new ErrorApi(r.status, textoDe(problema.detail), textoDe(problema.title) && r.status < 500 ? textoDe(problema.title) : undefined)
  }
  if (texto && datos === undefined) throw new ErrorApi(502, 'La respuesta del servidor no es JSON válido.')
  return datos as T
}

export const api = {
  sesiones: () => llamar<Sesion[]>('GET', '/sesiones'),
  sesion: (c: string) => llamar<Sesion>('GET', `/sesiones/${c}`),
  programarTanda: (t: { codigo: string; inicio: string; duracionMinutos: number; cupos: number; tipoKart: string }) => llamar<Sesion>('POST', '/sesiones', t),
  clientes: () => llamar<Cliente[]>('GET', '/clientes'),
  cliente: (doc: string) => llamar<Cliente>('GET', `/clientes/${doc}`),
  registrarCliente: (c: Omit<Cliente, 'edad' | 'exoneracionFirmada'> & { aceptaExoneracion: boolean }) => llamar<Cliente>('POST', '/clientes', c),
  reserva: (codigo: string) => llamar<Reserva>('GET', `/reservas/${codigo}`),
  cancelarReserva: (codigo: string) => llamar<Reserva>('PATCH', `/reservas/${codigo}`, { estado: 'CANCELADA' }),
  reservar: (documentoCliente: string, codigoSesion: string, cupos: number) => llamar<Reserva>('POST', '/reservas', { documentoCliente, codigoSesion, cupos }),
  pagar: (codigo: string, metodo: string, clave: string) => llamar<{ reserva: Reserva; pago: Pago; factura: Factura | null }>('POST', `/reservas/${codigo}/pagos`, { metodo }, { 'Idempotency-Key': clave }),
  parrilla: (c: string) => llamar<Parrilla>('GET', `/sesiones/${c}/parrilla`),
  asignar: (c: string, codigoReserva: string, documentoPiloto: string, numeroKart: number) => llamar<Asignacion>('POST', `/sesiones/${c}/asignaciones`, { codigoReserva, documentoPiloto, numeroKart }),
  retirarKart: (c: string, numeroKart: number) => llamar<null>('DELETE', `/sesiones/${c}/asignaciones/${numeroKart}`),
  cancelarTanda: (c: string) => llamar<Sesion>('PATCH', `/sesiones/${c}`, { estado: 'CANCELADA' }),
  iniciar: (c: string) => llamar<Sesion>('POST', `/sesiones/${c}/iniciar`),
  finalizar: (c: string) => llamar<{ sesion: Sesion; usos: unknown[]; ordenesGeneradas: Orden[] }>('POST', `/sesiones/${c}/finalizar`),
  clasificacion: (c: string) => llamar<Clasificacion>('GET', `/sesiones/${c}/clasificacion`),
  paso: (codigoTransponder: string) => llamar<{ vueltaRegistrada: boolean; vuelta: { numeroVuelta: number; tiempoMs: number } | null }>('POST', '/telemetria/pasos', { codigoTransponder }),
  karts: () => llamar<Kart[]>('GET', '/flota/karts'),
  umbral: () => llamar<{ horas: number }>('GET', '/flota/umbral'),
  actualizarUmbral: (horas: number) => llamar<{ horas: number }>('PUT', '/flota/umbral', { horas }),
  ordenes: () => llamar<Orden[]>('GET', '/ordenes'),
  mecanicos: () => llamar<Mecanico[]>('GET', '/mecanicos'),
  asignarOrden: (n: string, codigoMecanico: string) => llamar<Orden>('POST', `/ordenes/${n}/asignacion`, { codigoMecanico }),
  cerrarOrden: (n: string, codigoMecanico: string, observaciones: string) => llamar<Orden>('POST', `/ordenes/${n}/cierre`, { codigoMecanico, observaciones }),
  tarifas: () => llamar<Tarifa[]>('GET', '/tarifas'),
  registrarTarifa: (tipoKart: string, valorPorCupo: number) => llamar<Tarifa>('POST', '/tarifas', { tipoKart, valorPorCupo }),
  reporte: (desde: string, hasta: string) => llamar<Reporte>('GET', `/reportes/ocupacion?desde=${desde}&hasta=${hasta}`),
}

export const pesos = (v: number) => new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(v)
export const hora = (iso: string) => new Date(iso).toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' })
export const fecha = (iso: string) => new Date(iso).toLocaleDateString('es-CO', { weekday: 'short', day: 'numeric', month: 'short' })
export const cronometro = (ms: number | null | undefined) => {
  if (ms == null) return '—'
  const m = Math.floor(ms / 60000), s = Math.floor((ms % 60000) / 1000), c = ms % 1000
  return `${m}:${String(s).padStart(2, '0')}.${String(c).padStart(3, '0')}`
}
