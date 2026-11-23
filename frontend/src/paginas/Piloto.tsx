import { useMemo, useState } from 'react'
import { api, fecha, hora, pesos, type Cliente, type Factura, type Reserva, type Sesion } from '../api'
import { Aviso, Estado, Titulo, Vacio } from '../componentes'
import { useAviso, useCarga } from '../useAsync'

/** HU-01 — El piloto se registra, elige tanda, reserva cupos (retenidos 10 min) y paga. */
export default function Piloto() {
  const { datos: sesiones, recargar } = useCarga(api.sesiones)
  const { aviso, setAviso, ejecutar } = useAviso()
  const [documento, setDocumento] = useState('1085000001')
  const [cliente, setCliente] = useState<Cliente | null>(null)
  const [registro, setRegistro] = useState(false)
  const [tanda, setTanda] = useState<Sesion | null>(null)
  const [cupos, setCupos] = useState(1)
  const [reserva, setReserva] = useState<Reserva | null>(null)
  const [factura, setFactura] = useState<Factura | null>(null)
  const [historial, setHistorial] = useState<Reserva[]>([])

  const abiertas = useMemo(() => (sesiones ?? []).filter(s => s.estado === 'PROGRAMADA'), [sesiones])
  const porDia = useMemo(() => {
    const m = new Map<string, Sesion[]>()
    abiertas.forEach(s => { const d = fecha(s.horaInicio); m.set(d, [...(m.get(d) ?? []), s]) })
    return [...m.entries()]
  }, [abiertas])

  const identificar = async () => {
    const c = await ejecutar(() => api.cliente(documento))
    if (c) { setCliente(c); setRegistro(false); setAviso(null); setHistorial(await api.reservasDe(c.documento)) }
    else setRegistro(true)
  }

  const reservar = async () => {
    if (!cliente || !tanda) return
    const r = await ejecutar(() => api.reservar(cliente.documento, tanda.codigo, cupos), r => `Cupos retenidos hasta las ${hora(r.venceRetencion)}. Paga antes de ese momento.`)
    if (r) { setReserva(r); setFactura(null); recargar() }
  }

  const pagar = async (metodo: string) => {
    if (!reserva) return
    const r = await ejecutar(() => api.pagar(reserva.codigo, metodo, `web-${reserva.codigo}`), () => 'Pago aprobado. Te enviamos la factura electrónica.')
    if (r) { setReserva(r.reserva); setFactura(r.factura); setHistorial(await api.reservasDe(r.reserva.documentoCliente)); recargar() }
  }

  return (
    <section>
      <Titulo hu="HU-01" rol="Piloto" titulo="Reserva tu tanda" />
      <Aviso aviso={aviso} cerrar={() => setAviso(null)} />

      <div className="rejilla dos">
        <div className="panel">
          <h2><span className="paso">1</span> ¿Quién conduce?</h2>
          <div className="fila">
            <input value={documento} onChange={e => setDocumento(e.target.value)} placeholder="Documento" aria-label="Documento" />
            <button onClick={identificar}>Continuar</button>
          </div>
          {cliente && (
            <div className="ficha">
              <strong>{cliente.nombre}</strong>
              <span>{cliente.edad} años · {cliente.exoneracionFirmada ? 'Exoneración firmada ✓' : 'Sin exoneración'}</span>
            </div>
          )}
          {registro && <Registro documento={documento} alRegistrar={c => { setCliente(c); setRegistro(false) }} ejecutar={ejecutar} />}

          {historial.length > 0 && (
            <>
              <h3>Mis reservas</h3>
              <ul className="lista">
                {historial.slice(0, 5).map(r => (
                  <li key={r.codigo}><span className="mono">{r.codigoSesion}</span> {r.cupos} cupo(s) · {pesos(r.valorTotal)} <Estado valor={r.estado} /></li>
                ))}
              </ul>
            </>
          )}
        </div>

        <div className="panel">
          <h2><span className="paso">2</span> Elige la tanda</h2>
          {porDia.length === 0 && <Vacio>No hay tandas abiertas. El administrador puede programarlas en HU-05.</Vacio>}
          {porDia.map(([dia, lista]) => (
            <div key={dia} className="dia">
              <h3>{dia}</h3>
              <div className="tandas">
                {lista.map(s => (
                  <button key={s.codigo} disabled={s.cuposDisponibles === 0}
                    className={`tanda ${tanda?.codigo === s.codigo ? 'elegida' : ''} ${s.tipoKart === 'INFANTIL' ? 'infantil' : ''}`}
                    onClick={() => { setTanda(s); setReserva(null); setFactura(null) }}>
                    <b>{hora(s.horaInicio)}</b>
                    <small>{s.tipoKart === 'INFANTIL' ? 'Infantil 8–15' : 'Adulto 16+'}</small>
                    <i style={{ ['--lleno' as string]: `${(s.cuposVendidos / s.cuposMaximos) * 100}%` }}>{s.cuposDisponibles} libres</i>
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="panel">
        <h2><span className="paso">3</span> Reserva y paga</h2>
        {!tanda || !cliente ? <Vacio>Identifícate y elige una tanda.</Vacio> : (
          <div className="checkout">
            <div>
              <p className="grande">{tanda.codigo} · {fecha(tanda.horaInicio)} {hora(tanda.horaInicio)} · {tanda.duracionMinutos} min</p>
              <label>Cupos
                <input type="number" min={1} max={tanda.cuposDisponibles} value={cupos} onChange={e => setCupos(Number(e.target.value))} />
              </label>
              <button onClick={reservar} disabled={!!reserva && reserva.estado !== 'EXPIRADA'}>Reservar cupos</button>
            </div>
            {reserva && (
              <div className="recibo">
                <div className="fila-espacio"><span>Reserva</span><Estado valor={reserva.estado} /></div>
                <div className="fila-espacio"><span>Total</span><strong>{pesos(reserva.valorTotal)}</strong></div>
                {reserva.estado === 'PENDIENTE_PAGO' && (
                  <>
                    <p className="nota">Retención de cupos (RN-01) hasta las {hora(reserva.venceRetencion)}</p>
                    <div className="fila">
                      <button onClick={() => pagar('TARJETA')}>Pagar con tarjeta</button>
                      <button className="secundario" onClick={() => pagar('PSE')}>PSE</button>
                      <button className="peligro" onClick={() => pagar('SIMULAR_RECHAZO')} title="Simula un pago rechazado">Simular rechazo</button>
                    </div>
                  </>
                )}
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
      <p className="nota">No encontramos ese documento. Regístrate:</p>
      <input placeholder="Nombre completo" value={f.nombre} onChange={e => setF({ ...f, nombre: e.target.value })} />
      <input placeholder="Correo" type="email" required value={f.correo} onChange={e => setF({ ...f, correo: e.target.value })} />
      <input placeholder="Teléfono" value={f.telefono} onChange={e => setF({ ...f, telefono: e.target.value })} />
      <label>Fecha de nacimiento <input type="date" value={f.fechaNacimiento} onChange={e => setF({ ...f, fechaNacimiento: e.target.value })} /></label>
      <label className="check">
        <input type="checkbox" checked={f.aceptaExoneracion} onChange={e => setF({ ...f, aceptaExoneracion: e.target.checked })} />
        Acepto la exoneración de responsabilidad y las normas de seguridad de pista (RN-03)
      </label>
      <button onClick={enviar}>Registrarme</button>
    </div>
  )
}
