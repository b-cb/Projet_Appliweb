import { useRef, useEffect, useState } from 'react'

export default function ChatPanel({ messages, moPseudo, onEnvoyer }) {
  const [input, setInput] = useState('')
  const endRef = useRef(null)

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const envoyer = () => {
    if (!input.trim()) return
    onEnvoyer(input.trim())
    setInput('')
  }

  return (
    <div className="chat-panel">
      <div className="chat-header">Chat</div>
      <div className="chat-messages">
        {messages.length === 0
          ? <p className="empty">Aucun message.</p>
          : messages.map((m, i) => (
            <div key={m.id || i} className={`chat-msg ${m.pseudo === moPseudo ? 'chat-moi' : ''}`}>
              <span className="chat-pseudo">{m.pseudo}</span>
              <span className="chat-contenu">{m.contenu}</span>
            </div>
          ))
        }
        <div ref={endRef} />
      </div>
      <div className="chat-input-row">
        <input
          type="text"
          placeholder="Écrire…"
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && envoyer()}
          maxLength={300}
        />
        <button className="btn-small btn-invite" onClick={envoyer}>→</button>
      </div>
    </div>
  )
}
