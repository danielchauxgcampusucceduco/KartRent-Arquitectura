# Frontend de KartRent

React 19 + TypeScript + Vite. Una pantalla por historia de usuario:

| Ruta | HU | Acceso |
| --- | --- | --- |
| `/reservar` | HU-01 Piloto | Público (sin sesión) |
| `/boxes` | HU-02 Jefe de boxes | `boxes` o `admin` |
| `/en-vivo` | HU-03 Tiempos en vivo | Público; el simulador del decodificador pide `cronometraje` |
| `/taller` | HU-04 Taller y flota | Mecánico (su código, p. ej. `M-02`), `boxes` o `admin` |
| `/admin` | HU-05 Administración | `admin` |

```bash
npm install
npm run dev     # http://localhost:5173, con proxy a http://localhost:8080
npm test        # Vitest + Testing Library
npm run lint    # Oxlint
npm run build   # tsc + vite build
```

## Cómo está organizado

- `src/api.ts`: cliente HTTP. Clasifica los errores (`400`, `401`, `403`, `404`, `409`, `500` y falta de conexión) en `ErrorApi`, lee Problem Details sin romperse si el servidor responde HTML y envía `Authorization` cuando hay sesión.
- `src/sesion.ts`: credenciales HTTP Basic de los roles internos, **solo en memoria** (al recargar se piden de nuevo). Un `401` con sesión la cierra.
- `src/useAsync.ts` y `src/componentes.tsx`: `useCarga` + el componente `Carga` (cargando, error con «Reintentar» y vacío), `Aviso` y el acceso por rol (`Protegida`).
- `src/misReservas.ts`: el piloto no inicia sesión; el navegador recuerda los códigos de sus reservas.
- `src/useEnVivo.ts`: suscripción STOMP por WebSocket a `/topic/sesiones/{codigo}/clasificacion`.
- Pruebas (`*.test.ts(x)`): pago aprobado y rechazado con reintento, cancelación, errores por código HTTP y acceso por rol, con un servidor `fetch` falso.
