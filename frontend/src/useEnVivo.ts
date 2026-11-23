import { Client } from '@stomp/stompjs'
import { useEffect, useState } from 'react'

/** Suscripción STOMP sobre WebSocket (HU-03). Devuelve el último mensaje del tópico y el estado de conexión. */
export function useTopico<T>(topico: string | null) {
  const [mensaje, setMensaje] = useState<T | null>(null)
  const [conectado, setConectado] = useState(false)
  useEffect(() => {
    if (!topico) return
    const protocolo = location.protocol === 'https:' ? 'wss' : 'ws'
    const cliente = new Client({ brokerURL: `${protocolo}://${location.host}/ws`, reconnectDelay: 3000 })
    cliente.onConnect = () => {
      setConectado(true)
      cliente.subscribe(topico, m => setMensaje(JSON.parse(m.body)))
    }
    cliente.onWebSocketClose = () => setConectado(false)
    cliente.activate()
    return () => { void cliente.deactivate() }
  }, [topico])
  return { mensaje, conectado }
}
