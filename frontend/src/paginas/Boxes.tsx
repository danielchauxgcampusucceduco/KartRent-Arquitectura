import { useEffect, useState } from 'react'
import { api, comoErrorApi, fecha, hora, type Orden, type Parrilla } from '../api'
import { Aviso, Carga, Estado, Titulo, Vacio } from '../componentes'
import { useAviso, useCarga } from '../useAsync'

/** HU-02 — El jefe de boxes asigna karts disponibles a los pilotos confirmados, inicia y finaliza la tanda. */
export default function Boxes() {
  const cargaSesiones = useCarga(api.sesiones)
  const { datos: sesiones, recargar: recargarSesiones } = cargaSesiones
  const { aviso, setAviso, ejecutar } = useAviso()
  const [codigo, setCodigo] = useState<string>('')
  const [parrilla, setParrilla] = useState<Parrilla | null>(null)
  const [kartElegido, setKartElegido] = useState<Record<string, number>>({})
  const [ordenes, setOrdenes] = useState<Orden[]>([])

  const activas = (sesiones ?? []).filter(s => s.estado !== 'FINALIZADA')
  useEffect(() => {
    if (!codigo && activas.length) setCodigo((activas.find(s => s.cuposVendidos > 0) ?? activas[0]).codigo)
  }, [activas, codigo])

  const cargar = () => {
    if (!codigo) return
    api.parrilla(codigo).then(setParrilla).catch(e => { const x = comoErrorApi(e); setAviso({ tipo: 'error', titulo: x.titulo, texto: x.message }) })
  }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(cargar, [codigo])

  const asignar = async (codigoReserva: string, documento: string) => {
    const kart = kartElegido[codigoReserva]
    if (!kart) return setAviso({ tipo: 'error', titulo: 'Datos o regla de negocio', texto: 'Elige un kart' })
    const r = await ejecutar(() => api.asignar(codigo, codigoReserva, documento, kart), a => `Kart ${a.numeroKart} → parrilla ${a.numeroParrilla} (transponder ${a.codigoTransponder})`)
    if (r) cargar()
  }
  const retirar = async (numeroKart: number) => {
    if (await ejecutar(() => api.retirarKart(codigo, numeroKart), () => `Kart ${numeroKart} retirado de la parrilla.`)) { cargar(); recargarSesiones() }
  }
  const iniciar = async () => { if (await ejecutar(() => api.iniciar(codigo), () => '¡Bandera verde! La tanda está en pista.')) { cargar(); recargarSesiones() } }
  const finalizar = async () => {
    const r = await ejecutar(() => api.finalizar(codigo), c => `Bandera a cuadros. ${c.ordenesGeneradas.length} orden(es) preventiva(s) generada(s).`)
    if (r) { setOrdenes(r.ordenesGeneradas); cargar(); recargarSesiones() }
  }

  const s = parrilla?.sesion
  return (
    <section>
      <Titulo hu="HU-02" rol="Jefe de boxes" titulo="Parrilla de salida">
        <select value={codigo} onChange={e => { setCodigo(e.target.value); setOrdenes([]) }} aria-label="Tanda">
          {activas.map(t => <option key={t.codigo} value={t.codigo}>{t.codigo} · {fecha(t.horaInicio)} {hora(t.horaInicio)} · {t.cuposVendidos}/{t.cuposMaximos}</option>)}
        </select>
      </Titulo>
      <Aviso aviso={aviso} cerrar={() => setAviso(null)} />

      <Carga carga={cargaSesiones}>{() => !s ? <Vacio>Selecciona una tanda.</Vacio> : (
        <>
          <div className="tablero">
            <div><small>Estado</small><Estado valor={s.estado} /></div>
            <div><small>Tipo</small><b>{s.tipoKart}</b></div>
            <div><small>Cupos vendidos</small><b>{s.cuposVendidos} / {s.cuposMaximos}</b></div>
            <div><small>En parrilla</small><b>{s.asignaciones.length}</b></div>
            <div className="acciones">
              <button onClick={iniciar} disabled={s.estado !== 'EN_PARRILLA'}>Iniciar tanda</button>
              <button className="peligro" onClick={finalizar} disabled={s.estado !== 'EN_CURSO'}>Finalizar</button>
            </div>
          </div>

          <div className="rejilla dos">
            <div className="panel">
              <h2>Pilotos por asignar</h2>
              {parrilla.reservas.filter(r => r.asignados < r.cupos).length === 0 && <Vacio>Todos los pilotos confirmados tienen kart.</Vacio>}
              {parrilla.reservas.filter(r => r.asignados < r.cupos).map(r => (
                <div key={r.codigoReserva} className="asignar">
                  <div><strong>{r.nombreCliente}</strong><small>{r.documentoCliente} · {r.asignados}/{r.cupos} asignados</small></div>
                  <select value={kartElegido[r.codigoReserva] ?? ''} onChange={e => setKartElegido({ ...kartElegido, [r.codigoReserva]: Number(e.target.value) })}>
                    <option value="">Kart…</option>
                    {parrilla.kartsDisponibles.map(k => <option key={k.numero} value={k.numero}>#{k.numero} · {k.horasDesdeServicio.toFixed(1)} h</option>)}
                  </select>
                  <button onClick={() => asignar(r.codigoReserva, r.documentoCliente)}>Asignar</button>
                </div>
              ))}
              <p className="nota">Solo aparecen karts DISPONIBLES del tipo de la tanda (RN-04). La edad se valida contra el kart (RN-05).</p>
            </div>

            <div className="panel">
              <h2>Parrilla</h2>
              {s.asignaciones.length === 0 ? <Vacio>Aún no hay karts en parrilla.</Vacio> : (
                <ol className="parrilla">
                  {s.asignaciones.map(a => (
                    <li key={a.numeroParrilla}>
                      <span className="pos">P{a.numeroParrilla}</span>
                      <span className="numero-kart">{a.numeroKart}</span>
                      <span>{a.nombrePiloto}<small className="mono">{a.codigoTransponder}</small></span>
                      {s.estado === 'EN_PARRILLA' && <button className="secundario chico" onClick={() => retirar(a.numeroKart)} aria-label={`Retirar kart ${a.numeroKart}`}>Retirar</button>}
                    </li>
                  ))}
                </ol>
              )}
            </div>
          </div>

          {ordenes.length > 0 && (
            <div className="panel alerta">
              <h2>Karts enviados a taller (HU-04)</h2>
              {ordenes.map(o => <p key={o.numero}><b className="mono">{o.numero}</b> · Kart {o.numeroKart} superó el umbral con {o.horasMotorAlAbrir.toFixed(1)} h de motor</p>)}
            </div>
          )}
        </>
      )}</Carga>
    </section>
  )
}
