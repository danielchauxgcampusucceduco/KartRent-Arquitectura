import { NavLink, Navigate, Route, Routes } from 'react-router-dom'
import Piloto from './paginas/Piloto'
import Boxes from './paginas/Boxes'
import EnVivo from './paginas/EnVivo'
import Taller from './paginas/Taller'
import Admin from './paginas/Admin'
import { BarraSesion, Protegida } from './componentes'
import { useServidorArrancando } from './arranque'

const secciones = [
  { ruta: '/reservar', hu: 'HU-01', nombre: 'Reservar', rol: 'Piloto' },
  { ruta: '/boxes', hu: 'HU-02', nombre: 'Boxes', rol: 'Jefe de boxes' },
  { ruta: '/en-vivo', hu: 'HU-03', nombre: 'En vivo', rol: 'Pantalla de pista' },
  { ruta: '/taller', hu: 'HU-04', nombre: 'Taller', rol: 'Mecánico jefe' },
  { ruta: '/admin', hu: 'HU-05', nombre: 'Administración', rol: 'Administrador' },
]

export default function App() {
  const arrancando = useServidorArrancando()
  return (
    <div className="marco">
      <header className="cabecera">
        <div className="marca">
          <span className="bandera" aria-hidden />
          <div>
            <strong>KartRent</strong>
            <small>Renta y gestión operativa de karting</small>
          </div>
        </div>
        <nav>
          {secciones.map(s => (
            <NavLink key={s.ruta} to={s.ruta} className={({ isActive }) => (isActive ? 'activa' : '')} title={s.rol}>
              <span className="hu">{s.hu}</span>
              {s.nombre}
            </NavLink>
          ))}
        </nav>
        <BarraSesion />
      </header>
      {arrancando && (
        <div className="arrancando" role="status">
          <span className="giro" aria-hidden /> Encendiendo el servidor… puede tardar hasta dos minutos la primera vez.
        </div>
      )}
      <main>
        <Routes>
          <Route path="/" element={<Navigate to="/reservar" replace />} />
          <Route path="/reservar" element={<Piloto />} />
          <Route path="/boxes" element={<Protegida rol="Jefe de boxes" ayuda="Usuario boxes (o admin)."><Boxes /></Protegida>} />
          <Route path="/en-vivo" element={<EnVivo />} />
          <Route path="/taller" element={<Protegida rol="Taller" ayuda="Mecánico: tu código de empleado (p. ej. M-01); también boxes o admin."><Taller /></Protegida>} />
          <Route path="/admin" element={<Protegida rol="Administrador" ayuda="Usuario admin."><Admin /></Protegida>} />
        </Routes>
      </main>
      <footer>
        Diseño de Software · Arquitectura limpia (puertos y adaptadores) · <a href="/swagger-ui.html">API</a>
      </footer>
    </div>
  )
}
