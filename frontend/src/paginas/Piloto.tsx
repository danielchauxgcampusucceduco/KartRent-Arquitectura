import { useEffect, useMemo, useState } from 'react'
import { api, comoErrorApi, fecha, hora, pesos, type Cliente, type Factura, type Pago, type Reserva, type Sesion } from '../api'
import { Aviso, Carga, Estado, Titulo, Vacio } from '../componentes'
import { codigosGuardados, guardarCodigo } from '../misReservas'
import { useAviso, useCarga } from '../useAsync'

const nuevaClave = () =>
  typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`

const mmss = (segundos: number) => `${Math.floor(segundos / 60)}:${String(segundos % 60).padStart(2, '0')}`

/** HU-01 — El piloto se registra, elige tanda, reserva cupos (retenidos 10 min) y paga. No inicia sesión. */
export default function Piloto() {
  const sesiones = useCarga(api.sesiones)
  const { aviso, setAviso, ejecutar } = useAviso()
  const [documento, setDocumento] = useState('')
  const [activo, setActivo] = useState('')
  const [cliente, setCliente] = useState<Cliente | null>(null)
  const [registro, setRegistro] = useState(false)
  const [tanda, setTanda] = useState<Sesion | null>(null)
  const [cupos, setCupos] = useState(1)
  const [reserva, setReserva] = useState<Reserva | null>(null)
  const [recibidoEn, setRecibidoEn] = useState(0)
  const [vencida, setVencida] = useState(false)
  const [pago, setPago] = useState<Pago | null>(null)
  const [factura, setFactura] = useState<Factura | null>(null)
  const [pagando, setPagando] = useState(false)
  const [historial, setHistorial] = useState<Reserva[]>([])

  // Solo las tandas que aún aceptan reservas: PROGRAMADA y EN_PARRILLA (RN-06).
  const abiertas = useMemo(
    () => (sesiones.datos ?? []).filter(s => s.estado === 'PROGRAMADA' || s.estado === 'EN_PARRILLA'),
    [sesiones.datos],
  )
  const porDia = useMemo(() => {
    const m = new Map<string, Sesion[]>()
    abiertas.forEach(s => { const d = fecha(s.horaInicio); m.set(d, [...(m.get(d) ?? []), s]) })
    return [...m.entries()]
  }, [abiertas])

  // El piloto no consulta GET /clientes/{doc}/reservas (protegido): el navegador guarda los códigos de sus reservas.
  const cargarHistorial = async (doc: string) => {
    const r = await Promise.allSettled(codigosGuardados().map(c => api.reserva(c)))
    setHistorial(r.flatMap(x => (x.status === 'fulfilled' && x.value.documentoCliente === doc ? [x.value] : []))
      .sort((a, b) => b.fechaCreacion.localeCompare(a.fechaCreacion)))
  }
  const continuar = () => {
    const doc = documento.trim()
    if (!doc) return setAviso({ tipo: 'error', titulo: 'Datos o regla de negocio', texto: 'Escribe tu documento para continuar.' })
    setAviso(null)
    setActivo(doc)
    setCliente(null)
    setRegistro(false)
    void cargarHistorial(doc)
  }

  const reservar = async () => {
    if (!activo || !tanda) return
    try {
      const r = await api.reservar(activo, tanda.codigo, cupos)
      guardarCodigo(r.codigo)
      setReserva(r); setRecibidoEn(Date.now()); setVencida(false); setPago(null); setFactura(null)
      setAviso({ tipo: 'ok', texto: `Cupos retenidos hasta las ${hora(r.venceRetencion)}. Paga antes de ese momento.` })
      sesiones.recargar(); void cargarHistorial(activo)
    } catch (e) {
      const err = comoErrorApi(e)
      setAviso({ tipo: 'error', titulo: err.titulo, texto: err.message })
      if (err.estado === 404 && err.message.includes('no registrado')) setRegistro(true)
      if (err.estado === 409) sesiones.recargar()
    }
  }

  const pagar = async (metodo: string) => {
    if (!reserva || pagando) return
    setPagando(true)
    // Cada intento lleva su propia Idempotency-Key: repetir la clave de un rechazo devolvería el mismo rechazo.
    const r = await ejecutar(() => api.pagar(reserva.codigo, metodo, nuevaClave()))
    setPagando(false)
    if (!r) { void refrescar(reserva.codigo); return }
    setReserva(r.reserva); setPago(r.pago)
    if (!r.pago.aprobado) {
      setAviso({ tipo: 'error', titulo: 'Pago rechazado', texto: 'La pasarela rechazó el pago. Tus cupos siguen retenidos: intenta con otro método antes de que venza la retención.' })
    } else if (r.pago.reembolsado) {
      setAviso({ tipo: 'error', titulo: 'Pago reembolsado', texto: 'El cobro se devolvió porque la reserva ya no estaba disponible. Elige una tanda y reserva de nuevo.' })
    } else {
      setFactura(r.factura)
      setAviso({ tipo: 'ok', texto: 'Pago aprobado. Te enviamos la factura electrónica.' })
    }
    sesiones.recargar(); void cargarHistorial(r.reserva.documentoCliente)
  }

  const refrescar = async (codigo: string) => {
    try {
      const r = await api.reserva(codigo)
      setReserva(actual => (actual?.codigo === codigo ? r : actual))
      sesiones.recargar(); if (activo) void cargarHistorial(activo)
    } catch { /* el aviso del error original ya está en pantalla */ }
  }

  const cancelar = async (codigo: string) => {
    const r = await ejecutar(() => api.cancelarReserva(codigo),
      c => (c.estado === 'REEMBOLSADA' ? 'Reserva cancelada y pago reembolsado.' : 'Reserva cancelada: los cupos quedaron libres.'))
    if (!r) return
    setReserva(actual => (actual?.codigo === codigo ? r : actual))
    sesiones.recargar(); if (activo) void cargarHistorial(activo)
  }

  const cuposValidos = !!tanda && Number.isInteger(cupos) && cupos >= 1 && cupos <= tanda.cuposDisponibles
  const reservaActiva = !!reserva && (reserva.estado === 'PENDIENTE_PAGO' || reserva.estado === 'CONFIRMADA')

  return (
    <section>
      <Titulo hu="HU-01" rol="Piloto" titulo="Reserva tu tanda" />
      <Aviso aviso={aviso} cerrar={() => setAviso(null)} />

      <div className="rejilla dos">
        <div className="panel">
          <h2><span className="paso">1</span> ¿Quién conduce?</h2>
          <div className="fila">
            <input value={documento} onChange={e => setDocumento(e.target.value)} placeholder="Documento" aria-label="Documento" />
            <button onClick={continuar}>Continuar</button>
          </div>
          {activo && !cliente && !registro && (
            <div className="ficha">
              <strong>Documento {activo}</strong>
              <span>¿Primera vez? <button className="enlace" onClick={() => setRegistro(true)}>Regístrate</button></span>
            </div>
          )}
          {cliente && (
            <div className="ficha">
              <strong>{cliente.nombre}</strong>
              <span>{cliente.edad} años · {cliente.exoneracionFirmada ? 'Exoneración firmada ✓' : 'Sin exoneración'}</span>
            </div>
          )}
          {registro && (
            <Registro documento={activo || documento.trim()} ejecutar={ejecutar}
              alRegistrar={c => { setCliente(c); setActivo(c.documento); setRegistro(false) }} />
          )}

          {historial.length > 0 && (
            <>
              <h3>Mis reservas</h3>
              <ul className="lista">
                {historial.slice(0, 5).map(r => (
                  <li key={r.codigo}>
                    <span className="mono">{r.codigoSesion}</span> {r.cupos} cupo(s) · {pesos(r.valorTotal)} <Estado valor={r.estado} />
                    {(r.estado === 'PENDIENTE_PAGO' || r.estado === 'CONFIRMADA') && (
                      <button className="peligro chico" onClick={() => cancelar(r.codigo)}>Cancelar</button>
                    )}
                  </li>
                ))}
              </ul>
            </>
          )}
        </div>

        <div className="panel">
          <h2><span className="paso">2</span> Elige la tanda</h2>
          <Carga carga={sesiones}>
            {() => (
              porDia.length === 0
                ? <Vacio>No hay tandas abiertas. El administrador puede programarlas en HU-05.</Vacio>
                : porDia.map(([dia, lista]) => (
                  <div key={dia} className="dia">
                    <h3>{dia}</h3>
                    <div className="tandas">
                      {lista.map(s => (
                        <button key={s.codigo} disabled={s.cuposDisponibles === 0}
                          className={`tanda ${tanda?.codigo === s.codigo ? 'elegida' : ''} ${s.tipoKart === 'INFANTIL' ? 'infantil' : ''}`}
                          onClick={() => { setTanda(s); setCupos(1); setReserva(null); setPago(null); setFactura(null) }}>
                          <b>{hora(s.horaInicio)}</b>
                          <small>{s.tipoKart === 'INFANTIL' ? 'Infantil 8–15' : 'Adulto 16+'}{s.estado === 'EN_PARRILLA' ? ' · en parrilla' : ''}</small>
                          <i style={{ ['--lleno' as string]: `${(s.cuposVendidos / s.cuposMaximos) * 100}%` }}>{s.cuposDisponibles} libres</i>
                        </button>
                      ))}
                    </div>
                  </div>
                ))
            )}
          </Carga>
        </div>
      </div>

      <div className="panel">
        <h2><span className="paso">3</span> Reserva y paga</h2>
        {!tanda || !activo ? <Vacio>Escribe tu documento, pulsa Continuar y elige una tanda.</Vacio> : (
          <div className="checkout">
            <div>
              <p className="grande">{tanda.codigo} · {fecha(tanda.horaInicio)} {hora(tanda.horaInicio)} · {tanda.duracionMinutos} min</p>
              <label>Cupos
                <input type="number" min={1} max={tanda.cuposDisponibles} value={cupos} onChange={e => setCupos(Number(e.target.value))} />
              </label>
              <button onClick={reservar} disabled={!cuposValidos || reservaActiva}>Reservar cupos</button>
            </div>
            {reserva && (
              <div className="recibo">
                <div className="fila-espacio"><span>Reserva</span><Estado valor={reserva.estado} /></div>
                <div className="fila-espacio"><span>Total</span><strong>{pesos(reserva.valorTotal)}</strong></div>
                {reserva.estado === 'PENDIENTE_PAGO' && (
                  <>
                    {vencida
                      ? <p className="nota" role="alert">La retención de cupos venció (RN-01). Reserva de nuevo.</p>
                      : <Retencion key={reserva.codigo} reserva={reserva} recibidoEn={recibidoEn}
                          alVencer={() => { setVencida(true); void refrescar(reserva.codigo) }} />}
                    <div className="fila">
                      <button onClick={() => pagar('TARJETA')} disabled={pagando || vencida}>Pagar con tarjeta</button>
                      <button className="secundario" onClick={() => pagar('PSE')} disabled={pagando || vencida}>PSE</button>
                      <button className="peligro" onClick={() => pagar('SIMULAR_RECHAZO')} disabled={pagando || vencida} title="Simula un pago rechazado">Simular rechazo</button>
                      <button className="secundario" onClick={() => cancelar(reserva.codigo)} disabled={pagando}>Cancelar reserva</button>
                    </div>
                  </>
                )}
                {reserva.estado === 'CONFIRMADA' && (
                  <div className="fila"><button className="peligro" onClick={() => cancelar(reserva.codigo)}>Cancelar y reembolsar</button></div>
                )}
                {pago?.reembolsado && <p className="nota">Pago {pago.referenciaPasarela} reembolsado.</p>}
                {reserva.estado === 'REEMBOLSADA' && <p className="nota">El pago de esta reserva fue reembolsado.</p>}
                {factura && (
                  <div className="factura">
                    <div className="fila-espacio"><span>Factura</span><b className="mono">{factura.numero}</b></div>
                    <div className="fila-espacio"><span>Subtotal</span>{pesos(factura.subtotal)}</div>
                    <div className="fila-espacio"><span>IVA 19 %</span>{pesos(factura.iva)}</div>
                    <small className="mono cufe" title="CUFE">{factura.cufe}</small>
                  </div>
                )}
              </div>
            )}
          </div>
        )}
      </div>
    </section>
  )
}

/** Cuenta regresiva de la retención (RN-01). Se calcula con la duración que informa el servidor, sin depender de su zona horaria. */
function Retencion({ reserva, recibidoEn, alVencer }: { reserva: Reserva; recibidoEn: number; alVencer: () => void }) {
  const total = Date.parse(reserva.venceRetencion) - Date.parse(reserva.fechaCreacion)
  const restante = () => Math.max(0, Math.ceil((total - (Date.now() - recibidoEn)) / 1000))
  const [segundos, setSegundos] = useState(restante)
  useEffect(() => {
    const id = setInterval(() => {
      const r = restante()
      setSegundos(r)
      if (r === 0) { clearInterval(id); alVencer() }
    }, 1000)
    return () => clearInterval(id)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  return <p className="nota" aria-label="Tiempo de retención">Retención de cupos (RN-01): <b className="mono">{mmss(segundos)}</b> para pagar</p>
}

function Registro({ documento, alRegistrar, ejecutar }: {
  documento: string; alRegistrar: (c: Cliente) => void
  ejecutar: ReturnType<typeof useAviso>['ejecutar']
}) {
  const [f, setF] = useState({ nombre: '', correo: '', telefono: '', fechaNacimiento: '', aceptaExoneracion: false })
  const enviar = async () => {
    const c = await ejecutar(() => api.registrarCliente({ documento, ...f }), c => `Bienvenido, ${c.nombre}`)
    if (c) alRegistrar(c)
  }
  return (
    <div className="registro">
      <p className="nota">Registro del documento {documento || '(escríbelo arriba)'}:</p>
      <input placeholder="Nombre completo" value={f.nombre} onChange={e => setF({ ...f, nombre: e.target.value })} />
      <input placeholder="Correo" type="email" required value={f.correo} onChange={e => setF({ ...f, correo: e.target.value })} />
      <input placeholder="Teléfono" value={f.telefono} onChange={e => setF({ ...f, telefono: e.target.value })} />
      <label>Fecha de nacimiento <input type="date" value={f.fechaNacimiento} onChange={e => setF({ ...f, fechaNacimiento: e.target.value })} /></label>
      <label className="check">
        <input type="checkbox" checked={f.aceptaExoneracion} onChange={e => setF({ ...f, aceptaExoneracion: e.target.checked })} />
        Acepto la exoneración de responsabilidad y las normas de seguridad de pista (RN-03)
      </label>
      <button onClick={enviar} disabled={!documento}>Registrarme</button>
    </div>
  )
}
