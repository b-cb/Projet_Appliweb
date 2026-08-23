import { useRef, useCallback } from 'react'
import { Client } from '@stomp/stompjs'

const WS_URL = `ws://${window.location.hostname}:8080/ws/websocket`

export function useWebSocket() {
  const clientRef = useRef(null)
  const partieAbonneeRef = useRef(null)

  const connecter = useCallback((partieId, userId, { onEtatJeu, onJoueurRejoint, onChat }) => {
    if (partieAbonneeRef.current === partieId && clientRef.current?.active) return

    if (clientRef.current?.active) clientRef.current.deactivate()

    const client = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 3000,
      // TODO SECURITY: transmettre le JWT dans connectHeaders lorsque le backend
      // validera STOMP CONNECT et autorisera les abonnements aux topics privés.
      onConnect: () => {
        partieAbonneeRef.current = partieId

        // Topic commun : événements sans données privées
        client.subscribe(`/topic/partie/${partieId}`, (frame) => {
          const evt = JSON.parse(frame.body)
          if (evt.type === 'JOUEUR_REJOINT' && onJoueurRejoint) onJoueurRejoint(evt.payload)
          if (evt.type === 'CHAT' && onChat) onChat(evt.payload)
        })

        // Topic personnel : état du jeu avec la main du joueur
        client.subscribe(`/topic/partie/${partieId}/joueur/${userId}`, (frame) => {
          const evt = JSON.parse(frame.body)
          if (evt.payload?.maMain !== undefined && onEtatJeu) onEtatJeu(evt.payload)
        })
      },
      onDisconnect: () => { partieAbonneeRef.current = null }
    })

    client.activate()
    clientRef.current = client
  }, [])

  const deconnecter = useCallback(() => {
    clientRef.current?.deactivate()
    clientRef.current = null
    partieAbonneeRef.current = null
  }, [])

  return { connecter, deconnecter }
}
