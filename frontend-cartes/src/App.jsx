import { useState, useEffect } from 'react'
import './App.css'

const API = `http://${window.location.hostname}:8080/api`

function App() {
  // Auth
  const [utilisateur, setUtilisateur] = useState(null)
  const [token, setToken] = useState(localStorage.getItem('token') || null)
  const [pseudo, setPseudo] = useState("")
  const [motDePasse, setMotDePasse] = useState("")
  const [isInscription, setIsInscription] = useState(false)

  // Lobby
  const [parties, setParties] = useState([])
  const [utilisateurs, setUtilisateurs] = useState([])
  const [invitations, setInvitations] = useState([])

  // Vue détail d'une partie
  const [partieSelectionnee, setPartieSelectionnee] = useState(null)
  const [joueursDeLaPartie, setJoueursDeLaPartie] = useState([])

  // Vue jeu actif
  const [etatJeu, setEtatJeu] = useState(null)

  // Invitation popup
  const [invitDestId, setInvitDestId] = useState("")

  // Enchère
  const [enchereContrat, setEnchereContrat] = useState(80)
  const [enchereCouleur, setEnchereCouleur] = useState("Coeur")

  // Messages
  const [message, setMessage] = useState("")

  const authHeaders = () => ({
    'Content-Type': 'application/json',
    ...(token ? { 'Authorization': `Bearer ${token}` } : {})
  })

  // ===== POLLING =====
  useEffect(() => {
    if (!utilisateur || !token) return
    const interval = setInterval(() => {
      fetchParties()
      fetchInvitations()
      fetchUtilisateurs()
      if (partieSelectionnee) fetchJoueurs(partieSelectionnee.id)
      // Polling de l'état du jeu si on est dans une partie active
      if (etatJeu && ['EN_ENCHERE', 'EN_JEU'].includes(etatJeu.statut)) {
        fetchEtatJeu(etatJeu.partieId)
      }
    }, 3000)
    return () => clearInterval(interval)
  }, [utilisateur, partieSelectionnee, token, etatJeu])

  // ===== API CALLS =====

  const fetchParties = async () => {
    try {
      const res = await fetch(`${API}/parties`, { headers: authHeaders() })
      if (res.ok) setParties(await res.json())
    } catch (e) { console.error(e) }
  }

  const fetchUtilisateurs = async () => {
    try {
      const res = await fetch(`${API}/utilisateurs`, { headers: authHeaders() })
      if (res.ok) setUtilisateurs(await res.json())
    } catch (e) { console.error(e) }
  }

  const fetchInvitations = async () => {
    if (!utilisateur) return
    try {
      const res = await fetch(`${API}/invitation/recues?utilisateurId=${utilisateur.id}`, { headers: authHeaders() })
      if (res.ok) setInvitations(await res.json())
    } catch (e) { console.error(e) }
  }

  const fetchJoueurs = async (partieId) => {
    try {
      const res = await fetch(`${API}/partie/${partieId}/joueurs`, { headers: authHeaders() })
      if (res.ok) setJoueursDeLaPartie(await res.json())
    } catch (e) { console.error(e) }
  }

  const fetchPartie = async (partieId) => {
    try {
      const res = await fetch(`${API}/partie/${partieId}`, { headers: authHeaders() })
      if (res.ok) {
        const data = await res.json()
        setPartieSelectionnee(data)
        fetchJoueurs(partieId)
        // Si la partie est en cours de jeu, charger l'état du jeu
        if (['EN_ENCHERE', 'EN_JEU', 'TERMINEE'].includes(data.statut)) {
          fetchEtatJeu(partieId)
        }
      }
    } catch (e) { console.error(e) }
  }

  const fetchEtatJeu = async (partieId) => {
    if (!utilisateur) return
    try {
      const res = await fetch(
        `${API}/partie/${partieId}/etat?utilisateurId=${utilisateur.id}`,
        { headers: authHeaders() }
      )
      if (res.ok) setEtatJeu(await res.json())
    } catch (e) { console.error(e) }
  }

  // ===== ACTIONS AUTH =====

  const handleAuth = async () => {
    if (!pseudo.trim() || !motDePasse.trim()) return
    const endpoint = isInscription ? 'inscrire' : 'connexion'
    try {
      const res = await fetch(`${API}/auth/${endpoint}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ pseudo: pseudo.trim(), motDePasse })
      })
      if (res.ok) {
        const data = await res.json()
        setUtilisateur(data.utilisateur)
        setToken(data.token)
        localStorage.setItem('token', data.token)
        setMessage(`Bienvenue ${data.utilisateur.pseudo} !`)
        setTimeout(() => { fetchParties(); fetchUtilisateurs(); fetchInvitations() }, 100)
      } else {
        const err = await res.json()
        setMessage(err.erreur || "Erreur d'authentification.")
      }
    } catch (e) { setMessage("Erreur de connexion au serveur.") }
  }

  const deconnexion = () => {
    setUtilisateur(null); setToken(null)
    localStorage.removeItem('token')
    setPseudo(""); setMotDePasse("")
    setPartieSelectionnee(null); setEtatJeu(null); setMessage("")
  }

  // ===== ACTIONS LOBBY =====

  const creerPartie = async () => {
    try {
      const res = await fetch(`${API}/partie/creer`, { method: 'POST', headers: authHeaders() })
      const data = await res.json()
      setMessage(`Partie #${data.id} créée !`)
      fetchParties()
      await fetch(`${API}/partie/${data.id}/rejoindre?utilisateurId=${utilisateur.id}`, { method: 'POST', headers: authHeaders() })
      fetchPartie(data.id)
    } catch (e) { setMessage("Erreur lors de la création.") }
  }

  const rejoindrePartie = async (partieId) => {
    try {
      const res = await fetch(`${API}/partie/${partieId}/rejoindre?utilisateurId=${utilisateur.id}`, { method: 'POST', headers: authHeaders() })
      if (res.ok) { setMessage(`Tu as rejoint la partie #${partieId}`); fetchPartie(partieId) }
      else { const err = await res.json().catch(() => null); setMessage(err?.erreur || "Erreur.") }
    } catch (e) { setMessage("Erreur.") }
  }

  const demarrerPartie = async (partieId) => {
    try {
      const res = await fetch(`${API}/partie/${partieId}/demarrer`, { method: 'POST', headers: authHeaders() })
      if (res.ok) {
        setMessage(`Partie #${partieId} démarrée ! Phase d'enchères.`)
        fetchPartie(partieId)
        setTimeout(() => fetchEtatJeu(partieId), 200)
      } else { const err = await res.json().catch(() => null); setMessage(err?.erreur || "Erreur au démarrage.") }
    } catch (e) { setMessage("Erreur au démarrage.") }
  }

  const envoyerInvitation = async (partieId) => {
    if (!invitDestId) return
    try {
      const res = await fetch(`${API}/invitation/envoyer?expediteurId=${utilisateur.id}&destinataireId=${invitDestId}&partieId=${partieId}`, { method: 'POST', headers: authHeaders() })
      if (res.ok) { setMessage(`Invitation envoyée !`); setInvitDestId("") }
      else { const err = await res.json().catch(() => null); setMessage(err?.erreur || "Erreur d'envoi.") }
    } catch (e) { setMessage("Erreur d'envoi.") }
  }

  const accepterInvitation = async (invId) => {
    try {
      const res = await fetch(`${API}/invitation/${invId}/accepter`, { method: 'POST', headers: authHeaders() })
      if (res.ok) { setMessage("Invitation acceptée !"); fetchInvitations(); fetchParties() }
      else { const err = await res.json().catch(() => null); setMessage(err?.erreur || "Erreur.") }
    } catch (e) { setMessage("Erreur.") }
  }

  const refuserInvitation = async (invId) => {
    try {
      await fetch(`${API}/invitation/${invId}/refuser`, { method: 'POST', headers: authHeaders() })
      setMessage("Invitation refusée."); fetchInvitations()
    } catch (e) { setMessage("Erreur.") }
  }

  // ===== ACTIONS JEU =====

  const handleEnchere = async (passe) => {
    if (!etatJeu) return
    const body = passe
      ? { passe: true }
      : { passe: false, contrat: enchereContrat, couleur: enchereCouleur }
    try {
      const res = await fetch(
        `${API}/partie/${etatJeu.partieId}/encherir?utilisateurId=${utilisateur.id}`,
        { method: 'POST', headers: authHeaders(), body: JSON.stringify(body) }
      )
      if (res.ok) { setEtatJeu(await res.json()); setMessage("") }
      else { const err = await res.json().catch(() => null); setMessage(err?.erreur || "Erreur d'enchère.") }
    } catch (e) { setMessage("Erreur.") }
  }

  const handleJouerCarte = async (carteId) => {
    if (!etatJeu) return
    try {
      const res = await fetch(
        `${API}/partie/${etatJeu.partieId}/jouer?utilisateurId=${utilisateur.id}`,
        { method: 'POST', headers: authHeaders(), body: JSON.stringify({ carteId }) }
      )
      if (res.ok) { setEtatJeu(await res.json()); setMessage("") }
      else { const err = await res.json().catch(() => null); setMessage(err?.erreur || "Impossible de jouer cette carte.") }
    } catch (e) { setMessage("Erreur.") }
  }

  // ===== RENDU =====

  // Écran de connexion
  if (!utilisateur) {
    return (
      <div className="app">
        <div className="login-card">
          <h1>Belote ENSEEIHT</h1>
          <p className="subtitle">Jeu de cartes en ligne</p>
          <input type="text" placeholder="Pseudo (3-20 caractères)" value={pseudo}
            onChange={(e) => setPseudo(e.target.value)} />
          <input type="password" placeholder="Mot de passe (min. 4 caractères)" value={motDePasse}
            onChange={(e) => setMotDePasse(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleAuth()} />
          <button className="btn-primary" onClick={handleAuth}>
            {isInscription ? "S'inscrire" : "Se connecter"}
          </button>
          <button className="btn-small btn-outline" onClick={() => setIsInscription(!isInscription)}>
            {isInscription ? "Déjà un compte ? Se connecter" : "Pas de compte ? S'inscrire"}
          </button>
          {message && <div className="flash">{message}</div>}
        </div>
      </div>
    )
  }

  // Écran de jeu actif
  if (etatJeu && ['EN_ENCHERE', 'EN_JEU', 'TERMINEE'].includes(etatJeu.statut)) {
    const monTour = etatJeu.tourJoueurId === etatJeu.monJoueurId
    return (
      <div className="app">
        <header className="header">
          <h1>Belote ENSEEIHT — Partie #{etatJeu.partieId}</h1>
          <div className="user-info">
            <span className="user-badge">{utilisateur.pseudo} · Équipe {etatJeu.monEquipe}</span>
            <button className="btn-small btn-outline" onClick={() => setEtatJeu(null)}>← Lobby</button>
            <button className="btn-small btn-outline" onClick={deconnexion}>Déconnexion</button>
          </div>
        </header>

        {message && <div className="flash">{message}</div>}

        <div className="game-layout">

          {/* Colonne gauche : infos partie + pli courant */}
          <div className="panel">
            <h2>État de la partie</h2>
            <p>Statut : <strong>{etatJeu.statut}</strong></p>
            {etatJeu.atout && <p>Atout : <strong>{etatJeu.atout}</strong> (contrat {etatJeu.contratValeur})</p>}
            <p>Score — Équipe A: <strong>{etatJeu.scoreA}</strong> | Équipe B: <strong>{etatJeu.scoreB}</strong></p>
            {etatJeu.statut !== 'TERMINEE' && (
              <p>Tour de : <strong>{monTour ? 'Vous' : etatJeu.tourPseudo}</strong></p>
            )}
            {etatJeu.numPliCourant > 0 && (
              <p>Pli n° <strong>{etatJeu.numPliCourant}</strong> / 8</p>
            )}

            {/* Pli courant */}
            {etatJeu.statut === 'EN_JEU' && (
              <div>
                <h3>Pli en cours ({etatJeu.pliCourant?.length || 0}/4)</h3>
                {(!etatJeu.pliCourant || etatJeu.pliCourant.length === 0) ? (
                  <p className="empty">Aucune carte jouée.</p>
                ) : (
                  <div className="pli-cartes">
                    {etatJeu.pliCourant.map((cp, i) => (
                      <div key={i} className="carte-pli">
                        <span className="carte-label">{cp.carte.valeur} {cp.carte.couleur}</span>
                        <span className="carte-joueur">{cp.pseudo} (Éq.{cp.equipe})</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* Historique enchères */}
            {etatJeu.statut === 'EN_ENCHERE' && etatJeu.encheres?.length > 0 && (
              <div>
                <h3>Enchères</h3>
                <ul className="enchere-list">
                  {etatJeu.encheres.map(e => (
                    <li key={e.id}>
                      <strong>{e.pseudoJoueur}</strong> : {e.passe ? 'Passe' : `${e.contrat} ${e.couleur}`}
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {/* Résultat final */}
            {etatJeu.statut === 'TERMINEE' && etatJeu.resultat && (
              <div className="resultat-panel">
                <h3>Résultat final</h3>
                <p>Contrat : <strong>{etatJeu.resultat.contratValeur} {etatJeu.resultat.contratCouleur}</strong> (preneur : {etatJeu.resultat.pseudoPreneur})</p>
                <p>Contrat {etatJeu.resultat.contratRempli ? '✓ rempli' : '✗ chuté'}</p>
                <p>Équipe A : <strong>{etatJeu.scoreA} pts</strong> | Équipe B : <strong>{etatJeu.scoreB} pts</strong></p>
                <p>Vainqueur : <strong>Équipe {etatJeu.resultat.gagnantEquipe}</strong></p>
              </div>
            )}
          </div>

          {/* Colonne droite : actions */}
          <div className="panel">

            {/* Phase enchères */}
            {etatJeu.statut === 'EN_ENCHERE' && (
              <div>
                <h2>Enchères</h2>
                {monTour ? (
                  <div className="enchere-panel">
                    <p>C'est votre tour d'enchérir.</p>
                    <div className="enchere-row">
                      <select value={enchereContrat} onChange={e => setEnchereContrat(Number(e.target.value))}>
                        {[80, 90, 100, 110, 120, 130, 140, 150, 160].map(v => (
                          <option key={v} value={v}
                            disabled={etatJeu.contratValeur > 0 && v <= etatJeu.contratValeur}>
                            {v}
                          </option>
                        ))}
                      </select>
                      <select value={enchereCouleur} onChange={e => setEnchereCouleur(e.target.value)}>
                        {['Coeur', 'Carreau', 'Trefle', 'Pique'].map(c => (
                          <option key={c} value={c}>{c}</option>
                        ))}
                      </select>
                    </div>
                    <div className="enchere-actions">
                      <button className="btn-primary" onClick={() => handleEnchere(false)}>Enchérir</button>
                      <button className="btn-small btn-outline" onClick={() => handleEnchere(true)}>Passer</button>
                    </div>
                  </div>
                ) : (
                  <p className="empty">En attente de {etatJeu.tourPseudo}...</p>
                )}
              </div>
            )}

            {/* Phase jeu : ma main */}
            {etatJeu.statut === 'EN_JEU' && (
              <div>
                <h2>Ma main ({etatJeu.maMain?.length || 0} cartes)</h2>
                {(!etatJeu.maMain || etatJeu.maMain.length === 0) ? (
                  <p className="empty">Vous n'avez plus de cartes.</p>
                ) : (
                  <div className="main-cartes">
                    {etatJeu.maMain.map(carte => (
                      <button
                        key={carte.id}
                        className={`carte-btn ${monTour ? 'carte-jouable' : 'carte-inactive'}`}
                        onClick={() => monTour && handleJouerCarte(carte.id)}
                        disabled={!monTour}
                      >
                        <span className={`carte-valeur couleur-${carte.couleur?.toLowerCase()}`}>
                          {carte.valeur}
                        </span>
                        <span className="carte-couleur">{carte.couleur}</span>
                      </button>
                    ))}
                  </div>
                )}
                {!monTour && <p className="empty">En attente de {etatJeu.tourPseudo}...</p>}
              </div>
            )}

            {etatJeu.statut === 'TERMINEE' && (
              <div>
                <h2>Partie terminée</h2>
                <button className="btn-primary" onClick={() => setEtatJeu(null)}>Retour au lobby</button>
              </div>
            )}
          </div>
        </div>
      </div>
    )
  }

  // Écran principal (lobby)
  return (
    <div className="app">
      <header className="header">
        <h1>Belote ENSEEIHT</h1>
        <div className="user-info">
          <span className="user-badge">{utilisateur.pseudo} (ID: {utilisateur.id})</span>
          <button className="btn-small btn-outline" onClick={deconnexion}>Déconnexion</button>
        </div>
      </header>

      {message && <div className="flash">{message}</div>}

      <div className="main-content">
        {/* Colonne gauche : Lobby */}
        <div className="panel">
          <div className="panel-header">
            <h2>Lobby</h2>
            <button className="btn-primary" onClick={creerPartie}>+ Créer une partie</button>
          </div>

          {parties.length === 0 ? (
            <p className="empty">Aucune partie créée pour le moment.</p>
          ) : (
            <div className="party-list">
              {parties.map(p => (
                <div key={p.id}
                  className={`party-card ${partieSelectionnee?.id === p.id ? 'active' : ''}`}
                  onClick={() => fetchPartie(p.id)}>
                  <div className="party-info">
                    <span className="party-name">Partie #{p.id}</span>
                    <span className={`badge ${p.statut === 'OUVERTE' ? 'badge-open' : p.statut === 'TERMINEE' ? 'badge-done' : 'badge-playing'}`}>
                      {p.statut}
                    </span>
                  </div>
                  <div className="party-scores">Équipe A: {p.scoreA} — Équipe B: {p.scoreB}</div>
                  {p.statut === 'OUVERTE' && (
                    <button className="btn-small btn-join"
                      onClick={(e) => { e.stopPropagation(); rejoindrePartie(p.id) }}>
                      Rejoindre
                    </button>
                  )}
                  {['EN_ENCHERE', 'EN_JEU'].includes(p.statut) && (
                    <button className="btn-small btn-invite"
                      onClick={(e) => { e.stopPropagation(); fetchEtatJeu(p.id).then(() => {}) }}>
                      Rejoindre le jeu
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Colonne droite : détail partie + invitations */}
        <div className="panel">
          {partieSelectionnee ? (
            <div className="party-detail">
              <h2>Partie #{partieSelectionnee.id}</h2>
              <p>Statut : <strong>{partieSelectionnee.statut}</strong></p>
              <p>Score — Équipe A: <strong>{partieSelectionnee.scoreA}</strong> | Équipe B: <strong>{partieSelectionnee.scoreB}</strong></p>

              <h3>Joueurs ({joueursDeLaPartie.length}/4)</h3>
              {joueursDeLaPartie.length === 0 ? (
                <p className="empty">Aucun joueur pour le moment.</p>
              ) : (
                <ul className="player-list">
                  {joueursDeLaPartie.map(j => (
                    <li key={j.id}>
                      <span className="player-icon">*</span>
                      {j.pseudo || `Joueur #${j.id}`}
                      <span className="player-team">Équipe {j.equipe} — Pos {j.position}</span>
                    </li>
                  ))}
                </ul>
              )}

              <div className="party-actions">
                {partieSelectionnee.statut === 'OUVERTE' && joueursDeLaPartie.length === 4 && (
                  <button className="btn-primary btn-start"
                    onClick={() => demarrerPartie(partieSelectionnee.id)}>
                    Démarrer la partie
                  </button>
                )}

                {partieSelectionnee.statut === 'OUVERTE' && (
                  <div className="invite-section">
                    <h4>Inviter un joueur</h4>
                    <div className="invite-row">
                      <select value={invitDestId} onChange={(e) => setInvitDestId(e.target.value)}>
                        <option value="">— Choisir —</option>
                        {utilisateurs.filter(u => u.id !== utilisateur.id)
                          .map(u => <option key={u.id} value={u.id}>{u.pseudo} (#{u.id})</option>)}
                      </select>
                      <button className="btn-small btn-invite"
                        onClick={() => envoyerInvitation(partieSelectionnee.id)}>
                        Inviter
                      </button>
                    </div>
                  </div>
                )}

                {['EN_ENCHERE', 'EN_JEU'].includes(partieSelectionnee.statut) && (
                  <button className="btn-primary"
                    onClick={() => fetchEtatJeu(partieSelectionnee.id)}>
                    Aller au jeu
                  </button>
                )}
              </div>
            </div>
          ) : (
            <p className="empty">Sélectionne une partie dans le lobby pour voir les détails.</p>
          )}

          {/* Invitations reçues */}
          <div className="invitations-section">
            <h2>Invitations reçues ({invitations.filter(i => i.statut === 'EN_ATTENTE').length})</h2>
            {invitations.filter(i => i.statut === 'EN_ATTENTE').length === 0 ? (
              <p className="empty">Aucune invitation en attente.</p>
            ) : (
              <div className="invitation-list">
                {invitations.filter(i => i.statut === 'EN_ATTENTE').map(inv => (
                  <div key={inv.id} className="invitation-card">
                    <span>De <strong>{inv.pseudoExpediteur}</strong> — Partie #{inv.partieId || '?'}</span>
                    <div className="inv-actions">
                      <button className="btn-small btn-accept" onClick={() => accepterInvitation(inv.id)}>Accepter</button>
                      <button className="btn-small btn-refuse" onClick={() => refuserInvitation(inv.id)}>Refuser</button>
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
