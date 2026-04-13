/**
 * Sélecteur du Roi appelé — phase APPEL_ROI du Tarot 5 joueurs.
 * Le preneur choisit une couleur de Roi qu'il ne détient pas en main.
 * En pratique, on affiche les 4 couleurs et on laisse le serveur valider.
 */

const COULEURS = [
  { couleur: 'Coeur',   label: 'Roi ♥ Cœur',    classe: 'roi-coeur'   },
  { couleur: 'Carreau', label: 'Roi ♦ Carreau',  classe: 'roi-carreau' },
  { couleur: 'Trefle',  label: 'Roi ♣ Trèfle',   classe: 'roi-trefle'  },
  { couleur: 'Pique',   label: 'Roi ♠ Pique',    classe: 'roi-pique'   },
]

export default function RoiSelector({ etatJeu, onAppelerRoi }) {
  const estPreneur = etatJeu.estPreneur
  const maMain = etatJeu.maMain || []

  // Couleurs dont le preneur détient déjà le Roi (on les grise)
  const roisDansMain = new Set(
    maMain.filter(c => c.valeur === 'Roi').map(c => c.couleur)
  )

  return (
    <div className="enchere-overlay">
      <div className="enchere-centre roi-selector">
        <div className="enchere-titre">Appel du Roi</div>

        {estPreneur ? (
          <>
            <p className="roi-instruction">
              Choisissez un Roi que vous n'avez pas en main.<br />
              Le joueur qui le détient sera votre partenaire.
            </p>
            <div className="roi-boutons">
              {COULEURS.map(({ couleur, label, classe }) => {
                const dejaEnMain = roisDansMain.has(couleur)
                return (
                  <button
                    key={couleur}
                    className={`btn-primary roi-btn ${classe} ${dejaEnMain ? 'roi-btn-grise' : ''}`}
                    onClick={() => onAppelerRoi(couleur)}
                    title={dejaEnMain ? 'Vous avez déjà ce Roi en main' : undefined}
                  >
                    {label}
                    {dejaEnMain && <span className="roi-possede"> (en main)</span>}
                  </button>
                )
              })}
            </div>
          </>
        ) : (
          <p className="attente">
            En attente de l'appel du Roi par <strong>{etatJeu.tourPseudo}</strong>…
          </p>
        )}
      </div>
    </div>
  )
}
