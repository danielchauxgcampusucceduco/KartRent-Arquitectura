import { useState } from 'react'
import { api, hora, fecha, pesos } from '../api'
import { Aviso, Titulo } from '../componentes'
import { useAviso, useCarga } from '../useAsync'

const hoyIso = () => new Date().toISOString().slice(0, 10)
const desplazar = (dias: number) => new Date(Date.now() + dias * 864e5).toISOString().slice(0, 10)

/** HU-05 — Tarifas, programación de tandas, umbral de mantenimiento y reporte de ocupación. */
export default function Admin() {
  const { aviso, setAviso, ejecutar } = useAviso()
  const tarifas = useCarga(api.tarifas)
  const [rango, setRango] = useState({ desde: desplazar(-7), hasta: desplazar(7) })
  const reporte = useCarga(() => api.reporte(rango.desde, rango.hasta), [rango.desde, rango.hasta])
  const [tarifa, setTarifa] = useState({ tipoKart: 'ADULTO', valor: 45000 })
  const [tanda, setTanda] = useState({ codigo: '', inicio: `${hoyIso()}T20:30`, duracionMinutos: 10, cupos: 8, tipoKart: 'ADULTO' })
  const [umbral, setUmbral] = useState(20)

  const r = reporte.datos
  return (
    <section>
      <Titulo hu="HU-05" rol="Administrador" titulo="Operación del kartódromo" />
      <Aviso aviso={aviso} cerrar={() => setAviso(null)} />

      <div className="indicadores">
        <div><small>Ocupación</small><b>{r ? `${r.ocupacionPorcentaje.toFixed(1)} %` : '—'}</b><div className="medidor"><i style={{ width: `${r?.ocupacionPorcentaje ?? 0}%` }} /></div></div>
        <div><small>Cupos vendidos</small><b>{r?.cuposVendidos ?? '—'} <span className="gris">/ {r?.cuposOfrecidos ?? '—'}</span></b></div>
        <div><small>Ingresos confirmados</small><b>{r ? pesos(r.ingresos) : '—'}</b></div>
        <div><small>Tandas</small><b>{r?.tandas.length ?? '—'}</b></div>
      </div>

      <div className="rejilla tres">
        <div className="panel">
          <h2>Tarifas</h2>
          <ul className="lista">{tarifas.datos?.map((t, i) => <li key={i}><b>{t.tipoKart}</b> {pesos(t.valorPorCupo)} <small>desde {t.vigenteDesde}</small></li>)}</ul>
          <div className="fila">
            <select value={tarifa.tipoKart} onChange={e => setTarifa({ ...tarifa, tipoKart: e.target.value })}><option>ADULTO</option><option>INFANTIL</option></select>
            <input type="number" value={tarifa.valor} onChange={e => setTarifa({ ...tarifa, valor: Number(e.target.value) })} />
            <button onClick={async () => { if (await ejecutar(() => api.registrarTarifa(tarifa.tipoKart, tarifa.valor), () => 'Tarifa registrada')) tarifas.recargar() }}>Guardar</button>
          </div>
        </div>

        <div className="panel">
          <h2>Programar tanda</h2>
          <input placeholder="Código (p. ej. T-ESPECIAL)" value={tanda.codigo} onChange={e => setTanda({ ...tanda, codigo: e.target.value })} />
          <input type="datetime-local" value={tanda.inicio} onChange={e => setTanda({ ...tanda, inicio: e.target.value })} />
          <div className="fila">
            <label>Min <input type="number" value={tanda.duracionMinutos} onChange={e => setTanda({ ...tanda, duracionMinutos: Number(e.target.value) })} /></label>
            <label>Cupos <input type="number" value={tanda.cupos} onChange={e => setTanda({ ...tanda, cupos: Number(e.target.value) })} /></label>
            <select value={tanda.tipoKart} onChange={e => setTanda({ ...tanda, tipoKart: e.target.value })}><option>ADULTO</option><option>INFANTIL</option></select>
          </div>
          <button onClick={async () => { if (await ejecutar(() => api.programarTanda({ ...tanda, inicio: `${tanda.inicio}:00` }), s => `Tanda ${s.codigo} programada`)) reporte.recargar() }}>Programar</button>
        </div>

        <div className="panel">
          <h2>Umbral de mantenimiento</h2>
          <p className="nota">Horas de motor desde el último servicio que disparan la orden preventiva (RN-09).</p>
          <div className="fila">
            <input type="number" step="0.5" value={umbral} onChange={e => setUmbral(Number(e.target.value))} />
            <button onClick={() => ejecutar(() => api.actualizarUmbral(umbral), u => `Umbral actualizado a ${u.horas} h`)}>Aplicar</button>
          </div>
        </div>
      </div>

      <div className="panel">
        <div className="fila-espacio">
          <h2>Reporte de ocupación (RN-13)</h2>
          <div className="fila">
            <input type="date" value={rango.desde} onChange={e => setRango({ ...rango, desde: e.target.value })} />
            <input type="date" value={rango.hasta} onChange={e => setRango({ ...rango, hasta: e.target.value })} />
          </div>
        </div>
        <div className="barras">
          {r?.tandas.map(t => (
            <div key={t.codigoSesion} className="barra" title={`${t.codigoSesion}: ${t.cuposVendidos}/${t.cuposOfrecidos} · ${pesos(t.ingresos)}`}>
              <i style={{ height: `${Math.max(2, t.ocupacionPorcentaje)}%` }} className={t.tipoKart === 'INFANTIL' ? 'infantil' : ''} />
              <small>{fecha(t.inicio).split(' ')[0]} {hora(t.inicio)}</small>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
