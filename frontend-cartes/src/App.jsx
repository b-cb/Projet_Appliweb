import { useState, useEffect } from 'react'
import './App.css'

const API = `http://${window.location.hostname}:8080/api`

function App() {
  // État utilisateur connecté
  const [utilisateur, setUtilisateur] = useState(null)
  const [pseudo, setPseudo] = useState("")

  // Lobby
  const [parties, setParties] = useState([])
  const [utilisateurs, setUtilisateurs] = useState([])
  const [invitations, setInvitations] = useState([])

  // Vue détail d'une partie
  const [partieSelectionnee, setPartieSelectionnee] = useState(null)
  const [joueursDeLaPartie, setJoueursDeLaPartie] = useState([])

  // Invitation popup
  const [invitDestId, setInvitDestId] = useState("")

  // Messages
  const [message, setMessage] = useState("")

  // ===== POLLING =====
  useEffect(() => {
    if (!utilisateur) return
    const interval = setInterval(() => {
      fetchParties()
      fetchInvitations()
      fetchUtilisateurs()
      if (partieSelectionnee) fetchJoueurs(partieSelectionnee.id)
    }, 3000)
    return () => clearInterval(interval)
  }, [utilisateur, partieSelectionnee])

  // ===== API CALLS =====

  const fetchParties = async () => {
    try {
      const res = await fetch(`${API}/parties`)
      setParties(await res.json())
    } catch (e) { console.error(e) }
  }

  const fetchUtilisateurs = async () => {
    try {
      const res = await fetch(`${API}/utilisateurs`)
      setUtilisateurs(await res.json())
    } catch (e) { console.error(e) }
  }

  const fetchInvitations = async () => {
    if (!utilisateur) return
    try {
      const res = await fetch(`${API}/invitation/recues?utilisateurId=${utilisateur.id}`)
      setInvitations(await res.json())
    } catch (e) { console.error(e) }
  }

  const fetchJoueurs = async (partieId) => {
    try {
      const res = await fetch(`${API}/partie/${partieId}/joueurs`)
      setJoueursDeLaPartie(await res.json())
    } catch (e) { console.error(e) }
  }

  const fetchPartie = async (partieId) => {
    try {
      const res = await fetch(`${API}/partie/${partieId}`)
      const data = await res.json()
      setPartieSelectionnee(data)
      fetchJoueurs(partieId)
    } catch (e) { console.error(e) }
  }

  // ===== ACTIONS =====

  const connexion = async () => {
    if (!pseudo.trim()) return
    try {
      const res = await fetch(`${API}/utilisateur/connexion?pseudo=${pseudo}`, { method: 'POST' })
      const data = await res.json()
      setUtilisateur(data)
      setMessage(`Bienvenue ${data.pseudo} ! (ID: ${data.id})`)
      fetchParties()
      fetchUtilisateurs()
      fetchInvitations()
    } catch (e) { setMessage("Erreur de connexion.") }
  }

  const creerPartie = async () => {
    try {
      const res = await fetch(`${API}/partie/creer`, { method: 'POST' })
      const data = await res.json()
      setMessage(`Partie #${data.id} créée !`)
      fetchParties()
      // Rejoindre automatiquement la partie créée
      await fetch(`${API}/partie/${data.id}/rejoindre?utilisateurId=${utilisateur.id}`, { method: 'POST' })
      fetchPartie(data.id)
    } catch (e) { setMessage("Erreur lors de la création.") }
  }

  const rejoindrePartie = async (partieId) => {
    try {
      const res = await fetch(`${API}/partie/${partieId}/rejoindre?utilisateurId=${utilisateur.id}`, { method: 'POST' })
      if (res.ok) {
        setMessage(`Tu as rejoint la partie #${partieId}`)
        fetchPartie(partieId)
      } else {
        const txt = await res.text()
        setMessage(txt)
      }
    } catch (e) { setMessage("Erreur.") }
  }

  const demarrerPartie = async (partieId) => {
    try {
      const res = await fetch(`${API}/partie/${partieId}/demarrer`, { method: 'POST' })
      if (res.ok) {
        setMessage(`Partie #${partieId} démarrée ! Les cartes ont été distribuées.`)
        fetchPartie(partieId)
      } else {
        const txt = await res.text()
        setMessage(txt)
      }
    } catch (e) { setMessage("Erreur au démarrage.") }
  }

  const envoyerInvitation = async (partieId) => {
    if (!invitDestId) return
    try {
      const res = await fetch(`${API}/invitation/envoyer?expediteurId=${utilisateur.id}&destinataireId=${invitDestId}&partieId=${partieId}`, { method: 'POST' })
      if (res.ok) {
        setMessage(`Invitation envoyée !`)
        setInvitDestId("")
      } else {
        const txt = await res.text()
        setMessage(txt)
      }
    } catch (e) { setMessage("Erreur d'envoi.") }
  }

  const accepterInvitation = async (invId) => {
    try {
      const res = await fetch(`${API}/invitation/${invId}/accepter`, { method: 'POST' })
      if (res.ok) {
        setMessage("Invitation acceptée ! Tu as rejoint la partie.")
        fetchInvitations()
        fetchParties()
      } else {
        const txt = await res.text()
        setMessage(txt)
      }
    } catch (e) { setMessage("Erreur.") }
  }

  const refuserInvitation = async (invId) => {
    try {
      await fetch(`${API}/invitation/${invId}/refuser`, { method: 'POST' })
      setMessage("Invitation refusée.")
      fetchInvitations()
    } catch (e) { setMessage("Erreur.") }
  }

  const deconnexion = () => {
    setUtilisateur(null)
    setPseudo("")
    setPartieSelectionnee(null)
    setMessage("")
  }

  // ===== RENDU =====

  // Écran de connexion
  if (!utilisateur) {
    return (
      <div className="app">
        <div className="login-card">
          <h1>🂠 Belote ENSEEIHT</h1>
          <p className="subtitle">Jeu de cartes en ligne</p>
          <input
            type="text"
            placeholder="Entre ton pseudo..."
            value={pseudo}
            onChange={(e) => setPseudo(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && connexion()}
          />
          <button className="btn-primary" onClick={connexion}>Rejoindre</button>
        </div>
      </div>
    )
  }

  // Écran principal (lobby)
  return (
    <div className="app">
      {/* Header */}
      <header className="header">
        <h1>🂠 Belote ENSEEIHT</h1>
        <div className="user-info">
          <span className="user-badge">👤 {utilisateur.pseudo} (ID: {utilisateur.id})</span>
          <button className="btn-small btn-outline" onClick={deconnexion}>Déconnexion</button>
        </div>
      </header>

      {/* Message flash */}
      {message && <div className="flash">{message}</div>}

      <div className="main-content">
        {/* Colonne gauche : Lobby */}
        <div className="panel">
          <div className="panel-header">
            <h2>🎮 Lobby</h2>
            <button className="btn-primary" onClick={creerPartie}>+ Créer une partie</button>
          </div>

          {parties.length === 0 ? (
            <p className="empty">Aucune partie créée pour le moment.</p>
          ) : (
            <div className="party-list">
              {parties.map(p => (
                <div
                  key={p.id}
                  className={`party-card ${partieSelectionnee?.id === p.id ? 'active' : ''}`}
                  onClick={() => fetchPartie(p.id)}
                >
                  <div className="party-info">
                    <span className="party-name">Partie #{p.id}</span>
                    <span className={`badge ${p.statut === 'OUVERTE' ? 'badge-open' : 'badge-playing'}`}>
                      {p.statut}
                    </span>
                  </div>
                  <div className="party-scores">
                    Équipe A: {p.scoreA} — Équipe B: {p.scoreB}
                  </div>
                  {p.statut === 'OUVERTE' && (
                    <button className="btn-small btn-join" onClick={(e) => { e.stopPropagation(); rejoindrePartie(p.id) }}>
                      Rejoindre
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Colonne droite : détail partie + invitations */}
        <div className="panel">
          {/* Détail partie sélectionnée */}
          {partieSelectionnee ? (
            <div className="party-detail">
              <h2>📋 Partie #{partieSelectionnee.id}</h2>
              <p>Statut : <strong>{partieSelectionnee.statut}</strong></p>
              <p>Score — Équipe A: <strong>{partieSelectionnee.scoreA}</strong> | Équipe B: <strong>{partieSelectionnee.scoreB}</strong></p>

              <h3>Joueurs ({joueursDeLaPartie.length}/4)</h3>
              {joueursDeLaPartie.length === 0 ? (
                <p className="empty">Aucun joueur pour le moment.</p>
              ) : (
                <ul className="player-list">
                  {joueursDeLaPartie.map(j => (
                    <li key={j.id}>
                      <span className="player-icon">🃏</span>
                      {j.utilisateur?.pseudo || `Joueur #${j.id}`}
                      <span className="player-team">Équipe {j.equipe} — Pos {j.position}</span>
                    </li>
                  ))}
                </ul>
              )}

              {/* Actions */}
              <div className="party-actions">
                {partieSelectionnee.statut === 'OUVERTE' && joueursDeLaPartie.length === 4 && (
                  <button className="btn-primary btn-start" onClick={() => demarrerPartie(partieSelectionnee.id)}>
                    🚀 Démarrer la partie
                  </button>
                )}

                {partieSelectionnee.statut === 'OUVERTE' && (
                  <div className="invite-section">
                    <h4>Inviter un joueur</h4>
                    <div className="invite-row">
                      <select value={invitDestId} onChange={(e) => setInvitDestId(e.target.value)}>
                        <option value="">— Choisir —</option>
                        {utilisateurs
                          .filter(u => u.id !== utilisateur.id)
                          .map(u => <option key={u.id} value={u.id}>{u.pseudo} (#{u.id})</option>)
                        }
                      </select>
                      <button className="btn-small btn-invite" onClick={() => envoyerInvitation(partieSelectionnee.id)}>
                        📨 Inviter
                      </button>
                    </div>
                  </div>
                )}

                {partieSelectionnee.statut === 'EN_COURS' && (
                  <p className="game-started">✅ La partie est en cours ! Les cartes ont été distribuées.</p>
                )}
              </div>
            </div>
          ) : (
            <p className="empty">Sélectionne une partie dans le lobby pour voir les détails.</p>
          )}

          {/* Invitations reçues */}
          <div className="invitations-section">
            <h2>📬 Invitations reçues ({invitations.filter(i => i.statut === 'EN_ATTENTE').length})</h2>
            {invitations.filter(i => i.statut === 'EN_ATTENTE').length === 0 ? (
              <p className="empty">Aucune invitation en attente.</p>
            ) : (
              <div className="invitation-list">
                {invitations.filter(i => i.statut === 'EN_ATTENTE').map(inv => (
                  <div key={inv.id} className="invitation-card">
                    <span>De <strong>{inv.expediteur?.pseudo}</strong> — Partie #{inv.partie?.id || '?'}</span>
                    <div className="inv-actions">
                      <button className="btn-small btn-accept" onClick={() => accepterInvitation(inv.id)}>✅ Accepter</button>
                      <button className="btn-small btn-refuse" onClick={() => refuserInvitation(inv.id)}>❌ Refuser</button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

export default App