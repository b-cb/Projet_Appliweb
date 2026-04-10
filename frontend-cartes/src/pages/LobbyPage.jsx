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
    setJoueurs(await api.fetchJoueurs(token, partieId))
  }, [token])

  useEffect(() => {
    if (!utilisateur) { navigate('/'); return }
    chargerParties(); chargerUtilisateurs(); chargerInvitations()
    const interval = setInterval(() => {
      chargerParties(); chargerInvitations(); chargerUtilisateurs()
    }, 4000)
    return () => clearInterval(interval)
  }, [utilisateur])

  const selectionnerPartie = async (partieId) => {
    const p = await api.fetchPartie(token, partieId)
    if (p) { setPartieSelectionnee(p); chargerJoueurs(partieId) }
  }

  const creerPartie = async () => {
    const { ok, data } = await api.creerPartie(token)
    if (ok) {
      afficherFlash(`Partie #${data.id} créée !`)
      await api.rejoindrePartie(token, data.id, utilisateur.id)
      chargerParties()
      selectionnerPartie(data.id)
    }
  }

  const creerAvecBots = async () => {
    const { ok, data } = await api.creerPartieAvecBots(token, utilisateur.id)
    if (ok) {
      afficherFlash(`Partie #${data.id} avec bots créée !`)
      chargerParties()
      navigate(`/partie/${data.id}`)
    } else {
      afficherFlash(data?.erreur || 'Erreur lors de la création avec bots.')
    }
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
    if (ok) { navigate(`/partie/${partieId}`) }
    else afficherFlash(data?.erreur || 'Erreur au démarrage.')
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
        {/* Panneau gauche — liste des parties */}
        <div className="panel">
          <div className="panel-header">
            <h2>Parties ({parties.length})</h2>
            <div className="create-btns">
              <button className="btn-primary" onClick={creerPartie}>+ Créer</button>
              <button className="btn-small btn-bots" onClick={creerAvecBots}>🤖 Avec bots</button>
            </div>
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
                    </div>
                    <div className="party-scores">Éq.A: {p.scoreA} — Éq.B: {p.scoreB}</div>
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
              <h2>Partie #{partieSelectionnee.id}</h2>
              <p>Statut : <strong>{partieSelectionnee.statut}</strong></p>

              <h3>Joueurs ({joueurs.length}/4)</h3>
              <div className="equipes-grid">
                <div className="equipe-col">
                  <div className="equipe-label eq1">Équipe 1</div>
                  {joueurs.filter(j => j.equipe === 1).map(j => (
                    <div key={j.id} className="player-item">
                      <span className="player-sym">♠</span>{j.pseudo || `Joueur #${j.id}`}
                    </div>
                  ))}
                </div>
                <div className="equipe-col">
                  <div className="equipe-label eq2">Équipe 2</div>
                  {joueurs.filter(j => j.equipe === 2).map(j => (
                    <div key={j.id} className="player-item">
                      <span className="player-sym">♥</span>{j.pseudo || `Joueur #${j.id}`}
                    </div>
                  ))}
                </div>
              </div>

              <div className="party-actions">
                {partieSelectionnee.statut === 'OUVERTE' && joueurs.length === 4 && (
                  <button className="btn-primary btn-start" onClick={() => demarrer(partieSelectionnee.id)}>
                    Démarrer la partie
                  </button>
                )}

                {partieSelectionnee.statut === 'OUVERTE' && (
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
                        {utilisateurs.filter(u => u.id !== utilisateur?.id).map(u => (
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
