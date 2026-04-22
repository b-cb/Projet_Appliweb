import { useState, useEffect, useCallback, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/AuthContext'
import { useWebSocket } from '../hooks/useWebSocket'
import * as api from '../services/api'
import PlayerTable from '../components/PlayerTable'
import HandCards from '../components/HandCards'
import BiddingPanel from '../components/BiddingPanel'
import ChatPanel from '../components/ChatPanel'
import TarotGamePage from './TarotGamePage'

const SYMBOLES = { Coeur: '♥', Carreau: '♦', Trefle: '♣', Pique: '♠' }

export default function GamePage() {
  const { id: partieId } = useParams()
  const { token } = useAuth()
  const [typeJeu, setTypeJeu] = useState(null)

  useEffect(() => {
    api.fetchPartie(token, partieId).then(p => {
      if (p) setTypeJeu(p.typeJeu || 'COINCHE')
    })
  }, [partieId, token])

  if (typeJeu === 'TAROT') return <TarotGamePage />
  if (typeJeu === null) return (
    <div className="app" style={{ alignItems: 'center', justifyContent: 'center' }}>
      <p style={{ color: 'var(--text-muted)' }}>Chargement…</p>
    </div>
  )

  return <CoinchePage />
}

function CoinchePage() {
  const { id: partieId } = useParams()
  const { utilisateur, token, logout } = useAuth()
  const navigate = useNavigate()
  const { connecter, deconnecter } = useWebSocket()

  const [etatJeu, setEtatJeu] = useState(null)
  const [joueurs, setJoueurs] = useState([])
  const [messages, setMessages] = useState([])
  const [flash, setFlash] = useState('')

  // Buffer pour afficher le pli complet avant de passer au suivant
  const pendingEtatRef = useRef(null)
  const bufferTimerRef = useRef(null)

  const afficherFlash = (msg) => { setFlash(msg); setTimeout(() => setFlash(''), 3000) }

  const chargerEtat = useCallback(async () => {
    if (!utilisateur) return
    const etat = await api.fetchEtatJeu(token, partieId, utilisateur.id)
    if (etat) setEtatJeu(etat)
  }, [token, partieId, utilisateur])

  const chargerJoueurs = useCallback(async () => {
    setJoueurs(await api.fetchJoueurs(token, partieId))
  }, [token, partieId])

  // Traitement intelligent des mises à jour WebSocket
  const handleEtatJeu = useCallback((nouvelEtat) => {
    setEtatJeu(prev => {
      if (!prev) return nouvelEtat

      // Détecter un changement de pli : le pli précédent avait des cartes, le nouveau est vide
      const prevPli = prev.pliCourant || []
      const newPli = nouvelEtat.pliCourant || []
      const pliVientDeFinir = prevPli.length > 0 && newPli.length === 0
          && prev.statut === 'EN_JEU' && nouvelEtat.statut !== 'TERMINEE'

      if (pliVientDeFinir) {
        // Annuler tout timer en cours
        if (bufferTimerRef.current) clearTimeout(bufferTimerRef.current)

        // Stocker le nouvel état pour plus tard
        pendingEtatRef.current = nouvelEtat

        // Afficher le pli complet temporairement (garder l'ancien état)
        bufferTimerRef.current = setTimeout(() => {
          if (pendingEtatRef.current) {
            setEtatJeu(pendingEtatRef.current)
            pendingEtatRef.current = null
          }
          bufferTimerRef.current = null
        }, 2000)

        return prev // Garder l'ancien état (pli complet visible)
      }

      // Si un état bufférisé est en attente et qu'un nouvel événement arrive, le remplacer
      if (pendingEtatRef.current) {
        pendingEtatRef.current = nouvelEtat
        return prev
      }

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

  const handleEnchere = async (body) => {
    const { ok, data } = await api.encherir(token, partieId, utilisateur.id, body)
    if (ok) setEtatJeu(data)
    else afficherFlash(data?.erreur || "Erreur d'enchère.")
  }

  const handleCoincher = async (surcoinche) => {
    const { ok, data } = await api.coincher(token, partieId, utilisateur.id, surcoinche)
    if (ok) setEtatJeu(data)
    else afficherFlash(data?.erreur || 'Impossible de coincher.')
  }

  const handleJouerCarte = async (carteId) => {
    const { ok, data } = await api.jouerCarte(token, partieId, utilisateur.id, carteId)
    if (ok) setEtatJeu(data)
    else afficherFlash(data?.erreur || 'Impossible de jouer cette carte.')
  }

  const handleEnvoyerMessage = async (contenu) => {
    await api.envoyerMessage(token, partieId, utilisateur.id, contenu)
  }

  const retourLobby = () => { deconnecter(); navigate('/lobby') }
  const deconnexion = () => { deconnecter(); logout(); navigate('/') }

  if (!etatJeu) {
    return (
      <div className="app" style={{ alignItems: 'center', justifyContent: 'center' }}>
        <p style={{ color: 'var(--text-muted)', fontSize: '1.1rem' }}>Chargement de la partie…</p>
      </div>
    )
  }

  const monTour = etatJeu.tourJoueurId === etatJeu.monJoueurId

  return (
    <div className="app game-app">
      <header className="header">
        <div className="header-left">
          <span className="logo-mini">♠♥</span>
          <span className="header-title">Partie #{partieId}</span>
          {etatJeu.atout && (
            <span className="atout-badge">
              Atout : {SYMBOLES[etatJeu.atout] || etatJeu.atout} {etatJeu.atout}
              {etatJeu.contratValeur > 0 && ` — Contrat ${etatJeu.contratValeur}`}
            </span>
          )}
        </div>
        <div className="header-center">
          {etatJeu.maxDonnes > 0 && (
            <span className="atout-badge">Manche {etatJeu.donneActuelle}/{etatJeu.maxDonnes}</span>
          )}
          {etatJeu.maxPoints > 0 && (
            <span className="atout-badge">Objectif : {etatJeu.maxPoints} pts</span>
          )}
          {/* Score donne en cours */}
          <span className="score-badge" title="Points de la donne en cours">
            Éq.1 : {etatJeu.scoreA} pts
          </span>
          <span className="score-sep">|</span>
          <span className="score-badge" title="Points de la donne en cours">
            Éq.2 : {etatJeu.scoreB} pts
          </span>
          {/* Total multi-donnes */}
          {(etatJeu.maxDonnes > 0 || etatJeu.maxPoints > 0) && (
            <>
              <span className="score-sep" style={{ margin: '0 4px' }}>·</span>
              <span className="score-badge" style={{ opacity: 0.75, fontSize: '0.8rem' }}>
                Total : {etatJeu.scoreGlobalA}/{etatJeu.scoreGlobalB}
              </span>
            </>
          )}
        </div>
        <div className="user-info">
          <span className="user-badge">{utilisateur?.pseudo} · Équipe {etatJeu.monEquipe}</span>
          <button className="btn-small btn-outline" onClick={retourLobby}>← Lobby</button>
          <button className="btn-small btn-outline" onClick={deconnexion}>Déco</button>
        </div>
      </header>

      {flash && <div className="flash">{flash}</div>}

      <div className="game-main">
        <div className="table-zone">
          {/* Table avec les joueurs */}
          {joueurs.length === 4
            ? <PlayerTable etatJeu={etatJeu} joueurs={joueurs} utilisateur={utilisateur} />
            : (
              <div className="table-feutre" style={{ alignItems: 'center', justifyContent: 'center' }}>
                <p style={{ color: 'rgba(255,255,255,0.4)' }}>Chargement des joueurs…</p>
              </div>
            )
          }

          {/* Panel enchères (superposé sur le centre si EN_ENCHERE) */}
          {etatJeu.statut === 'EN_ENCHERE' && (
            <div className="enchere-overlay">
              <BiddingPanel etatJeu={etatJeu} monTour={monTour} onEncherir={handleEnchere} onCoincher={handleCoincher} />
            </div>
          )}

          {/* Résultat */}
          {etatJeu.statut === 'TERMINEE' && etatJeu.resultat && (
            <div className="resultat-overlay">
              <div className="resultat-centre">
                <h3>Partie terminée !</h3>
                <p>
                  Contrat {etatJeu.resultat.contratValeur} {SYMBOLES[etatJeu.resultat.contratCouleur]}
                  {' '}par {etatJeu.resultat.pseudoPreneur}
                </p>
                <p>Contrat {etatJeu.resultat.contratRempli ? '✓ rempli' : '✗ chuté'}</p>
                <p><strong>Vainqueur final : Équipe {etatJeu.resultat.gagnantEquipe}</strong></p>
                <p>Éq.1 {etatJeu.scoreGlobalA} pts — Éq.2 {etatJeu.scoreGlobalB} pts (total)</p>
                <button className="btn-primary" onClick={retourLobby}>Retour au lobby</button>
              </div>
            </div>
          )}

          {/* Transition entre deux donnes */}
          {etatJeu.statut === 'EN_ENCHERE' && etatJeu.donneActuelle > 1 && etatJeu.scoreGlobalA === 0 && etatJeu.scoreGlobalB === 0 && null}
          {etatJeu.statut === 'EN_ENCHERE' && etatJeu.donneActuelle > 1 && (etatJeu.scoreGlobalA > 0 || etatJeu.scoreGlobalB > 0) && (
            <div className="resultat-overlay" style={{ pointerEvents: 'none', opacity: 0 }} />
          )}

          {/* Ma main */}
          <HandCards
            cartes={etatJeu.maMain}
            monTour={monTour}
            statut={etatJeu.statut}
            onJouer={handleJouerCarte}
          />
        </div>

        {/* Chat */}
        <ChatPanel
          messages={messages}
          moPseudo={utilisateur?.pseudo}
          onEnvoyer={handleEnvoyerMessage}
        />
      </div>
    </div>
  )
}
