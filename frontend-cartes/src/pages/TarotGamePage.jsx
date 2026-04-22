/**
 * Page de jeu Tarot (3, 4 ou 5 joueurs).
 * Branché depuis GamePage quand typeJeu === 'TAROT'.
 */
import { useState, useEffect, useCallback, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/AuthContext'
import { useWebSocket } from '../hooks/useWebSocket'
import * as api from '../services/api'
import CardImage from '../components/CardImage'
import TarotBiddingPanel from '../components/TarotBiddingPanel'
import ChienPanel from '../components/ChienPanel'
import RoiSelector from '../components/RoiSelector'
import ChatPanel from '../components/ChatPanel'

const SUIT_SYMBOLS = { Coeur: '♥', Carreau: '♦', Trefle: '♣', Pique: '♠', Atout: '★' }
const BID_LABELS = { PETITE: 'Petite', GARDE: 'Garde', GARDE_SANS: 'Garde sans', GARDE_CONTRE: 'Garde contre' }

/**
 * Positions absolues (en %) pour chaque siège autour de la table Tarot.
 * L'offset 0 = le joueur courant (toujours en bas), puis sens horaire.
 *
 * 3j :  bas-centre | haut-gauche | haut-droite
 * 4j :  bas-centre | gauche      | haut-centre | droite
 * 5j :  bas-centre | bas-gauche  | haut-gauche | haut-droite | bas-droite
 */
const SEAT_POSITIONS = {
  3: [
    { bottom: '6%',  left: '50%',  transform: 'translateX(-50%)' },
    { top:    '8%',  left: '18%'  },
    { top:    '8%',  right: '18%' },
  ],
  4: [
    { bottom: '6%',  left: '50%',  transform: 'translateX(-50%)' },
    { top:    '50%', left: '4%',   transform: 'translateY(-50%)' },
    { top:    '6%',  left: '50%',  transform: 'translateX(-50%)' },
    { top:    '50%', right: '4%',  transform: 'translateY(-50%)' },
  ],
  5: [
    { bottom: '5%',  left: '50%',  transform: 'translateX(-50%)' },
    { bottom: '28%', left: '6%'   },
    { top:    '8%',  left: '10%'  },
    { top:    '8%',  right: '10%' },
    { bottom: '28%', right: '6%'  },
  ],
}

function MiniCarteTarot({ cp }) {
  return (
    <div className="pli-carte-item">
      <CardImage carte={cp.carte} largeur={42} />
      <div className="mini-carte-pseudo">{cp.pseudo}</div>
    </div>
  )
}

// Tri des cartes Tarot : Atouts d'abord (1→21+Excuse), puis couleurs par groupe et force
const ORDRE_COULEUR_TAROT = ['Atout', 'Pique', 'Coeur', 'Carreau', 'Trefle']
const ORDRE_VALEUR_TAROT_COULEUR = ['Valet', 'Cavalier', 'Dame', 'Roi', 'As', '2', '3', '4', '5', '6', '7', '8', '9', '10']
const ATOUT_VALS = ['Excuse', '1','2','3','4','5','6','7','8','9','10','11','12','13','14','15','16','17','18','19','20','21']

function trierCartesTarot(cartes) {
  return [...cartes].sort((a, b) => {
    const ci = ORDRE_COULEUR_TAROT.indexOf(a.couleur) - ORDRE_COULEUR_TAROT.indexOf(b.couleur)
    if (ci !== 0) return ci
    if (a.couleur === 'Atout') return ATOUT_VALS.indexOf(a.valeur) - ATOUT_VALS.indexOf(b.valeur)
    return ORDRE_VALEUR_TAROT_COULEUR.indexOf(a.valeur) - ORDRE_VALEUR_TAROT_COULEUR.indexOf(b.valeur)
  })
}

function MainTarot({ cartes, monTour, statut, onJouer }) {
  if (!cartes || cartes.length === 0) return null
  const jouable = monTour && statut === 'EN_JEU'
  const cartesTriees = trierCartesTarot(cartes)
  return (
    <div className="hand-zone">
      <div className="hand-cartes">
        {cartesTriees.map(c => (
          <button
            key={c.id}
            className={`carte-svg ${jouable ? 'jouable' : 'inactive'}`}
            onClick={() => jouable && onJouer(c.id)}
            disabled={!jouable}
            title={`${c.valeur} ${c.couleur !== 'Atout' ? SUIT_SYMBOLS[c.couleur] : ''}`}
          >
            <CardImage carte={c} largeur={70} />
          </button>
        ))}
      </div>
    </div>
  )
}

export default function TarotGamePage() {
  const { id: partieId } = useParams()
  const { utilisateur, token, logout } = useAuth()
  const navigate = useNavigate()
  const { connecter, deconnecter } = useWebSocket()

  const [etatJeu, setEtatJeu] = useState(null)
  const [joueurs, setJoueurs] = useState([])
  const [messages, setMessages] = useState([])
  const [flash, setFlash] = useState('')

  const pendingEtatRef = useRef(null)
  const bufferTimerRef = useRef(null)

  const afficherFlash = (msg) => { setFlash(msg); setTimeout(() => setFlash(''), 3000) }

  const chargerEtat = useCallback(async () => {
    if (!utilisateur) return
    const etat = await api.fetchEtatJeuTarot(token, partieId, utilisateur.id)
    if (etat) setEtatJeu(etat)
  }, [token, partieId, utilisateur])

  const chargerJoueurs = useCallback(async () => {
    setJoueurs(await api.fetchJoueurs(token, partieId))
  }, [token, partieId])

  const handleEtatJeu = useCallback((nouvelEtat) => {
    setEtatJeu(prev => {
      if (!prev) return nouvelEtat
      const prevPli = prev.pliCourant || []
      const newPli = nouvelEtat.pliCourant || []
      const pliVientDeFinir = prevPli.length > 0 && newPli.length === 0
          && prev.statut === 'EN_JEU' && nouvelEtat.statut !== 'TERMINEE'
      if (pliVientDeFinir) {
        if (bufferTimerRef.current) clearTimeout(bufferTimerRef.current)
        pendingEtatRef.current = nouvelEtat
        bufferTimerRef.current = setTimeout(() => {
          if (pendingEtatRef.current) { setEtatJeu(pendingEtatRef.current); pendingEtatRef.current = null }
          bufferTimerRef.current = null
        }, 2000)
        return prev
      }
      if (pendingEtatRef.current) { pendingEtatRef.current = nouvelEtat; return prev }
      return nouvelEtat
    })
  }, [])

  useEffect(() => {
    if (!utilisateur) { navigate('/'); return }
    chargerEtat()
    chargerJoueurs()
    api.fetchHistoriqueChat(token, partieId).then(setMessages)
    connecter(partieId, utilisateur.id, {
      onEtatJeu: handleEtatJeu,
      onJoueurRejoint: () => chargerJoueurs(),
      onChat: (msg) => setMessages(prev => [...prev, msg])
    })
    return () => {
      deconnecter()
      if (bufferTimerRef.current) clearTimeout(bufferTimerRef.current)
    }
  }, [partieId, utilisateur])

  const handleEnchere = async (typeBid) => {
    const { ok, data } = await api.enchirirTarot(token, partieId, utilisateur.id, typeBid)
    if (ok) setEtatJeu(data)
    else afficherFlash(data?.erreur || "Erreur d'enchère.")
  }

  const handleAppelerRoi = async (couleur) => {
    const { ok, data } = await api.appelerRoiTarot(token, partieId, utilisateur.id, couleur)
    if (ok) setEtatJeu(data)
    else afficherFlash(data?.erreur || "Erreur lors de l'appel du Roi.")
  }

  const handleEcarter = async (carteIds) => {
    const { ok, data } = await api.ecarterCartes(token, partieId, utilisateur.id, carteIds)
    if (ok) setEtatJeu(data)
    else afficherFlash(data?.erreur || "Erreur lors de l'écart.")
  }

  const handleJouerCarte = async (carteId) => {
    const { ok, data } = await api.jouerCarteTarot(token, partieId, utilisateur.id, carteId)
    if (ok) setEtatJeu(data)
    else afficherFlash(data?.erreur || 'Impossible de jouer cette carte.')
  }

  const handleEnvoyerMessage = async (contenu) => {
    await api.envoyerMessage(token, partieId, utilisateur.id, contenu)
  }

  const handleDeclarePoignee = async (type) => {
    const { ok, data } = await api.declarePoigneeTarot(token, partieId, utilisateur.id, type)
    if (ok) setEtatJeu(data)
    else afficherFlash(data?.erreur || 'Impossible de déclarer la Poignée.')
  }

  const retourLobby = () => { deconnecter(); navigate('/lobby') }
  const deconnexion = () => { deconnecter(); logout(); navigate('/') }

  if (!etatJeu) {
    return (
      <div className="app" style={{ alignItems: 'center', justifyContent: 'center' }}>
        <p style={{ color: 'var(--text-muted)', fontSize: '1.1rem' }}>Chargement de la partie Tarot…</p>
      </div>
    )
  }

  const monTour = etatJeu.tourJoueurId === etatJeu.monJoueurId
  const phase = etatJeu.phaseJeu
  const statut = etatJeu.statut

  // Score preneur en points réels (÷2)
  const pointsPreneur = etatJeu.pointsPreneurX2 != null
    ? (etatJeu.pointsPreneurX2 / 2).toFixed(1)
    : '—'

  // Badge rôle du joueur
  const roleBadge = () => {
    if (etatJeu.estPreneur) return ' (preneur)'
    if (etatJeu.estPartenaire) return ' (partenaire)'
    if (etatJeu.monEquipe === 2) return ' (défenseur)'
    return ''
  }

  return (
    <div className="app game-app">
      <header className="header">
        <div className="header-left">
          <span className="logo-mini">★</span>
          <span className="header-title">Tarot #{partieId}</span>
          {etatJeu.enchereType && (
            <span className="atout-badge">
              {BID_LABELS[etatJeu.enchereType]} ×{etatJeu.multiplicateur}
              {etatJeu.estPreneur && ' (vous prenez)'}
            </span>
          )}
          {etatJeu.appelRoi && statut !== 'EN_ENCHERE' && (
            <span className="atout-badge" style={{ background: 'var(--accent-muted)' }}>
              Roi de {etatJeu.appelRoi}
              {etatJeu.pseudoPartenaire && ` — partenaire : ${etatJeu.pseudoPartenaire}`}
            </span>
          )}
        </div>
        <div className="header-center">
          {etatJeu.maxDonnes > 0 && (
            <span className="atout-badge">Manche {etatJeu.donneActuelle}/{etatJeu.maxDonnes}</span>
          )}
          {statut === 'EN_JEU' && (
            <>
              <span className="score-badge">
                Preneur : {pointsPreneur} pts ({etatJeu.boutsPreneur} bout{etatJeu.boutsPreneur !== 1 ? 's' : ''})
              </span>
              <span className="score-sep">|</span>
              <span className="score-badge">Seuil : {etatJeu.seuilCourant}</span>
            </>
          )}
        </div>
        <div className="user-info">
          <span className="user-badge">
            {utilisateur?.pseudo}{roleBadge()}
          </span>
          <button className="btn-small btn-outline" onClick={retourLobby}>← Lobby</button>
          <button className="btn-small btn-outline" onClick={deconnexion}>Déco</button>
        </div>
      </header>

      {flash && <div className="flash">{flash}</div>}

      <div className="game-main">
        <div className="table-zone">
          {/* Table avec joueurs positionnés autour */}
          <div className="table-feutre tarot-table">
            {/* Joueurs autour de la table */}
            {(() => {
              const nbJ = joueurs.length
              const myPos = joueurs.find(j => j.id === etatJeu.monJoueurId)?.position ?? 0
              const seats = SEAT_POSITIONS[nbJ] || SEAT_POSITIONS[4]
              return joueurs.map(j => {
                const offset = (j.position - myPos + nbJ) % nbJ
                const seatStyle = seats[offset] || {}
                const estActif = j.id === etatJeu.tourJoueurId
                return (
                  <div
                    key={j.id}
                    className={`joueur-info-tarot ${estActif ? 'joueur-actif-tarot' : ''}`}
                    style={seatStyle}
                  >
                    <span className="joueur-nom">{j.pseudo}</span>
                    {estActif && <span className="tour-indicator-mini">▶</span>}
                  </div>
                )
              })
            })()}

            {/* Centre : pli courant */}
            <div className="pli-centre">
              {statut === 'EN_JEU' && (
                <>
                  <div className="pli-titre">Pli {etatJeu.numPliCourant}</div>
                  <div className="pli-cartes-table">
                    {(!etatJeu.pliCourant || etatJeu.pliCourant.length === 0)
                      ? <span className="pli-vide">—</span>
                      : etatJeu.pliCourant.map((cp, i) => <MiniCarteTarot key={i} cp={cp} />)
                    }
                  </div>
                  {etatJeu.tourJoueurId === etatJeu.monJoueurId && !etatJeu.pliCourant?.length && (
                    <p className="attente" style={{ marginTop: 6 }}>Jouez une carte !</p>
                  )}
                  {etatJeu.tourJoueurId !== etatJeu.monJoueurId && (
                    <p className="attente">Tour de <strong>{etatJeu.tourPseudo}</strong></p>
                  )}
                </>
              )}
            </div>

            {/* Dernier pli */}
            {etatJeu.dernierPli?.length > 0 && statut === 'EN_JEU' && (
              <div className="dernier-pli-zone">
                <div className="dernier-pli-titre">Pli précédent — Éq.{etatJeu.dernierPliGagnantEquipe}</div>
                <div className="dernier-pli-cartes">
                  {etatJeu.dernierPli.map((cp, i) => <MiniCarteTarot key={i} cp={cp} />)}
                </div>
              </div>
            )}
          </div>

          {/* Overlay : enchères Tarot */}
          {statut === 'EN_ENCHERE' && phase == null && (
            <div className="enchere-overlay">
              <TarotBiddingPanel etatJeu={etatJeu} monTour={monTour} onEncherir={handleEnchere} />
            </div>
          )}

          {/* Overlay : appel du Roi (5 joueurs) */}
          {statut === 'EN_ENCHERE' && phase === 'APPEL_ROI' && (
            <RoiSelector etatJeu={etatJeu} onAppelerRoi={handleAppelerRoi} />
          )}

          {/* Overlay : chien / écart */}
          {statut === 'EN_ENCHERE' && (phase === 'CHIEN' || phase === 'CHIEN_VU') && (
            <ChienPanel etatJeu={etatJeu} onEcarter={handleEcarter} />
          )}

          {/* Overlay : Poignée (preneur peut déclarer avant le 1er pli) */}
          {statut === 'EN_JEU' && etatJeu.numPliCourant <= 1 && etatJeu.estPreneur && !etatJeu.poigneeDeclaree && (
            <div style={{
              position: 'absolute', top: 8, left: '50%', transform: 'translateX(-50%)',
              background: 'rgba(0,0,0,0.7)', borderRadius: 10, padding: '8px 14px',
              display: 'flex', gap: 8, alignItems: 'center', zIndex: 10, flexWrap: 'wrap'
            }}>
              <span style={{ color: '#ccc', fontSize: '0.85rem' }}>Poignée ?</span>
              {['SIMPLE', 'DOUBLE', 'TRIPLE'].map(t => (
                <button key={t} className="btn-small btn-invite"
                  onClick={() => handleDeclarePoignee(t)}
                  title={t === 'SIMPLE' ? '+20 pts' : t === 'DOUBLE' ? '+30 pts' : '+40 pts'}>
                  {t === 'SIMPLE' ? 'Simple (+20)' : t === 'DOUBLE' ? 'Double (+30)' : 'Triple (+40)'}
                </button>
              ))}
            </div>
          )}
          {etatJeu.poigneeDeclaree && statut === 'EN_JEU' && (
            <div style={{ position: 'absolute', top: 8, left: '50%', transform: 'translateX(-50%)',
              background: 'rgba(180,120,0,0.8)', borderRadius: 8, padding: '4px 12px', zIndex: 10 }}>
              Poignée {etatJeu.poigneeDeclaree} déclarée ! ({etatJeu.poigneeDeclaree === 'SIMPLE' ? '20' : etatJeu.poigneeDeclaree === 'DOUBLE' ? '30' : '40'} pts bonus)
            </div>
          )}

          {/* Overlay : résultat */}
          {statut === 'TERMINEE' && etatJeu.resultat && (
            <div className="resultat-overlay">
              <div className="resultat-centre">
                <h3>Partie Tarot terminée !</h3>
                <p>
                  <strong>{etatJeu.resultat.pseudoPreneur}</strong> joue en{' '}
                  {BID_LABELS[etatJeu.resultat.enchereType]} ×{etatJeu.resultat.multiplicateur}
                </p>
                {etatJeu.resultat.pseudoPartenaire && (
                  <p>Partenaire : <strong>{etatJeu.resultat.pseudoPartenaire}</strong></p>
                )}
                <p>
                  Points : {(etatJeu.resultat.pointsPreneurX2 / 2).toFixed(1)} / {etatJeu.resultat.seuil}
                  {' '}({etatJeu.resultat.boutsPreneur} bout{etatJeu.resultat.boutsPreneur !== 1 ? 's' : ''})
                </p>
                {etatJeu.resultat.petitAuBout && <p>✨ Petit au bout ! (+{etatJeu.resultat.multiplicateur * 10} pts)</p>}
                <p>Contrat {etatJeu.resultat.contratRempli ? '✓ rempli' : '✗ chuté'}</p>
                <p>Score : {etatJeu.resultat.contratRempli ? '+' : '−'}{etatJeu.resultat.scorePartie} pts</p>
                <p><strong>Vainqueur : Équipe {etatJeu.resultat.gagnantEquipe}</strong></p>
                {etatJeu.maxDonnes > 0 && (
                  <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>
                    Donnes : {etatJeu.donneActuelle}/{etatJeu.maxDonnes}
                  </p>
                )}
                <button className="btn-primary" onClick={retourLobby}>Retour au lobby</button>
              </div>
            </div>
          )}

          {/* Ma main */}
          <MainTarot
            cartes={etatJeu.maMain}
            monTour={monTour}
            statut={statut}
            onJouer={handleJouerCarte}
          />
        </div>

        <ChatPanel
          messages={messages}
          moPseudo={utilisateur?.pseudo}
          onEnvoyer={handleEnvoyerMessage}
        />
      </div>
    </div>
  )
}
