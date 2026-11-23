// Cliente HTTP de la API KartRent (/api/v1). Los errores llegan como Problem Details (RFC 7807).

export type Cliente = { documento: string; nombre: string; correo: string; telefono: string; fechaNacimiento: string; edad: number; exoneracionFirmada: boolean }
export type Reserva = { codigo: string; documentoCliente: string; codigoSesion: string; fechaCreacion: string; cupos: number; valorTotal: number; estado: string; venceRetencion: string }
export type Factura = { numero: string; cufe: string; codigoReserva: string; fechaEmision: string; subtotal: number; iva: number; total: number }
export type Pago = { referenciaPasarela: string; monto: number; metodo: string; aprobado: boolean; fechaPago: string }
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

export class ErrorApi extends Error {
  estado: number
  titulo: string
  constructor(estado: number, titulo: string, mensaje: string) {
    super(mensaje)
    this.estado = estado
    this.titulo = titulo
  }
}

async function llamar<T>(metodo: string, ruta: string, cuerpo?: unknown, cabeceras: Record<string, string> = {}): Promise<T> {
  const r = await fetch(`/api/v1${ruta}`, {
    method: metodo,
    headers: { 'Content-Type': 'application/json', ...cabeceras },
    body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
  })
  const texto = await r.text()
  const datos = texto ? JSON.parse(texto) : null
  if (!r.ok) throw new ErrorApi(r.status, datos?.title ?? 'Error', datos?.detail ?? `HTTP ${r.status}`)
  return datos as T
}

export const api = {
  sesiones: () => llamar<Sesion[]>('GET', '/sesiones'),
  sesion: (c: string) => llamar<Sesion>('GET', `/sesiones/${c}`),
  programarTanda: (t: { codigo: string; inicio: string; duracionMinutos: number; cupos: number; tipoKart: string }) => llamar<Sesion>('POST', '/sesiones', t),
  clientes: () => llamar<Cliente[]>('GET', '/clientes'),
  cliente: (doc: string) => llamar<Cliente>('GET', `/clientes/${doc}`),
  registrarCliente: (c: Omit<Cliente, 'edad' | 'exoneracionFirmada'> & { aceptaExoneracion: boolean }) => llamar<Cliente>('POST', '/clientes', c),
  reservasDe: (doc: string) => llamar<Reserva[]>('GET', `/clientes/${doc}/reservas`),
  reservar: (documentoCliente: string, codigoSesion: string, cupos: number) => llamar<Reserva>('POST', '/reservas', { documentoCliente, codigoSesion, cupos }),
  pagar: (codigo: string, metodo: string, clave: string) => llamar<{ reserva: Reserva; pago: Pago; factura: Factura | null }>('POST', `/reservas/${codigo}/pagos`, { metodo }, { 'Idempotency-Key': clave }),
  parrilla: (c: string) => llamar<Parrilla>('GET', `/sesiones/${c}/parrilla`),
  asignar: (c: string, codigoReserva: string, documentoPiloto: string, numeroKart: number) => llamar<Asignacion>('POST', `/sesiones/${c}/asignaciones`, { codigoReserva, documentoPiloto, numeroKart }),
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
