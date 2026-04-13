import CardImage from './CardImage'

function Carte({ carte, jouable, onClick }) {
  return (
    <button
      className={`carte-svg ${jouable ? 'jouable' : 'inactive'}`}
      onClick={jouable ? onClick : undefined}
      disabled={!jouable}
      title={`${carte.valeur} de ${carte.couleur}`}
      aria-label={`${carte.valeur} de ${carte.couleur}`}
    >
      <CardImage carte={carte} largeur={70} />
    </button>
  )
}

export default function HandCards({ cartes, monTour, statut, onJouer }) {
  if (!cartes || cartes.length === 0) return null

  const jouable = monTour && statut === 'EN_JEU'

  return (
    <div className="main-zone">
      <div className="main-label">
        Mes cartes ({cartes.length})
        {statut === 'EN_ENCHERE' && <span className="main-label-info"> — phase d'enchères</span>}
        {statut === 'EN_JEU' && !monTour && <span className="main-label-info"> — attente de votre tour</span>}
      </div>
      <div className="main-cartes">
        {cartes.map(carte => (
          <Carte
            key={carte.id}
            carte={carte}
            jouable={jouable}
            onClick={() => onJouer(carte.id)}
          />
        ))}
      </div>
    </div>
  )
}
