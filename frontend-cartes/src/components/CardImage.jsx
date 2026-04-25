/**
 * Affiche une carte à jouer.
 *
 * Sources d'images :
 *   - Dos                        → svg-cards.svg#back
 *   - Atouts 0-21 (Excuse+1-21)  → /tarot/atout_NN.png  (inchangé)
 *   - Cavalier (12)              → /tarot/cavalier_{couleur}.png (généré)
 *   - Autres cartes de couleur   → svg-cards.svg#<suit>_<val>
 *     (As=1, 2-10, Valet=jack, Dame=queen, Roi=king)
 *
 * Le sprite svg-cards a un viewBox de 169.075 × 244.640.
 */

// Ratio du sprite svg-cards
const SVG_W = 169.075
const SVG_H = 244.640

// Mapping couleur Tarot → nom de couleur dans le sprite
const SUIT_SVG = {
  Coeur:   'heart',
  Carreau: 'diamond',
  Pique:   'spade',
  Trefle:  'club',
}

// Mapping valeur → suffixe dans le sprite
// As = "1" dans le sprite (ex: heart_1)
const VALEUR_SVG = {
  As:      '1',
  Valet:   'jack',
  Dame:    'queen',
  Roi:     'king',
  // 2-10 : la valeur numérique est utilisée directement
}

// Pour les cavaliers, on utilise les images d'origine du deck tarot
const CAVALIER_PNG = {
  Coeur:   '/tarot/coeur_12.png',
  Carreau: '/tarot/carreau_12.png',
  Pique:   '/tarot/pique_12.png',
  Trefle:  '/tarot/trefle_12.png',
}

// Atouts PNG (inchangés)
function atoutPath(valeur) {
  const n = valeur === 'Excuse' ? '00' : String(valeur).padStart(2, '0')
  return `/tarot/atout_${n}.png`
}

/**
 * @param {{ carte?: {valeur:string, couleur:string}, largeur?: number, dos?: boolean }} props
 */
export default function CardImage({ carte, largeur = 70, dos = false }) {
  const hauteur = Math.round(largeur * (SVG_H / SVG_W))

  // --- Dos de carte ---
  if (dos) {
    return (
      <svg
        width={largeur} height={hauteur}
        viewBox={`0 0 ${SVG_W} ${SVG_H}`}
        style={{ display: 'block' }}
        aria-label="Dos de carte"
      >
        <use href="/svg-cards.svg#back" />
      </svg>
    )
  }

  if (!carte) return null

  // --- Atouts : PNG inchangés ---
  if (carte.couleur === 'Atout') {
    return (
      <img
        src={atoutPath(carte.valeur)}
        alt={`Atout ${carte.valeur}`}
        width={largeur}
        height={hauteur}
        style={{ display: 'block', borderRadius: 4 }}
        draggable={false}
      />
    )
  }

  // --- Cavaliers : PNG générés ---
  if (carte.valeur === 'Cavalier') {
    const src = CAVALIER_PNG[carte.couleur]
    return src ? (
      <img
        src={src}
        alt={`Cavalier de ${carte.couleur}`}
        width={largeur}
        height={hauteur}
        style={{ display: 'block', borderRadius: 4 }}
        draggable={false}
      />
    ) : null
  }

  // --- Cartes de couleur : SVG sprite ---
  const suit = SUIT_SVG[carte.couleur]
  if (!suit) return null

  const valSuffix = VALEUR_SVG[carte.valeur] ?? String(carte.valeur)
  const spriteId = `${suit}_${valSuffix}`

  return (
    <svg
      width={largeur}
      height={hauteur}
      viewBox={`0 0 ${SVG_W} ${SVG_H}`}
      style={{ display: 'block', borderRadius: 4 }}
      aria-label={`${carte.valeur} de ${carte.couleur}`}
    >
      <use href={`/svg-cards.svg#${spriteId}`} />
    </svg>
  )
}
