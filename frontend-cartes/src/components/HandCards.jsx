const SYMBOLES = { Coeur: '♥', Carreau: '♦', Trefle: '♣', Pique: '♠' }
const COULEUR_CSS = { Coeur: 'rouge', Carreau: 'rouge', Trefle: 'noir', Pique: 'noir' }

function Carte({ carte, jouable, onClick }) {
  const sym = SYMBOLES[carte.couleur] || carte.couleur
  const cls = COULEUR_CSS[carte.couleur] || 'noir'
  return (
    <button
      className={`carte ${cls} ${jouable ? 'jouable' : 'inactive'}`}
      onClick={jouable ? onClick : undefined}
      disabled={!jouable}
      title={`${carte.valeur} de ${carte.couleur}`}
    >
      <span className="carte-val-top">{carte.valeur}</span>
      <span className="carte-sym">{sym}</span>
      <span className="carte-val-bot">{carte.valeur}</span>
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
