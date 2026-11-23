import { useState } from 'react'
import { api, fecha, type Orden } from '../api'
import { Aviso, Estado, Titulo, Vacio } from '../componentes'
import { useAviso, useCarga } from '../useAsync'

/** HU-04 — Mantenimiento preventivo: estado de la flota, órdenes y cierre por el mecánico asignado. */
export default function Taller() {
  const karts = useCarga(api.karts)
  const ordenes = useCarga(api.ordenes)
  const mecanicos = useCarga(api.mecanicos)
  const umbral = useCarga(api.umbral)
  const { aviso, setAviso, ejecutar } = useAviso()
  const [mecanico, setMecanico] = useState<Record<string, string>>({})
  const [obs, setObs] = useState<Record<string, string>>({})

  const recargar = () => { karts.recargar(); ordenes.recargar() }
  const asignar = async (o: Orden) => {
    if (await ejecutar(() => api.asignarOrden(o.numero, mecanico[o.numero] ?? ''), r => `${r.numero} asignada a ${r.codigoMecanico}`)) recargar()
  }
  const cerrar = async (o: Orden) => {
    if (await ejecutar(() => api.cerrarOrden(o.numero, mecanico[o.numero] ?? o.codigoMecanico ?? '', obs[o.numero] ?? ''), r => `${r.numero} cerrada. Kart ${r.numeroKart} habilitado.`)) recargar()
  }
  const limite = umbral.datos?.horas ?? 20

  return (
    <section>
      <Titulo hu="HU-04" rol="Mecánico jefe" titulo="Taller y flota">
        <span className="pildora">Umbral preventivo: <b>{limite} h</b></span>
      </Titulo>
      <Aviso aviso={aviso} cerrar={() => setAviso(null)} />

      <div className="flota">
        {karts.datos?.map(k => (
          <div key={k.numero} className={`kart-tarjeta k-${k.estado.toLowerCase()}`}>
            <div className="fila-espacio"><span className="numero-kart">{k.numero}</span><Estado valor={k.estado} /></div>
            <small>{k.tipo} · {k.codigoTransponder}</small>
            <div className="medidor" title={`${k.horasDesdeServicio} h desde el último servicio`}>
              <i style={{ width: `${Math.min(100, (k.horasDesdeServicio / limite) * 100)}%` }} className={k.requiereMantenimiento ? 'excedido' : ''} />
            </div>
            <small className="mono">{k.horasDesdeServicio.toFixed(1)} / {limite} h · {k.horasMotor.toFixed(0)} h motor</small>
          </div>
        ))}
      </div>

      <div className="panel">
        <h2>Órdenes de mantenimiento</h2>
        {ordenes.datos?.length === 0 && <Vacio>Sin órdenes.</Vacio>}
        <table className="tabla">
          <thead><tr><th>Orden</th><th>Kart</th><th>Apertura</th><th>Estado</th><th>Mecánico</th><th>Acción</th></tr></thead>
          <tbody>
            {ordenes.datos?.map(o => (
              <tr key={o.numero}>
                <td className="mono">{o.numero}</td>
                <td><span className="numero-kart chico">{o.numeroKart}</span></td>
                <td>{fecha(o.fechaApertura)}</td>
                <td><Estado valor={o.estado} /></td>
                <td>
                  {o.estado === 'CERRADA' ? (o.codigoMecanico ?? '—') : (
                    <select value={mecanico[o.numero] ?? o.codigoMecanico ?? ''} onChange={e => setMecanico({ ...mecanico, [o.numero]: e.target.value })}>
                      <option value="">Elegir…</option>
                      {mecanicos.datos?.map(m => <option key={m.codigoEmpleado} value={m.codigoEmpleado}>{m.codigoEmpleado} · {m.nombre}</option>)}
                    </select>
                  )}
                </td>
                <td>
                  {o.estado === 'ABIERTA' && <button onClick={() => asignar(o)}>Asignar</button>}
                  {o.estado === 'EN_PROCESO' && (
                    <div className="fila">
                      <input placeholder="Trabajo realizado" value={obs[o.numero] ?? ''} onChange={e => setObs({ ...obs, [o.numero]: e.target.value })} />
                      <button onClick={() => cerrar(o)}>Cerrar</button>
                    </div>
                  )}
                  {o.estado === 'CERRADA' && <small>{o.observaciones}</small>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <p className="nota">RN-10: solo el mecánico asignado puede cerrar la orden. RN-11: un kart no tiene dos órdenes abiertas.</p>
      </div>
    </section>
  )
}
