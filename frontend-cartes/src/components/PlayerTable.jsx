import CardImage from './CardImage'

function positionVisuelle(maPosition, autrePosition) {
  const delta = (autrePosition - maPosition + 4) % 4
  return ['bas', 'gauche', 'haut', 'droite'][delta]
}

function MiniCarte({ carte }) {
  return (
    <div className="mini-carte-svg">
      <CardImage carte={carte} largeur={100} />
    </div>
  )
}

export default function PlayerTable({ etatJeu, joueurs, utilisateur }) {
  const moi = joueurs.find(j => j.id === etatJeu.monJoueurId)
  const maPosition = moi?.position ?? 0
  const joueurParPos = {}
  joueurs.forEach(j => { joueurParPos[j.position] = j })

  return (
    <div className="table-feutre">
      {/* Les 3 autres joueurs */}
      {[0, 1, 2, 3].map(pos => {
        if (pos === maPosition) return null
        const j = joueurParPos[pos]
        if (!j) return null
        const visuelle = positionVisuelle(maPosition, pos)
        const estSonTour = j.id === etatJeu.tourJoueurId
        return (
          <div key={pos} className={`joueur-table joueur-${visuelle} ${estSonTour ? 'joueur-actif' : ''}`}>
            <div className="joueur-nom">{j.pseudo || `J${pos}`}</div>
            <div className={`joueur-equipe eq${j.equipe}`}>Équipe {j.equipe}</div>
            {estSonTour && <div className="tour-indicator">▶ Son tour</div>}
            {/* Dos de cartes symbolisant la main adverse */}
            <div className="joueur-dos-cartes">
              <CardImage dos largeur={28} />
            </div>
          </div>
        )
      })}

      {/* Centre : pli courant */}
      <div className="pli-centre">
        {etatJeu.statut === 'EN_JEU' && (
          <>
            <div className="pli-titre">Pli {etatJeu.numPliCourant}/8</div>
            <div className="pli-cartes-table">
              {(!etatJeu.pliCourant || etatJeu.pliCourant.length === 0)
                ? <span className="pli-vide">—</span>
                : etatJeu.pliCourant.map((cp, i) => (
                  <div key={i} className="pli-carte-item">
                    <MiniCarte carte={cp.carte} />
                    <div className="mini-carte-pseudo">{cp.pseudo}</div>
                  </div>
                ))
              }
            </div>
            {!etatJeu.pliCourant?.length && etatJeu.tourJoueurId === etatJeu.monJoueurId && (
              <p className="attente" style={{ marginTop: 6 }}>Jouez une carte !</p>
            )}
            {etatJeu.tourJoueurId !== etatJeu.monJoueurId && (
              <p className="attente">Tour de <strong>{etatJeu.tourPseudo}</strong></p>
            )}
          </>
        )}
      </div>

      {/* Dernier pli (coin bas-droit de la table) */}
      {etatJeu.dernierPli && etatJeu.dernierPli.length > 0 && etatJeu.statut === 'EN_JEU' && (
        <div className="dernier-pli-zone">
          <div className="dernier-pli-titre">
            Pli précédent — Éq.{etatJeu.dernierPliGagnantEquipe}
          </div>
          <div className="dernier-pli-cartes">
            {etatJeu.dernierPli.map((cp, i) => (
              <div key={i} className="dernier-pli-item">
                <MiniCarte carte={cp.carte} />
                <div className="mini-carte-pseudo">{cp.pseudo}</div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Moi (bas) */}
      <div className={`joueur-table joueur-bas joueur-moi ${etatJeu.tourJoueurId === etatJeu.monJoueurId ? 'joueur-actif' : ''}`}>
        <div className="joueur-nom">
          {utilisateur.pseudo} <span className="moi-label">(vous)</span>
        </div>
        <div className={`joueur-equipe eq${etatJeu.monEquipe}`}>Équipe {etatJeu.monEquipe}</div>
        {etatJeu.tourJoueurId === etatJeu.monJoueurId && etatJeu.statut === 'EN_JEU' && (
          <div className="tour-indicator">▶ Votre tour</div>
        )}
      </div>
    </div>
  )
}
