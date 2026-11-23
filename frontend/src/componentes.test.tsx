import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { Carga, Protegida } from './componentes'
import { api } from './api'
import { servidorFalso } from './pruebas-servidor'
import { useCarga } from './useAsync'

function Lista() {
  const carga = useCarga(api.tarifas)
  return <Carga carga={carga} vacio="Sin tarifas registradas.">{t => <p>{t.length} tarifa(s)</p>}</Carga>
}

describe('Carga: cargando, error con reintento y vacío', () => {
  it('muestra el error, permite reintentar y luego los datos', async () => {
    let intento = 0
    servidorFalso([{ metodo: 'GET', ruta: /tarifas/, responder: () => (++intento === 1
      ? { estado: 500, cuerpo: { detail: 'falló la base' } }
      : { estado: 200, cuerpo: [{ tipoKart: 'ADULTO', valorPorCupo: 45000, vigenteDesde: '2026-01-01' }] }) }])
    render(<Lista />)
    expect(screen.getByText('Cargando…')).toBeInTheDocument()
    expect(await screen.findByRole('alert')).toHaveTextContent('falló la base')
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByText('1 tarifa(s)')).toBeInTheDocument()
  })

  it('muestra el mensaje de vacío cuando la lista llega sin elementos', async () => {
    servidorFalso([{ metodo: 'GET', ruta: /tarifas/, responder: () => ({ estado: 200, cuerpo: [] }) }])
    render(<Lista />)
    expect(await screen.findByText('Sin tarifas registradas.')).toBeInTheDocument()
  })
})

describe('Acceso por rol (HTTP Basic)', () => {
  it('sin sesión pide credenciales y luego muestra la pantalla; las llamadas llevan Authorization', async () => {
    const llamadas = servidorFalso([{ metodo: 'GET', ruta: /tarifas/, responder: () => ({ estado: 200, cuerpo: [] }) }])
    render(<Protegida rol="Administrador" ayuda="Usuario admin."><Lista /></Protegida>)
    expect(screen.queryByText('Sin tarifas registradas.')).not.toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Usuario'), 'admin')
    await userEvent.type(screen.getByLabelText('Clave'), 'clave-de-prueba')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))
    expect(await screen.findByText('Sin tarifas registradas.')).toBeInTheDocument()
    await waitFor(() => expect(llamadas[0].cabeceras.Authorization).toBe(`Basic ${btoa('admin:clave-de-prueba')}`))
  })
})
