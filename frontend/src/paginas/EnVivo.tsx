import { useEffect, useState } from 'react'
import { api, comoErrorApi, cronometro, type Clasificacion, type Sesion } from '../api'
import { Aviso, FormularioAcceso, Titulo, Vacio } from '../componentes'
import { useUsuario } from '../sesion'
import { useAviso, useCarga } from '../useAsync'
import { useTopico } from '../useEnVivo'

type Empuje = Clasificacion & { ultimaVuelta?: { numeroKart: number; numeroVuelta: number; tiempoMs: number } }

/** HU-03 — Pantalla de tiempos en vivo por WebSocket + simulador del decodificador de transponders. */
export default function EnVivo() {
  const { datos: sesiones } = useCarga(api.sesiones)
  const { aviso, setAviso } = useAviso()
  const [codigo, setCodigo] = useState('')
  const [tabla, setTabla] = useState<Clasificacion | null>(null)
  const [destello, setDestello] = useState<number | null>(null)
  const [sesion, setSesion] = useState<Sesion | null>(null)
  const { mensaje, conectado } = useTopico<Empuje>(codigo ? `/topic/sesiones/${codigo}/clasificacion` : null)

  const candidatas = (sesiones ?? []).filter(s => s.estado === 'EN_CURSO' || s.estado === 'FINALIZADA' || s.estado === 'EN_PARRILLA')
  useEffect(() => {
    if (!codigo && candidatas.length) setCodigo((candidatas.find(s => s.estado === 'EN_CURSO') ?? candidatas[0]).codigo)
  }, [candidatas, codigo])
  useEffect(() => {
    if (!codigo) return
    const falla = (e: unknown) => { const x = comoErrorApi(e); setAviso({ tipo: 'error', titulo: x.titulo, texto: x.message }) }
    api.clasificacion(codigo).then(setTabla).catch(falla)
    api.sesion(codigo).then(setSesion).catch(falla)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [codigo])
  useEffect(() => {
    if (!mensaje) return
    setTabla(mensaje)
    if (mensaje.ultimaVuelta) {
      setDestello(mensaje.ultimaVuelta.numeroKart)
      const t = setTimeout(() => setDestello(null), 1200)
      return () => clearTimeout(t)
    }
  }, [mensaje])

  const mejorAbsoluta = Math.min(...(tabla?.clasificacion.map(l => l.mejorVueltaMs ?? Infinity) ?? [Infinity]))

  return (
    <section className="en-vivo">
      <Titulo hu="HU-03" rol="Pantalla de pista" titulo="Tiempos en vivo">
        <div className="fila">
          <span className={`senal ${conectado ? 'on' : ''}`}>{conectado ? 'EN VIVO' : 'SIN SEÑAL'}</span>
          <select value={codigo} onChange={e => setCodigo(e.target.value)} aria-label="Tanda">
            {candidatas.map(s => <option key={s.codigo} value={s.codigo}>{s.codigo} · {s.estado.replace('_', ' ')}</option>)}
          </select>
        </div>
      </Titulo>

      <Aviso aviso={aviso} cerrar={() => setAviso(null)} />

      {!tabla || tabla.clasificacion.length === 0 ? <Vacio>Inicia una tanda en Boxes para ver la clasificación.</Vacio> : (
        <table className="tiempos">
          <thead><tr><th>Pos</th><th>Kart</th><th>Piloto</th><th>Vueltas</th><th>Última</th><th>Mejor</th><th>Dif.</th></tr></thead>
          <tbody>
            {tabla.clasificacion.map(l => (
              <tr key={l.numeroKart} className={destello === l.numeroKart ? 'destello' : ''}>
                <td className="pos">{l.posicion}</td>
                <td><span className="numero-kart">{l.numeroKart}</span></td>
                <td>{l.piloto}</td>
                <td className="mono">{l.vueltas}</td>
                <td className="mono">{cronometro(l.ultimaVueltaMs)}</td>
                <td className={`mono ${l.mejorVueltaMs === mejorAbsoluta ? 'record' : ''}`}>{cronometro(l.mejorVueltaMs)}</td>
                <td className="mono gris">{l.mejorVueltaMs != null && l.posicion > 1 ? `+${((l.mejorVueltaMs - mejorAbsoluta) / 1000).toFixed(3)}` : ''}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {sesion?.estado === 'EN_CURSO' && <Simulador sesion={sesion} />}
    </section>
  )
}

/** Simula el decodificador de la línea de meta: cada clic es un paso del transponder (RN-07 descarta < 25 s). */
function Simulador({ sesion }: { sesion: Sesion }) {
  const [log, setLog] = useState<string[]>([])
  const [auto, setAuto] = useState(false)
  const usuario = useUsuario()
  const paso = async (tr: string, kart: number) => {
    let linea: string
    try {
      const r = await api.paso(tr)
      linea = r.vueltaRegistrada && r.vuelta ? `Kart ${kart}: vuelta ${r.vuelta.numeroVuelta} en ${cronometro(r.vuelta.tiempoMs)}` : `Kart ${kart}: paso por meta (sin vuelta válida)`
    } catch (e) {
      linea = `Kart ${kart}: ${comoErrorApi(e).message}`
      setAuto(false)
    }
    setLog(l => [linea, ...l].slice(0, 8))
  }
  useEffect(() => {
    if (!auto) return
    const timers = sesion.asignaciones.map(a => {
      const ritmo = 25500 + Math.random() * 9000
      return setInterval(() => paso(a.codigoTransponder, a.numeroKart), ritmo)
    })
    sesion.asignaciones.forEach(a => paso(a.codigoTransponder, a.numeroKart))
    return () => timers.forEach(clearInterval)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auto, sesion])
  if (!usuario) return <FormularioAcceso rol="Cronometraje" ayuda="El decodificador de meta envía los pasos con el usuario cronometraje (o admin)." />
  return (
    <div className="panel simulador">
      <h2>Decodificador de meta (simulador)</h2>
      <div className="fila envolver">
        {sesion.asignaciones.map(a => (
          <button key={a.codigoTransponder} className="secundario" onClick={() => paso(a.codigoTransponder, a.numeroKart)}>
            Paso #{a.numeroKart} <small className="mono">{a.codigoTransponder}</small>
          </button>
        ))}
        <button onClick={() => setAuto(!auto)}>{auto ? 'Detener carrera simulada' : 'Simular carrera'}</button>
      </div>
      <ul className="log mono">{log.map((l, i) => <li key={i}>{l}</li>)}</ul>
    </div>
  )
}
