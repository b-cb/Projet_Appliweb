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

  // Panneau de création de partie
  const [modeJeu, setModeJeu] = useState('COINCHE')   // COINCHE | TAROT
  const [nbJoueursMode, setNbJoueursMode] = useState(4)
  const [avecBots, setAvecBots] = useState(false)
  // Condition de fin de partie
  const [modeCondition, setModeCondition] = useState('donnes')  // 'donnes' | 'points'
  const [maxDonnes, setMaxDonnes] = useState(5)
  const [maxPoints, setMaxPoints] = useState(1000)

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
    const conditionBody = modeCondition === 'donnes'
      ? { maxDonnes, maxPoints: 0 }
      : { maxDonnes: 0, maxPoints }
    if (avecBots) {
      const { ok, data } = await api.creerPartieAvecBots(token, utilisateur.id, {
        typeJeu: modeJeu, nbJoueurs: nbJoueursMode, ...conditionBody
      })
      if (ok) {
        afficherFlash(`Partie #${data.id} avec bots créée !`)
        chargerParties()
        navigate(`/partie/${data.id}`)
      } else {
        afficherFlash(data?.erreur || 'Erreur lors de la création avec bots.')
      }
    } else {
      const { ok, data } = await api.creerPartie(token, { typeJeu: modeJeu, nbJoueurs: nbJoueursMode, ...conditionBody })
      if (ok) {
        afficherFlash(`Partie #${data.id} créée !`)
        await api.rejoindrePartie(token, data.id, utilisateur.id)
        chargerParties()
        selectionnerPartie(data.id)
      } else {
        afficherFlash(data?.erreur || 'Erreur lors de la création.')
      }
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
          </div>

          {/* Panneau de création */}
          <div className="create-panel">
            <div className="create-panel-row">
              <label className="create-label">Mode</label>
              <div className="create-modes">
                {[
                  { label: 'Coinche (4j)', typeJeu: 'COINCHE', nb: 4 },
                  { label: 'Tarot 3j', typeJeu: 'TAROT', nb: 3 },
                  { label: 'Tarot 4j', typeJeu: 'TAROT', nb: 4 },
                  { label: 'Tarot 5j', typeJeu: 'TAROT', nb: 5 },
                ].map(({ label, typeJeu, nb, disabled }) => (
                  <button
                    key={label}
                    className={`mode-btn ${modeJeu === typeJeu && nbJoueursMode === nb ? 'mode-btn-active' : ''} ${disabled ? 'mode-btn-disabled' : ''}`}
                    onClick={() => { if (!disabled) { setModeJeu(typeJeu); setNbJoueursMode(nb) } }}
                    title={disabled ? 'Bientôt disponible' : undefined}
                  >
                    {label}{disabled && <span className="bientot"> soon</span>}
                  </button>
                ))}
              </div>
            </div>

            <div className="create-panel-row">
              <label className="create-label">
                <input
                  type="checkbox"
                  checked={avecBots}
                  onChange={e => setAvecBots(e.target.checked)}
                  style={{ marginRight: 6 }}
                />
                Remplir avec des bots
                {modeJeu === 'TAROT' && nbJoueursMode === 5 && avecBots && (
                  <span style={{ color: 'var(--text-muted)', fontSize: '0.8rem', marginLeft: 6 }}>
                    (1 humain + 4 bots)
                  </span>
                )}
              </label>
            </div>

            <div className="create-panel-row">
              <label className="create-label">Condition de fin</label>
              <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                <select
                  value={modeCondition}
                  onChange={e => setModeCondition(e.target.value)}
                  style={{ padding: '4px 8px', borderRadius: 6 }}
                >
                  <option value="donnes">Nombre de donnes</option>
                  <option value="points">Score maximum</option>
                </select>
                {modeCondition === 'donnes' ? (
                  <input
                    type="number" min={1} max={20} value={maxDonnes}
                    onChange={e => setMaxDonnes(parseInt(e.target.value) || 1)}
                    style={{ width: 60, padding: '4px 6px', borderRadius: 6 }}
                  />
                ) : (
                  <input
                    type="number" min={100} max={5000} step={100} value={maxPoints}
                    onChange={e => setMaxPoints(parseInt(e.target.value) || 500)}
                    style={{ width: 80, padding: '4px 6px', borderRadius: 6 }}
                  />
                )}
                <span style={{ color: 'var(--text-muted)', fontSize: '0.8rem' }}>
                  {modeCondition === 'donnes' ? `donne${maxDonnes > 1 ? 's' : ''}` : 'pts'}
                </span>
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
                        <span className="badge badge-mode">{p.typeJeu}</span>
                      )}
                    </div>
                    {p.typeJeu === 'TAROT' ? (
                      <div className="party-scores">
                        {p.scoresJoueurs && Object.entries(p.scoresJoueurs).map(([pseudo, score]) => (
                          <span key={pseudo} style={{marginRight: 6}}>{pseudo}: {score}</span>
                        ))}
                      </div>
                    ) : (
                      <div className="party-scores">Éq.A: {p.scoreA} — Éq.B: {p.scoreB}</div>
                    )}
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

              <h3>Joueurs ({joueurs.length}/{partieSelectionnee.nbJoueursRequis ?? 4})</h3>
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
                {partieSelectionnee.statut === 'OUVERTE' && joueurs.length === (partieSelectionnee.nbJoueursRequis ?? 4) && (
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
