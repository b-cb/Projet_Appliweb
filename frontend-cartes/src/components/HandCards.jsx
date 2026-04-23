import CardImage from './CardImage'

// Ordre de tri par couleur (groupe): Pique, Coeur, Carreau, Trèfle
// Puis dans chaque couleur, par force croissante
const ORDER_COULEUR = ['Pique', 'Coeur', 'Carreau', 'Trefle']
const ORDER_VALEUR = ['7', '8', '9', 'Valet', 'Dame', 'Roi', '10', 'As']

function trierCartes(cartes) {
  return [...cartes].sort((a, b) => {
    const ci = ORDER_COULEUR.indexOf(a.couleur) - ORDER_COULEUR.indexOf(b.couleur)
    if (ci !== 0) return ci
    return ORDER_VALEUR.indexOf(a.valeur) - ORDER_VALEUR.indexOf(b.valeur)
  })
}

function Carte({ carte, jouable, enEnchere, onClick }) {
  const cssClass = enEnchere ? 'carte-svg' : `carte-svg ${jouable ? 'jouable' : 'inactive'}`
  return (
    <button
      className={cssClass}
      onClick={jouable ? onClick : undefined}
      disabled={!jouable}
      title={`${carte.valeur} de ${carte.couleur}`}
      aria-label={`${carte.valeur} de ${carte.couleur}`}
    >
      <CardImage carte={carte} largeur={100} />
    </button>
  )
}

export default function HandCards({ cartes, monTour, statut, onJouer }) {
  if (!cartes || cartes.length === 0) return null

  const jouable = monTour && statut === 'EN_JEU'
  const enEnchere = statut === 'EN_ENCHERE'
  const cartesTriees = trierCartes(cartes)

  return (
    <div className="main-zone">
      <div className="main-label">
        Mes cartes ({cartes.length})
        {statut === 'EN_ENCHERE' && <span className="main-label-info"> — phase d'enchères</span>}
        {statut === 'EN_JEU' && !monTour && <span className="main-label-info"> — attente de votre tour</span>}
      </div>
      <div className="main-cartes">
        {cartesTriees.map(carte => (
          <Carte
            key={carte.id}
            carte={carte}
            jouable={jouable}
            enEnchere={enEnchere}
            onClick={() => onJouer(carte.id)}
          />
        ))}
      </div>
    </div>
  )
}
