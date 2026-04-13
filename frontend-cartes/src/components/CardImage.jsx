/**
 * Affiche une carte à jouer :
 *   - Cartes Coinche (32 cartes) → sprite SVG via svg-cards
 *   - Cartes Tarot couleur + Cavalier → sprite SVG (Cavalier = jack avec overlay "Ca")
 *   - Atouts Tarot 1-21 + Excuse → TrumpCard (rendu CSS)
 *   - Dos de carte → svg-cards#back
 *
 * SVG fragment IDs : "{suit}_{value}" (ex: heart_1, spade_jack, club_10)
 */
import TrumpCard from './TrumpCard'

const SUIT_MAP = {
  Coeur: 'heart',
  Carreau: 'diamond',
  Trefle: 'club',
  Pique: 'spade',
}

const VALUE_MAP = {
  As: '1',
  Valet: 'jack',
  Dame: 'queen',
  Roi: 'king',
  Cavalier: 'jack', // nearest SVG available; we'll overlay "Ca"
}

const SYMBOLES = { Coeur: '♥', Carreau: '♦', Trefle: '♣', Pique: '♠' }

function carteToSvgId(carte) {
  const suit = SUIT_MAP[carte.couleur]
  if (!suit) return null
  const value = VALUE_MAP[carte.valeur] ?? carte.valeur.toLowerCase()
  return `${suit}_${value}`
}

/**
 * @param {{ carte?: {valeur:string, couleur:string}, largeur?: number, dos?: boolean }} props
 */
export default function CardImage({ carte, largeur = 70, dos = false }) {
  const hauteur = Math.round(largeur * (244.64 / 169.075))

  // --- Dos ---
  if (dos) {
    return (
      <svg
        width={largeur} height={hauteur}
        viewBox="0 0 169.075 244.640"
        style={{ display: 'block' }}
        aria-label="Dos de carte"
      >
        <use href="/svg-cards.svg#back" />
      </svg>
    )
  }

  if (!carte) return null

  // --- Atouts Tarot (1-21) et Excuse ---
  if (carte.couleur === 'Atout') {
    return <TrumpCard valeur={carte.valeur} largeur={largeur} />
  }

  const svgId = carteToSvgId(carte)

  if (!svgId) {
    // Fallback texte pour cartes non reconnues
    const sym = SYMBOLES[carte.couleur] ?? ''
    return (
      <div style={{
        width: largeur, height: hauteur,
        border: '1px solid #aaa', borderRadius: 6,
        display: 'flex', flexDirection: 'column',
        alignItems: 'center', justifyContent: 'center',
        fontSize: largeur * 0.22, background: '#fff',
        color: ['Coeur', 'Carreau'].includes(carte.couleur) ? '#c00' : '#111',
        userSelect: 'none',
      }}>
        <span>{carte.valeur}</span>
        <span>{sym}</span>
      </div>
    )
  }

  // --- Cavalier Tarot : jack SVG + badge "Ca" overlay ---
  const isCavalier = carte.valeur === 'Cavalier'

  return (
    <div style={{ position: 'relative', width: largeur, height: hauteur, display: 'inline-block' }}>
      <svg
        width={largeur} height={hauteur}
        viewBox="0 0 169.075 244.640"
        style={{ display: 'block' }}
        aria-label={`${carte.valeur} de ${carte.couleur}`}
      >
        <use href={`/svg-cards.svg#${svgId}`} />
      </svg>
      {isCavalier && (
        <span style={{
          position: 'absolute', top: Math.round(largeur * 0.04), right: Math.round(largeur * 0.06),
          background: 'rgba(0,0,100,0.75)', color: '#fff',
          fontSize: Math.round(largeur * 0.14), fontWeight: 'bold',
          borderRadius: 3, padding: '1px 3px',
          lineHeight: 1.2, pointerEvents: 'none',
        }}>Ca</span>
      )}
    </div>
  )
}
