/**
 * Affiche une carte à jouer.
 *
 * Sources d'images :
 *   - Dos                        → svg-cards.svg#back  (SVG sprite)
 *   - Atouts 1-21 + Excuse       → /tarot/atout_NN.png (CC0, itch.io)
 *   - Cartes de couleur (1-14)   → /tarot/{couleur}_NN.png
 *     As=01, 2-10, Valet=11, Cavalier=12, Dame=13, Roi=14
 *     Cups→coeur, Pentacles→carreau, Swords→pique, Wands→trefle
 *
 * Rapport largeur/hauteur réel des PNG : 456×638 ≈ 1.398
 */

// Ratio des PNG du deck (456 × 638)
const IMG_RATIO = 638 / 456

const SUIT_FR = {
  Coeur:   'coeur',
  Carreau: 'carreau',
  Pique:   'pique',
  Trefle:  'trefle',
}

const VALEUR_NUM = {
  As:       '01',
  Valet:    '11',
  Cavalier: '12',
  Dame:     '13',
  Roi:      '14',
}

function atoutPath(valeur) {
  const n = valeur === 'Excuse' ? '00' : String(valeur).padStart(2, '0')
  return `/tarot/atout_${n}.png`
}

function couleurPath(couleur, valeur) {
  const suit = SUIT_FR[couleur]
  if (!suit) return null
  const n = VALEUR_NUM[valeur] ?? String(valeur).padStart(2, '0')
  return `/tarot/${suit}_${n}.png`
}

/**
 * @param {{ carte?: {valeur:string, couleur:string}, largeur?: number, dos?: boolean }} props
 */
export default function CardImage({ carte, largeur = 70, dos = false }) {
  const hauteur = Math.round(largeur * IMG_RATIO)

  // --- Dos de carte (svg-cards, aucune image dos dans le deck) ---
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

  const src = carte.couleur === 'Atout'
    ? atoutPath(carte.valeur)
    : couleurPath(carte.couleur, carte.valeur)

  if (!src) {
    // Fallback texte pour couleur non reconnue
    return (
      <div style={{
        width: largeur, height: hauteur,
        border: '1px solid #aaa', borderRadius: 4,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        fontSize: largeur * 0.2, background: '#fff',
        userSelect: 'none',
      }}>
        {carte.valeur}
      </div>
    )
  }

  return (
    <img
      src={src}
      alt={`${carte.valeur} de ${carte.couleur}`}
      width={largeur}
      height={hauteur}
      style={{ display: 'block', borderRadius: 4 }}
      draggable={false}
    />
  )
}
