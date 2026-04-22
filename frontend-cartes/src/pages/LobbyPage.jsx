import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/AuthContext'
import * as api from '../services/api'

export default function LobbyPage() {
  const { utilisateur, token, logout } = useAuth()
  const navigate = useNavigate()

  const [parties, setParties] = useState([])
  const [utilisateurs, setUtilisateurs] = useState([])
  const [invitations, setInvitations] = useState([])
  const [partieSelectionnee, setPartieSelectionnee] = useState(null)
  const [joueurs, setJoueurs] = useState([])
  const [invitPseudo, setInvitPseudo] = useState('')
  const [flash, setFlash] = useState('')

  // Panneau de création
  const [modeJeu, setModeJeu] = useState('COINCHE')
  const [nbJoueursMode, setNbJoueursMode] = useState(4)

  const afficherFlash = (msg) => { setFlash(msg); setTimeout(() => setFlash(''), 3000) }

  const chargerParties = useCallback(async () => {
    const data = await api.fetchParties(token)
    setParties(data.filter(p => p.statut !== 'TERMINEE'))
  }, [token])

  const chargerUtilisateurs = useCallback(async () => {
    setUtilisateurs(await api.fetchUtilisateurs(token))
  }, [token])

  const chargerInvitations = useCallback(async () => {
    if (!utilisateur) return
    setInvitations(await api.fetchInvitations(token, utilisateur.id))
  }, [token, utilisateur])

  const chargerJoueurs = useCallback(async (partieId) => {
    const liste = await api.fetchJoueurs(token, partieId)
    setJoueurs(liste)
    return liste
  }, [token])

  useEffect(() => {
    if (!utilisateur) { navigate('/'); return }
    chargerParties(); chargerUtilisateurs(); chargerInvitations()
    const interval = setInterval(() => {
      chargerParties(); chargerInvitations(); chargerUtilisateurs()
    }, 4000)
    return () => clearInterval(interval)
  }, [utilisateur])

  // Resync joueurs quand la partie sélectionnée change dans la liste rafraîchie
  useEffect(() => {
    if (!partieSelectionnee) return
    const updated = parties.find(p => p.id === partieSelectionnee.id)
    if (updated) setPartieSelectionnee(updated)
  }, [parties])

  const selectionnerPartie = async (partieId) => {
    const p = await api.fetchPartie(token, partieId)
    if (p) {
      setPartieSelectionnee(p)
      chargerJoueurs(partieId)
    }
  }

  // Crée la partie, rejoint automatiquement, sélectionne dans le panneau droit
  const creerPartie = async () => {
    const { ok, data } = await api.creerPartie(token, { typeJeu: modeJeu, nbJoueurs: nbJoueursMode })
    if (!ok) { afficherFlash(data?.erreur || 'Erreur lors de la création.'); return }
    await api.rejoindrePartie(token, data.id, utilisateur.id)
    afficherFlash(`Partie #${data.id} créée !`)
    chargerParties()
    selectionnerPartie(data.id)
  }

  const supprimer = async (partieId, e) => {
    e.stopPropagation()
    const { ok, data } = await api.supprimerPartie(token, partieId, utilisateur.id)
    if (ok) {
      afficherFlash(`Partie #${partieId} supprimée.`)
      if (partieSelectionnee?.id === partieId) setPartieSelectionnee(null)
      chargerParties()
    } else {
      afficherFlash(data?.erreur || 'Impossible de supprimer cette partie.')
    }
  }

  const rejoindre = async (partieId) => {
    const { ok, data } = await api.rejoindrePartie(token, partieId, utilisateur.id)
    if (ok) { afficherFlash(`Rejoint la partie #${partieId}`); selectionnerPartie(partieId) }
    else afficherFlash(data?.erreur || 'Erreur.')
  }

  const demarrer = async (partieId) => {
    const { ok, data } = await api.demarrerPartie(token, partieId)
    if (ok) navigate(`/partie/${partieId}`)
    else afficherFlash(data?.erreur || 'Erreur au démarrage.')
  }

  const toggleBots = async (partieId, botsDejaPresents) => {
    if (botsDejaPresents) {
      const { ok, data } = await api.retirerBots(token, partieId, utilisateur.id)
      if (!ok) { afficherFlash(data?.erreur || 'Erreur lors du retrait des bots.'); return }
    } else {
      const { ok, data } = await api.remplirAvecBots(token, partieId, utilisateur.id)
      if (!ok) { afficherFlash(data?.erreur || 'Erreur lors de l\'ajout des bots.'); return }
    }
    chargerJoueurs(partieId)
    chargerParties()
  }

  const inviter = async (partieId) => {
    if (!invitPseudo.trim()) return
    const dest = utilisateurs.find(u => u.pseudo.toLowerCase() === invitPseudo.trim().toLowerCase())
    if (!dest) { afficherFlash(`Joueur "${invitPseudo}" introuvable.`); return }
    const { ok, data } = await api.envoyerInvitation(token, utilisateur.id, dest.id, partieId)
    if (ok) { afficherFlash(`Invitation envoyée à ${dest.pseudo} !`); setInvitPseudo('') }
    else afficherFlash(data?.erreur || "Erreur d'envoi.")
  }

  const accepter = async (invId, partieId) => {
    const { ok } = await api.accepterInvitation(token, invId)
    if (ok) {
      afficherFlash('Invitation acceptée !')
      chargerInvitations(); chargerParties()
      if (partieId) selectionnerPartie(partieId)
    }
  }

  const refuser = async (invId) => {
    await api.refuserInvitation(token, invId)
    afficherFlash('Invitation refusée.'); chargerInvitations()
  }

  const deconnexion = () => { logout(); navigate('/') }

  const invitationsEnAttente = invitations.filter(i => i.statut === 'EN_ATTENTE')

  // Dérivé : y a-t-il des bots dans la partie sélectionnée ?
  const botsPresents = joueurs.some(j => j.bot)
  const nbRequis = partieSelectionnee?.nbJoueursRequis ?? 4
  const partiePleine = joueurs.length >= nbRequis
  const estDansLaPartie = joueurs.some(j => j.utilisateurId === utilisateur?.id)

  return (
    <div className="app">
      <header className="header">
        <div className="header-left">
          <span className="logo-mini">♠♥</span>
          <h1>Belote ENSEEIHT</h1>
        </div>
        <div className="user-info">
          <span className="user-badge">{utilisateur?.pseudo}</span>
          <button className="btn-small btn-outline" onClick={deconnexion}>Déconnexion</button>
        </div>
      </header>

      {flash && <div className="flash">{flash}</div>}

      <div className="main-content">
        {/* Panneau gauche — création + liste */}
        <div className="panel">
          <div className="panel-header">
            <h2>Parties ({parties.length})</h2>
          </div>

          {/* Panneau de création */}
          <div className="create-panel">
            <div className="create-panel-row">
              <label className="create-label">Mode</label>
              <div className="create-modes">
                {[
                  { label: 'Coinche (4j)', typeJeu: 'COINCHE', nb: 4 },
                  { label: 'Tarot 3j',    typeJeu: 'TAROT',   nb: 3 },
                  { label: 'Tarot 4j',    typeJeu: 'TAROT',   nb: 4 },
                  { label: 'Tarot 5j',    typeJeu: 'TAROT',   nb: 5 },
                ].map(({ label, typeJeu, nb }) => (
                  <button
                    key={label}
                    className={`mode-btn ${modeJeu === typeJeu && nbJoueursMode === nb ? 'mode-btn-active' : ''}`}
                    onClick={() => { setModeJeu(typeJeu); setNbJoueursMode(nb) }}
                  >
                    {label}
                  </button>
                ))}
              </div>
            </div>

            <button className="btn-primary create-btn-main" onClick={creerPartie}>
              + Créer la partie
            </button>
          </div>

          {parties.length === 0
            ? <p className="empty">Aucune partie en cours. Créez-en une !</p>
            : (
              <div className="party-list">
                {parties.map(p => (
                  <div key={p.id}
                    className={`party-card ${partieSelectionnee?.id === p.id ? 'active' : ''}`}
                    onClick={() => selectionnerPartie(p.id)}>
                    <div className="party-info">
                      <span className="party-name">Partie #{p.id}</span>
                      <span className={`badge ${p.statut === 'OUVERTE' ? 'badge-open' : 'badge-playing'}`}>
                        {p.statut === 'OUVERTE' ? 'Ouverte' : p.statut === 'EN_ENCHERE' ? 'Enchères' : 'En jeu'}
                      </span>
                      {p.typeJeu && p.typeJeu !== 'COINCHE' && (
                        <span className="badge badge-mode">{p.typeJeu} {p.nbJoueursRequis}j</span>
                      )}
                    </div>
                    <div className="party-scores">
                      {p.nombreJoueurs}/{p.nbJoueursRequis} joueurs
                      {p.statut !== 'OUVERTE' && ` · Éq.A: ${p.scoreA} — Éq.B: ${p.scoreB}`}
                    </div>
                    {p.statut === 'OUVERTE' && (
                      <div className="party-card-actions">
                        <button className="btn-small btn-join"
                          onClick={e => { e.stopPropagation(); rejoindre(p.id) }}>
                          Rejoindre
                        </button>
                        <button className="btn-small btn-refuse"
                          onClick={e => supprimer(p.id, e)}
                          title="Supprimer cette partie">
                          ✕
                        </button>
                      </div>
                    )}
                    {['EN_ENCHERE', 'EN_JEU'].includes(p.statut) && (
                      <button className="btn-small btn-invite"
                        onClick={e => { e.stopPropagation(); navigate(`/partie/${p.id}`) }}>
                        Rejoindre le jeu
                      </button>
                    )}
                  </div>
                ))}
              </div>
            )}
        </div>

        {/* Panneau droit — détail + invitations */}
        <div className="panel">
          {partieSelectionnee ? (
            <div className="party-detail">
              <h2>Partie #{partieSelectionnee.id}
                {partieSelectionnee.typeJeu && partieSelectionnee.typeJeu !== 'COINCHE' && (
                  <span className="badge badge-mode" style={{ marginLeft: 8, fontSize: '0.75rem' }}>
                    {partieSelectionnee.typeJeu} {partieSelectionnee.nbJoueursRequis}j
                  </span>
                )}
              </h2>
              <p>Statut : <strong>{partieSelectionnee.statut}</strong></p>

              <h3>Joueurs ({joueurs.length}/{nbRequis})</h3>
              <div className="player-list-lobby">
                {joueurs.map(j => (
                  <div key={j.id} className={`player-item ${j.bot ? 'player-bot' : ''}`}>
                    <span className="player-sym">{j.bot ? '🤖' : '♠'}</span>
                    {j.pseudo || `Joueur #${j.id}`}
                    {j.bot && <span className="bot-label"> (bot)</span>}
                  </div>
                ))}
                {/* Emplacements vides */}
                {Array.from({ length: Math.max(0, nbRequis - joueurs.length) }).map((_, i) => (
                  <div key={`vide-${i}`} className="player-item player-vide">
                    <span className="player-sym">·</span>
                    <em>Place libre</em>
                  </div>
                ))}
              </div>

              <div className="party-actions">
                {/* Bouton démarrer — visible dès que la partie est pleine */}
                {partieSelectionnee.statut === 'OUVERTE' && partiePleine && (
                  <button className="btn-primary btn-start" onClick={() => demarrer(partieSelectionnee.id)}>
                    Démarrer la partie
                  </button>
                )}

                {/* Contrôle des bots — disponible tant que la partie est ouverte et que l'utilisateur est dedans */}
                {partieSelectionnee.statut === 'OUVERTE' && estDansLaPartie && (
                  <label className="bots-toggle">
                    <input
                      type="checkbox"
                      checked={botsPresents}
                      onChange={() => toggleBots(partieSelectionnee.id, botsPresents)}
                    />
                    Remplir les places libres avec des bots
                  </label>
                )}

                {/* Invitation */}
                {partieSelectionnee.statut === 'OUVERTE' && !partiePleine && estDansLaPartie && (
                  <div className="invite-section">
                    <h4>Inviter un joueur</h4>
                    <div className="invite-row">
                      <input
                        type="text"
                        placeholder="Pseudo du joueur…"
                        value={invitPseudo}
                        onChange={e => setInvitPseudo(e.target.value)}
                        onKeyDown={e => e.key === 'Enter' && inviter(partieSelectionnee.id)}
                        list="pseudos-list"
                      />
                      <datalist id="pseudos-list">
                        {utilisateurs.filter(u => u.id !== utilisateur?.id && !u.pseudo?.startsWith('Bot_')).map(u => (
                          <option key={u.id} value={u.pseudo} />
                        ))}
                      </datalist>
                      <button className="btn-small btn-invite" onClick={() => inviter(partieSelectionnee.id)}>
                        Inviter
                      </button>
                    </div>
                  </div>
                )}

                {['EN_ENCHERE', 'EN_JEU'].includes(partieSelectionnee.statut) && (
                  <button className="btn-primary" onClick={() => navigate(`/partie/${partieSelectionnee.id}`)}>
                    Aller au jeu
                  </button>
                )}
              </div>
            </div>
          ) : (
            <p className="empty" style={{ padding: '40px 0' }}>
              Sélectionne une partie pour voir les détails.
            </p>
          )}

          <div className="invitations-section">
            <h2>Invitations reçues ({invitationsEnAttente.length})</h2>
            {invitationsEnAttente.length === 0
              ? <p className="empty">Aucune invitation en attente.</p>
              : (
                <div className="invitation-list">
                  {invitationsEnAttente.map(inv => (
                    <div key={inv.id} className="invitation-card">
                      <span>De <strong>{inv.pseudoExpediteur}</strong> — Partie #{inv.partieId || '?'}</span>
                      <div className="inv-actions">
                        <button className="btn-small btn-accept" onClick={() => accepter(inv.id, inv.partieId)}>
                          Accepter
                        </button>
                        <button className="btn-small btn-refuse" onClick={() => refuser(inv.id)}>
                          Refuser
                        </button>
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
