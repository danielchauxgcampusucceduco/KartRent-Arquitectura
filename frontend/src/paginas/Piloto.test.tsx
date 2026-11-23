import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import Piloto from './Piloto'
import { facturaFalsa, pagoFalso, reservaFalsa, servidorFalso, sesionFalsa, type Ruta } from '../pruebas-servidor'

const base: Ruta[] = [
  { metodo: 'GET', ruta: /^\/sesiones$/, responder: () => ({ estado: 200, cuerpo: [sesionFalsa(), sesionFalsa({ codigo: 'T-9', estado: 'EN_CURSO' })] }) },
  { metodo: 'POST', ruta: /^\/reservas$/, responder: () => ({ estado: 201, cuerpo: reservaFalsa() }) },
]

async function hastaElPago() {
  render(<Piloto />)
  await userEvent.type(screen.getByLabelText('Documento'), '1085123456')
  await userEvent.click(screen.getByRole('button', { name: 'Continuar' }))
  await userEvent.click(await screen.findByRole('button', { name: /5 libres/ }))
  await userEvent.click(screen.getByRole('button', { name: 'Reservar cupos' }))
  await screen.findByRole('button', { name: 'Pagar con tarjeta' })
}

describe('HU-01 · pantalla del piloto', () => {
  it('no trae ningún documento escrito de fábrica y solo ofrece tandas abiertas', async () => {
    servidorFalso(base)
    render(<Piloto />)
    expect(screen.getByLabelText('Documento')).toHaveValue('')
    expect(await screen.findByRole('button', { name: /5 libres/ })).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: /libres/ })).toHaveLength(1)
  })

  it('pago aprobado: muestra la factura y confirma', async () => {
    servidorFalso([...base, { metodo: 'POST', ruta: /pagos$/, responder: () => ({ estado: 201, cuerpo: {
      reserva: reservaFalsa({ estado: 'CONFIRMADA' }), pago: pagoFalso(), factura: facturaFalsa } }) }])
    await hastaElPago()
    await userEvent.click(screen.getByRole('button', { name: 'Pagar con tarjeta' }))
    expect(await screen.findByText('FE-0001')).toBeInTheDocument()
    expect(screen.getByText(/Pago aprobado/)).toBeInTheDocument()
  })

  it('pago rechazado: no muestra factura, avisa y deja reintentar con otra Idempotency-Key', async () => {
    let intento = 0
    const llamadas = servidorFalso([...base, { metodo: 'POST', ruta: /pagos$/, responder: () => (++intento === 1
      ? { estado: 201, cuerpo: { reserva: reservaFalsa(), pago: pagoFalso({ aprobado: false }), factura: null } }
      : { estado: 201, cuerpo: { reserva: reservaFalsa({ estado: 'CONFIRMADA' }), pago: pagoFalso(), factura: facturaFalsa } }) }])
    await hastaElPago()
    await userEvent.click(screen.getByRole('button', { name: 'Simular rechazo' }))
    expect(await screen.findByText('Pago rechazado')).toBeInTheDocument()
    expect(screen.queryByText('FE-0001')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Pagar con tarjeta' })).toBeEnabled()

    await userEvent.click(screen.getByRole('button', { name: 'Pagar con tarjeta' }))
    expect(await screen.findByText('FE-0001')).toBeInTheDocument()
    const claves = llamadas.filter(l => l.ruta.endsWith('/pagos')).map(l => l.cabeceras['Idempotency-Key'])
    expect(claves).toHaveLength(2)
    expect(claves[0]).toBeTruthy()
    expect(claves[0]).not.toBe(claves[1])
  })

  it('un 409 al pagar se muestra como conflicto y no rompe la pantalla', async () => {
    servidorFalso([...base,
      { metodo: 'POST', ruta: /pagos$/, responder: () => ({ estado: 409, cuerpo: { title: 'Conflicto', detail: 'La retención de la reserva expiró' } }) },
      { metodo: 'GET', ruta: /^\/reservas\/[\w-]+$/, responder: () => ({ estado: 200, cuerpo: reservaFalsa({ estado: 'EXPIRADA' }) }) }])
    await hastaElPago()
    await userEvent.click(screen.getByRole('button', { name: 'Pagar con tarjeta' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('La retención de la reserva expiró')
    await waitFor(() => expect(screen.getAllByText('EXPIRADA').length).toBeGreaterThan(0))
  })

  it('cancelar la reserva envía PATCH con estado CANCELADA y libera los cupos', async () => {
    const llamadas = servidorFalso([...base, { metodo: 'PATCH', ruta: /^\/reservas\//, responder: () => ({ estado: 200, cuerpo: reservaFalsa({ estado: 'CANCELADA' }) }) }])
    await hastaElPago()
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar reserva' }))
    expect(await screen.findByText(/cupos quedaron libres/)).toBeInTheDocument()
    const patch = llamadas.find(l => l.metodo === 'PATCH')
    expect(patch?.cuerpo).toEqual({ estado: 'CANCELADA' })
  })

  it('un documento no registrado abre el formulario de registro', async () => {
    servidorFalso([...base.slice(0, 1), { metodo: 'POST', ruta: /^\/reservas$/, responder: () => ({ estado: 404, cuerpo: { title: 'No encontrado', detail: 'Cliente 1 no registrado' } }) }])
    render(<Piloto />)
    await userEvent.type(screen.getByLabelText('Documento'), '1')
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }))
    await userEvent.click(await screen.findByRole('button', { name: /5 libres/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Reservar cupos' }))
    expect(await screen.findByPlaceholderText('Nombre completo')).toBeInTheDocument()
  })
})
